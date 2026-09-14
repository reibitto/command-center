package commandcenter.command.win

import com.sun.jna.{Native, Pointer}
import com.sun.jna.platform.win32.WinNT.LUID
import com.sun.jna.ptr.IntByReference
import commandcenter.command.win.CCD.*
import zio.*

import java.nio.{ByteBuffer, ByteOrder}

/** High-level wrapper around [[CCD]] for enumerating and switching between GPU
  * display outputs (e.g. HDMI1, DisplayPort2, DisplayPort3), independent of
  * which monitor happens to be plugged into each one.
  */
object DisplayOutputs {

  /** How [[activateOnly]] applies a switch. */
  sealed trait SwitchStrategy

  object SwitchStrategy {

    /** A single `SetDisplayConfig` call: activate the target, deactivate
      * everything else, all at once. Simple, but observed to sometimes report
      * success without ever producing a real picture when the target's GPU
      * pipeline was fully cold (nothing else active) beforehand.
      */
    case object Direct extends SwitchStrategy

    /** Three separate `SetDisplayConfig` calls, mirroring a workaround from
      * other display-switcher tools:
      *
      *   1. '''Extend''': activate the target ''alongside'' whatever's
      *      currently active, without deactivating anything - gives the
      *      target's pipeline a chance to link-train while at least one other
      *      pipeline is already warm, rather than going from fully cold
      *      straight to solely active.
      *   1. '''Set primary''': reposition the target's source to desktop
      *      coordinate (0,0) - the CCD-level equivalent of making it the
      *      Windows-primary display - and move whatever else is active out of
      *      the way so the arrangement stays non-overlapping.
      *   1. '''Narrow''': the real, persisted activation - deactivate
      *      everything except the target.
      *
      * Each step best-effort logs and moves on to the next even if it fails,
      * since the point is to give the driver every opportunity to warm up the
      * link before the step that actually matters (narrow) runs.
      */
    case object ExtendSetPrimaryNarrow extends SwitchStrategy
  }

  // Byte offsets within DISPLAYCONFIG_MODE_INFO.union when infoType is DISPLAYCONFIG_MODE_INFO_TYPE_SOURCE (a
  // DISPLAYCONFIG_SOURCE_MODE: UINT32 width, UINT32 height, DISPLAYCONFIG_PIXELFORMAT pixelFormat, POINTL
  // position). Only a path's *source* modeInfoIdx entry is valid to interpret this way.
  private def readSourceWidth(mode: DISPLAYCONFIG_MODE_INFO): Int =
    ByteBuffer.wrap(mode.union).order(ByteOrder.LITTLE_ENDIAN).getInt(0)

  private def setSourcePosition(mode: DISPLAYCONFIG_MODE_INFO, x: Int, y: Int): Unit = {
    val buf = ByteBuffer.wrap(mode.union).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(12, x)
    buf.putInt(16, y)
  }

  final case class DisplayPath(
      adapterId: LUID,
      sourceId: Int,
      targetId: Int,
      friendlyName: String,
      devicePath: String,
      active: Boolean,
      available: Boolean
  )

  final private case class TargetInfo(friendlyName: String, devicePath: String)

  final private case class QueryResult(
      paths: Array[DISPLAYCONFIG_PATH_INFO],
      modes: Array[DISPLAYCONFIG_MODE_INFO],
      sizesRc: Int,
      queryRc: Int,
      retried: Boolean
  )

