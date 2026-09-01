package commandcenter.command.win

import com.sun.jna.{Native, Pointer}
import com.sun.jna.platform.win32.WinNT.LUID
import com.sun.jna.ptr.IntByReference
import commandcenter.command.win.CCD.*
import zio.*

/** High-level wrapper around [[CCD]] for enumerating and switching between GPU
  * display outputs (e.g. HDMI1, DisplayPort2, DisplayPort3), independent of
  * which monitor happens to be plugged into each one.
  */
object DisplayOutputs {

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
    * If `refreshRateHz` is set, once activation succeeds this also sets that
    * refresh rate via [[RefreshRate.setRefreshRate]] - best-effort, logged but
    * not fatal to the overall switch, since `SetDisplayConfig`'s own mode
    * negotiation can silently drop back to the display's EDID-preferred refresh
    * rate (often 60Hz) rather than whatever was previously selected.
    */
  def activateOnly(
      nameMatch: String,
      next: Boolean = false,
      refreshRateHz: Option[Int] = None,
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
        rc <- ZIO.attempt {
                paths.zipWithIndex.foreach { case (path, idx) =>
                  if (idx == targetIdx) path.flags |= DISPLAYCONFIG_PATH_ACTIVE
                  else {
                    path.flags &= ~DISPLAYCONFIG_PATH_ACTIVE
                    // Also drop any mode this path previously resolved to - otherwise a path that was
                    // activated earlier (even briefly, against a target with no real signal) keeps a
                    // stale mode index that can make a later SetDisplayConfig call misbehave.
                    path.sourceInfo.modeInfoIdx = DISPLAYCONFIG_PATH_MODE_IDX_INVALID
                    path.targetInfo.modeInfoIdx = DISPLAYCONFIG_PATH_MODE_IDX_INVALID
                  }
                }

                INSTANCE.SetDisplayConfig(
                  paths.length,
                  paths,
                  modes.length,
                  modes,
                  SDC_APPLY | SDC_USE_SUPPLIED_DISPLAY_CONFIG | SDC_ALLOW_CHANGES | SDC_SAVE_TO_DATABASE
                )
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
              ZIO.foreachDiscard(refreshRateHz) { hz =>
                // Give Windows a moment to propagate the CCD change to the legacy GDI device list that
                // RefreshRate reads from, before trying to read/set a mode on it.
                ZIO.sleep(300.millis) *>
                  RefreshRate
                    .setRefreshRate(chosenDevicePath, hz)
                    .tapErrorCause(c =>
                      ZIO.logWarningCause(s"Could not set refresh rate to ${hz}Hz for `$nameMatch`", c)
                    )
                    .ignore
              }
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