  private def query(
      onlyActivePaths: Boolean
  ): Task[(Array[DISPLAYCONFIG_PATH_INFO], Array[DISPLAYCONFIG_MODE_INFO])] =
    for {
      result <- ZIO.attempt {
                  val flags = if (onlyActivePaths) QDC_ONLY_ACTIVE_PATHS else QDC_ALL_PATHS
                  val pathCount = new IntByReference()
                  val modeCount = new IntByReference()

                  def refreshSizes(): Int = INSTANCE.GetDisplayConfigBufferSizes(flags, pathCount, modeCount)

                  val sizesRc = refreshSizes()
                  if (sizesRc != ERROR_SUCCESS)
                    throw new RuntimeException(s"GetDisplayConfigBufferSizes failed with code $sizesRc")

                  var paths = DISPLAYCONFIG_PATH_INFO.array(pathCount.getValue)
                  var modes = DISPLAYCONFIG_MODE_INFO.array(modeCount.getValue)
                  var queryRc = INSTANCE.QueryDisplayConfig(flags, pathCount, paths, modeCount, modes, Pointer.NULL)
                  var retried = false

                  // The active topology can change between the size query and the data query (e.g. a display
                  // waking up); retry once with freshly-sized buffers if that happens.
                  if (queryRc == ERROR_INSUFFICIENT_BUFFER) {
                    retried = true
                    val retryRc = refreshSizes()
                    if (retryRc != ERROR_SUCCESS)
                      throw new RuntimeException(s"GetDisplayConfigBufferSizes (retry) failed with code $retryRc")
                    paths = DISPLAYCONFIG_PATH_INFO.array(pathCount.getValue)
                    modes = DISPLAYCONFIG_MODE_INFO.array(modeCount.getValue)
                    queryRc = INSTANCE.QueryDisplayConfig(flags, pathCount, paths, modeCount, modes, Pointer.NULL)
                  }

                  if (queryRc != ERROR_SUCCESS)
                    throw new RuntimeException(s"QueryDisplayConfig failed with code $queryRc")

                  QueryResult(paths.take(pathCount.getValue), modes.take(modeCount.getValue), sizesRc, queryRc, retried)
                }
      _ <- ZIO.logDebug(
             s"QueryDisplayConfig(onlyActivePaths=$onlyActivePaths): GetDisplayConfigBufferSizes=${result.sizesRc}, " +
               s"QueryDisplayConfig=${result.queryRc}${if (result.retried) " (buffer resized once)" else ""}, " +
               s"${result.paths.length} paths, ${result.modes.length} modes"
           )
    } yield (result.paths, result.modes)

  private def targetInfo(path: DISPLAYCONFIG_PATH_INFO): Task[TargetInfo] =
    ZIO.attempt {
      val request = new DISPLAYCONFIG_TARGET_DEVICE_NAME
      request.header.`type` = DISPLAYCONFIG_DEVICE_INFO_GET_TARGET_NAME
      request.header.infoSize = request.size()
      request.header.adapterId = path.targetInfo.adapterId
      request.header.id = path.targetInfo.id
      request.write()

      val rc = INSTANCE.DisplayConfigGetDeviceInfo(request)

      if (rc != ERROR_SUCCESS) TargetInfo("", "")
      else {
        request.read()
        TargetInfo(
          Native.toString(request.monitorFriendlyDeviceName),
          Native.toString(request.monitorDevicePath)
        )
      }
    }

  /** Lists every display path Windows currently knows about for this GPU.
    * Unlike passing `onlyActivePaths = true` (which mirrors
    * `QDC_ONLY_ACTIVE_PATHS` and only returns what's currently lit up), the
    * default surfaces paths for outputs that are simply powered off right now
    * too - Windows keeps a path for a GPU port once it has detected a display
    * on it at least once, independent of that display's current power state,
    * since the DDC/EDID line most monitors expose typically stays live in
    * standby.
    *
    * A display that has never been detected (or one that kills DDC entirely
    * when off) won't appear until it's next powered on and re-detected.
    */
  def listPaths(onlyActivePaths: Boolean = false): Task[List[DisplayPath]] =
    for {
      (paths, _) <- query(onlyActivePaths)
      result     <- ZIO.foreach(paths.toList) { p =>
                  targetInfo(p).map { info =>
                    DisplayPath(
                      p.targetInfo.adapterId,
                      p.sourceInfo.id,
                      p.targetInfo.id,
                      info.friendlyName,
                      info.devicePath,
                      (p.flags & DISPLAYCONFIG_PATH_ACTIVE) != 0,
                      p.targetInfo.targetAvailable != 0
                    )
                  }
                }
    } yield result

  /** Current resolution, refresh rate and display scale for `devicePath` - see
    * [[DisplayMode.currentMode]]. `None` if the display isn't currently active.
    */
  def currentModeInfo(devicePath: String): Task[Option[DisplayMode.CurrentMode]] =
    DisplayMode.currentMode(devicePath)

  // All of the below is process-lifetime state only (plain in-memory fields, not written to disk) - it resets
  // on restart, at which point every target starts fresh from the "available" heuristic again.

  // The targetId passed to the most recent `activateOnly` call, regardless of which display it was or whether
  // it succeeded - used to detect when a call is a back-to-back repeat for the *same* display.
  private val lastInvokedTarget: java.util.concurrent.atomic.AtomicInteger =
    new java.util.concurrent.atomic.AtomicInteger(Int.MinValue)

  // Per targetId, which source/pipeline last succeeded for it - used as the starting point for a "fresh" call
  // (the first switch to this display since switching to something else), so once a working pipeline is found,
  // ordinary switches back to that display go straight to it instead of the naive "first available" heuristic.
  private val preferredSourceByTarget: java.util.concurrent.ConcurrentHashMap[Int, Int] =
    new java.util.concurrent.ConcurrentHashMap[Int, Int]()

  // Per targetId, the source/pipeline to use next if the *next* call to this same target is a repeat (or
  // forces one via `next = true`) - always advanced past whichever pipeline the previous call ended on,
  // independent of whether that previous call succeeded.
  private val nextSourceByTarget: java.util.concurrent.ConcurrentHashMap[Int, Int] =
    new java.util.concurrent.ConcurrentHashMap[Int, Int]()

  // Sets exactly `keepIdx` active and deactivates every other path (invalidating their mode indices too, so a
  // stale mode from a previous activation can't confuse a later SetDisplayConfig call). This is both the whole
  // of SwitchStrategy.Direct and the final "narrow" step of SwitchStrategy.ExtendSetPrimaryNarrow.
  private def activateOnlyPath(paths: Array[DISPLAYCONFIG_PATH_INFO], keepIdx: Int): Unit =
    paths.zipWithIndex.foreach { case (path, idx) =>
      if (idx == keepIdx) path.flags |= DISPLAYCONFIG_PATH_ACTIVE
      else {
        path.flags &= ~DISPLAYCONFIG_PATH_ACTIVE
        path.sourceInfo.modeInfoIdx = DISPLAYCONFIG_PATH_MODE_IDX_INVALID
        path.targetInfo.modeInfoIdx = DISPLAYCONFIG_PATH_MODE_IDX_INVALID
      }
    }

  private def directSwitch(
      paths: Array[DISPLAYCONFIG_PATH_INFO],
      modes: Array[DISPLAYCONFIG_MODE_INFO],
      targetIdx: Int
  ): Task[Int] =
    ZIO.attempt {
      activateOnlyPath(paths, targetIdx)
      INSTANCE.SetDisplayConfig(
        paths.length,
        paths,
        modes.length,
        modes,
        SDC_APPLY | SDC_USE_SUPPLIED_DISPLAY_CONFIG | SDC_ALLOW_CHANGES | SDC_SAVE_TO_DATABASE
      )
    }

  private def extendSetPrimaryNarrowSwitch(
      chosenSource: Int,
      chosenTarget: Int,
      initialPaths: Array[DISPLAYCONFIG_PATH_INFO],
      initialModes: Array[DISPLAYCONFIG_MODE_INFO],
      initialTargetIdx: Int
  ): Task[Int] = {
    def findPathIdx(paths: Array[DISPLAYCONFIG_PATH_INFO]): Option[Int] =
      paths.zipWithIndex.find { case (p, _) =>
        p.targetInfo.id == chosenTarget && p.sourceInfo.id == chosenSource
      }.map(_._2)

    for {
      // Step 1 (extend): activate the target alongside whatever's already active, touching nothing else.
      extendRc <- ZIO.attempt {
                    initialPaths.zipWithIndex.foreach { case (path, idx) =>
                      val keep = idx == initialTargetIdx || (path.flags & DISPLAYCONFIG_PATH_ACTIVE) != 0
                      if (keep) path.flags |= DISPLAYCONFIG_PATH_ACTIVE
                      else {
                        path.flags &= ~DISPLAYCONFIG_PATH_ACTIVE
                        path.sourceInfo.modeInfoIdx = DISPLAYCONFIG_PATH_MODE_IDX_INVALID
                        path.targetInfo.modeInfoIdx = DISPLAYCONFIG_PATH_MODE_IDX_INVALID
                      }
                    }

                    INSTANCE.SetDisplayConfig(
                      initialPaths.length,
                      initialPaths,
                      initialModes.length,
                      initialModes,
                      SDC_APPLY | SDC_USE_SUPPLIED_DISPLAY_CONFIG | SDC_ALLOW_CHANGES
                    )
                  }
      _ <- ZIO.logDebug(s"Extend step (source=$chosenSource, target=$chosenTarget) returned $extendRc")
      _ <- ZIO.sleep(500.millis)

      // Step 2 (set primary): reposition the target's source to (0,0) and move whatever else is active out of
      // the way, so the target becomes the Windows-primary display. Best-effort - if the target's path or a
      // usable source mode can't be found (e.g. it disappeared, or extend above failed), skip straight to the
      // narrow step below rather than aborting the whole switch over a cosmetic step.
      (paths2, modes2) <- query(onlyActivePaths = false)
      primaryRc        <- ZIO.attempt {
                     findPathIdx(paths2) match {
                       case None       => ERROR_SUCCESS
                       case Some(idx2) =>
                         val targetModeIdx = paths2(idx2).sourceInfo.modeInfoIdx
                         if (targetModeIdx < 0 || targetModeIdx >= modes2.length) ERROR_SUCCESS
                         else {
                           val targetWidth = readSourceWidth(modes2(targetModeIdx))
                           setSourcePosition(modes2(targetModeIdx), 0, 0)

                           paths2.zipWithIndex.foreach { case (p, i) =>
                             if (i != idx2 && (p.flags & DISPLAYCONFIG_PATH_ACTIVE) != 0) {
                               val otherModeIdx = p.sourceInfo.modeInfoIdx
                               if (otherModeIdx >= 0 && otherModeIdx < modes2.length)
                                 setSourcePosition(modes2(otherModeIdx), targetWidth, 0)
                             }
                           }

                           INSTANCE.SetDisplayConfig(
                             paths2.length,
                             paths2,
                             modes2.length,
                             modes2,
                             SDC_APPLY | SDC_USE_SUPPLIED_DISPLAY_CONFIG | SDC_ALLOW_CHANGES
                           )
                         }
                     }
                   }
      _ <- ZIO.logDebug(s"Set-primary step (source=$chosenSource, target=$chosenTarget) returned $primaryRc")
      _ <- ZIO.sleep(300.millis)

      // Step 3 (narrow): the real, persisted activation - only the target active, everything else off. This
      // step's return code is what the caller actually treats as success/failure.
      (paths3, modes3) <- query(onlyActivePaths = false)
      targetIdx3       <-
        ZIO
          .fromOption(findPathIdx(paths3))
          .orElseFail(
            new RuntimeException(
              s"(source=$chosenSource, target=$chosenTarget) disappeared during the extend/set-primary steps"
            )
          )
      rc <- ZIO.attempt {
              activateOnlyPath(paths3, targetIdx3)
              INSTANCE.SetDisplayConfig(
                paths3.length,
                paths3,
                modes3.length,
                modes3,
                SDC_APPLY | SDC_USE_SUPPLIED_DISPLAY_CONFIG | SDC_ALLOW_CHANGES | SDC_SAVE_TO_DATABASE
              )
            }
    } yield rc
  }

  /** Activates only the display whose friendly name contains `nameMatch`
    * (case-insensitive), deactivating every other currently-known path in the
    * same call. The target doesn't need to be powered on right now - only to
    * have been detected by Windows at some point (see [[listPaths]]).
    *
    * Activating this target is observed to occasionally fail or silently not
    * show any picture, even though `SetDisplayConfig` itself can return success -
    * most likely a real GPU-pipeline/source that isn't actually capable of
    * driving that particular target's negotiated mode reliably. Windows gives
    * no way to confirm a picture actually appeared, so there's no way to detect
    * that automatically. Two ways to route around it:
    *
    *   - '''Calling this back-to-back for the same display''' (e.g. pressing
    *     the same shortcut again) is treated as "that didn't work, try the next
    *     pipeline" and rotates forward, regardless of whether the previous call
    *     returned success. Switching to a *different* display in between resets
    *     this, so the next call back to this one starts over from the
    *     remembered-good pipeline instead of continuing to rotate.
    *   - Passing `next = true` forces the same rotation immediately, even on
    *     the first call back to a display since switching away from it.
    *
    * Separately, an actual `SetDisplayConfig` failure (a nonzero return code)
    * always retries with the next candidate pipeline internally, for up to
    * `maxAttempts` attempts with a short delay between them, re-querying the
    * topology fresh each time in case it genuinely changed. Every attempt's
    * outcome (chosen source/target and the `SetDisplayConfig` return code) is
    * logged at INFO so failures are visible without needing to bump the log
    * level.
    *
    * If `resolution` and/or `refreshRateHz` are set, once activation succeeds
    * this also restores those via [[DisplayMode.setMode]] - best-effort, logged
    * but not fatal to the overall switch, since `SetDisplayConfig`'s own mode
    * negotiation can silently drop back to the display's EDID-preferred mode
    * (often 60Hz at a lower resolution) rather than whatever was previously
    * selected.
    */
  def activateOnly(
      nameMatch: String,
      next: Boolean = false,
      resolution: Option[(Int, Int)] = None,
      refreshRateHz: Option[Int] = None,
      strategy: SwitchStrategy = SwitchStrategy.Direct,
      maxAttempts: Int = 4,
      retryDelay: Duration = 400.millis
  ): Task[Unit] = {
    def attempt(attemptNum: Int, rotationOrder: Vector[Int]): Task[Unit] =
      for {
        (paths, modes) <- query(onlyActivePaths = false)
        targets        <- ZIO.foreach(paths.toList)(targetInfo)
        matches =
          targets.zipWithIndex.filter { case (info, _) =>
            info.friendlyName.toLowerCase.contains(nameMatch.toLowerCase)
          }
        sourceId = rotationOrder(attemptNum - 1)
        targetIdx <-
          ZIO
            .fromOption(matches.find { case (_, idx) => paths(idx).sourceInfo.id == sourceId }.map(_._2))
            .orElseFail(
              new RuntimeException(
                s"No display found matching `$nameMatch` with source=$sourceId. " +
                  s"Known displays: ${targets.map(_.friendlyName).filter(_.nonEmpty).mkString(", ")}"
              )
            )
        chosenSource = paths(targetIdx).sourceInfo.id
        chosenTarget = paths(targetIdx).targetInfo.id
        chosenDevicePath = targets(targetIdx).devicePath
        rc <- strategy match {
                case SwitchStrategy.Direct =>
                  directSwitch(paths, modes, targetIdx)
                case SwitchStrategy.ExtendSetPrimaryNarrow =>
                  extendSetPrimaryNarrowSwitch(chosenSource, chosenTarget, paths, modes, targetIdx)
              }
        _ <- ZIO.logInfo(
               s"SetDisplayConfig attempt $attemptNum/$maxAttempts for `$nameMatch` " +
                 s"(source=$chosenSource, target=$chosenTarget) returned $rc" +
                 (if (rc == ERROR_SUCCESS) "" else " - FAILED")
             )
        nextForTarget = rotationOrder(attemptNum % rotationOrder.length)
        _ <- ZIO.succeed(nextSourceByTarget.put(chosenTarget, nextForTarget))
        _ <-
          if (rc == ERROR_SUCCESS)
            ZIO.succeed(preferredSourceByTarget.put(chosenTarget, chosenSource)) *>
              ZIO
                .when(resolution.nonEmpty || refreshRateHz.nonEmpty) {
                  // Give Windows a moment to propagate the CCD change to the legacy GDI device list that
                  // DisplayMode reads from, before trying to read/set a mode on it.
                  ZIO.sleep(300.millis) *>
                    DisplayMode
                      .setMode(chosenDevicePath, resolution, refreshRateHz)
                      .tapErrorCause(c => ZIO.logWarningCause(s"Could not restore display mode for `$nameMatch`", c))
                      .ignore
                }
                .unit
          else if (attemptNum < maxAttempts) ZIO.sleep(retryDelay) *> attempt(attemptNum + 1, rotationOrder)
          else
            ZIO.fail(
              new RuntimeException(
                s"SetDisplayConfig failed with code $rc for `$nameMatch` after $maxAttempts attempts"
              )
            )
      } yield ()

    for {
      (paths, _) <- query(onlyActivePaths = false)
      targets    <- ZIO.foreach(paths.toList)(targetInfo)
      matches =
        targets.zipWithIndex.filter { case (info, _) => info.friendlyName.toLowerCase.contains(nameMatch.toLowerCase) }
      result <-
        ZIO
          .fromOption(matches.headOption.map { case (_, anyIdx) => paths(anyIdx).targetInfo.id })
          .orElseFail(
            new RuntimeException(
              s"No display found matching `$nameMatch`. " +
                s"Known displays: ${targets.map(_.friendlyName).filter(_.nonEmpty).mkString(", ")}"
            )
          )
          .flatMap { targetId =>
            val candidateSourceIds = matches.filter { case (_, idx) => paths(idx).targetInfo.id == targetId }.map {
              case (_, idx) => paths(idx).sourceInfo.id
            }.distinct.sorted.toVector

            val isRepeat = next || lastInvokedTarget.getAndSet(targetId) == targetId

            val startSourceId =
              if (isRepeat)
                Option(nextSourceByTarget.get(targetId))
                  .filter(candidateSourceIds.contains)
                  .getOrElse(candidateSourceIds.head)
              else
                Option(preferredSourceByTarget.get(targetId))
                  .filter(candidateSourceIds.contains)
                  .orElse(candidateSourceIds.find { sourceId =>
                    matches.exists { case (_, idx) =>
                      paths(idx).sourceInfo.id == sourceId && paths(idx).targetInfo.targetAvailable != 0
                    }
                  })
                  .getOrElse(candidateSourceIds.head)

            val startIdx = candidateSourceIds.indexOf(startSourceId)
            val rotationOrder = candidateSourceIds.drop(startIdx) ++ candidateSourceIds.take(startIdx)

            attempt(1, rotationOrder)
          }
    } yield result
  }
}
