package commandcenter.command

import commandcenter.util.TimeZones
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.*
import zio.test.*

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

object WorldTimesCommandSpec extends CommandBaseSpec {

  // Fixed explicitly (rather than derived from locale/host settings) so the suite is deterministic regardless of
  // where it runs. `zzz` renders a zone abbreviation/offset, which is enough to tell zones apart in assertions
  // without depending on locale-specific month/day names.
  private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss zzz")

  private val newYork = TimeZone(ZoneId.of("America/New_York"), Some("New York"))
  private val tokyo = TimeZone(ZoneId.of("Asia/Tokyo"), Some("Tokyo"))
  private val utc = TimeZone(ZoneId.of("UTC"), Some("UTC"))

  private val command: WorldTimesCommand =
    WorldTimesCommand(List("time", "times"), fmt, fmt, fmt, List(newYork, tokyo, utc))

  // The zone java.time falls back to for anything the command doesn't attach an explicit zone to (e.g. resolving
  // "5pm" or "tomorrow"). Tests compute their expectations off this instead of assuming a particular host zone, so
  // the suite passes regardless of where it's run.
  private val systemZone: ZoneId = ZoneId.systemDefault()

  private def preview(input: String): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    val fullInput = if (input.isEmpty) "time" else s"time $input"
    command.preview(SearchInput(fullInput, List(fullInput), command.commandNames, defaultCommandContext))
  }

  private def plainText(r: PreviewResult[Unit]): String =
    r.renderFn() match {
      case Rendered.Ansi(s) => s.plainText
      case other            => throw new AssertionError(s"Expected an Ansi-rendered preview, got: $other")
    }

  private def singleText(results: PreviewResults[Unit]): String =
    results match {
      case PreviewResults.Single(r) => plainText(r)
      case other                    => throw new AssertionError(s"Expected a single preview result, got: $other")
    }

  private def multipleTexts(results: PreviewResults[Unit]): List[String] =
    results match {
      case PreviewResults.Multiple(rs) => rs.toList.map(plainText)
      case other                       => throw new AssertionError(s"Expected multiple preview results, got: $other")
    }

  private def textOf(input: String): ZIO[Env, CommandError, String] = preview(input).map(singleText)

  private def textsOf(input: String): ZIO[Env, CommandError, List[String]] = preview(input).map(multipleTexts)

  private def errorMessageOf(input: String): URIO[Env, String] =
    preview(input).foldZIO(
      {
        case CommandError.ShowMessage(Rendered.Ansi(s), _) => ZIO.succeed(s.plainText)
        case other                                         => ZIO.die(new AssertionError(s"Expected a ShowMessage error, got: $other"))
      },
      result => ZIO.die(new AssertionError(s"Expected a failure, but got a successful result: $result"))
    )

  private def render(time: ZonedDateTime): String = fmt.format(time)

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("WorldTimesCommandSpec")(
      suite("empty input")(
        test("shows the current time for every configured zone, in configured order") {
          val now = Instant.parse("2024-06-15T12:00:00Z")

          for {
            _     <- TestClock.setTime(now)
            texts <- textsOf("")
          } yield assertTrue(
            texts == List(
              s"New York ${render(now.atZone(newYork.zoneId))}",
              s"Tokyo ${render(now.atZone(tokyo.zoneId))}",
              s"UTC ${render(now.atZone(utc.zoneId))}"
            )
          )
        } @@ useTestClock
      ),
      suite("a bare zone resolves to that zone's current time")(
        test("a configured zone uses its custom display name") {
          val now = Instant.parse("2024-06-15T12:00:00Z")

          for {
            _    <- TestClock.setTime(now)
            text <- textOf("Asia/Tokyo")
          } yield assertTrue(text == s"Tokyo ${render(now.atZone(tokyo.zoneId))}")
        } @@ useTestClock,
        test("a zone not in the configured list falls back to its zone id as the display name") {
          val now = Instant.parse("2024-06-15T12:00:00Z")
          val sydney = ZoneId.of("Australia/Sydney")

          for {
            _    <- TestClock.setTime(now)
            text <- textOf("Australia/Sydney")
          } yield assertTrue(text == s"Australia/Sydney ${render(now.atZone(sydney))}")
        } @@ useTestClock,
        test("resolution is case-insensitive") {
          val now = Instant.parse("2024-06-15T12:00:00Z")

          for {
            _    <- TestClock.setTime(now)
            text <- textOf("asia/tokyo")
          } yield assertTrue(text == s"Tokyo ${render(now.atZone(tokyo.zoneId))}")
        } @@ useTestClock,
        test("a common abbreviation resolves to its zone") {
          val now = Instant.parse("2024-06-15T12:00:00Z")

          for {
            _    <- TestClock.setTime(now)
            text <- textOf("jst")
          } yield assertTrue(text == s"Tokyo ${render(now.atZone(tokyo.zoneId))}")
        } @@ useTestClock,
        test("a utc offset shorthand resolves") {
          val now = Instant.parse("2024-06-15T12:00:00Z")
          val zone = ZoneId.of("UTC+9")

          for {
            _    <- TestClock.setTime(now)
            text <- textOf("UTC+9")
          } yield assertTrue(text == s"${zone.getId} ${render(now.atZone(zone))}")
        } @@ useTestClock
      ),
      suite("an absolute date/time converts to a specific zone")(
        test("'at' converts an explicit date/time to the named zone") {
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          textOf("2024-06-15 14:30:00 at Asia/Tokyo").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("'to' is also accepted as a separator") {
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          textOf("2024-06-15 14:30:00 to Asia/Tokyo").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("'@' is also accepted as a separator") {
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          textOf("2024-06-15 14:30:00 @ Asia/Tokyo").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("'>' is also accepted as a separator") {
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          textOf("2024-06-15 14:30:00 > Asia/Tokyo").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("the target zone can be given as a common abbreviation") {
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          textOf("2024-06-15 14:30:00 at jst").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        }
      ),
      // No connector word at all -- just "<date/time> <zone>", e.g. "8am est". No zone we recognize contains
      // whitespace, so the last whitespace-delimited token is checked as a zone whenever there's no "at"/"to"/"@"/
      // ">" to split on.
      suite("a date/time immediately followed by a zone, with no connector word")(
        test("an explicit date/time followed directly by a zone abbreviation") {
          val zone = TimeZones.get("est").get
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(systemZone)
          val to = from.withZoneSameInstant(zone)

          textOf("2024-06-15 08:00:00 est").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("the zone abbreviation is case-insensitive") {
          val zone = TimeZones.get("est").get
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(systemZone)
          val to = from.withZoneSameInstant(zone)

          textOf("2024-06-15 08:00:00 EST").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("the zone can be a full IANA id instead of an abbreviation") {
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          textOf("2024-06-15 08:00:00 Asia/Tokyo").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        },
        test("a relative time followed by a zone abbreviation, as typically typed (e.g. '8am est')") {
          val zone = TimeZones.get("est").get
          val from = LocalDate.now(systemZone).atTime(8, 0).atZone(systemZone)
          val to = from.withZoneSameInstant(zone)

          textOf("8am est").map(t => assertTrue(t == s"${render(from)} is ${render(to)}"))
        }
      ),
      suite("a date/time phrase with no target zone converts to every configured zone")(
        test("an explicit absolute date/time") {
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)

          textsOf("2024-06-15 14:30:00").map(texts =>
            assertTrue(
              texts == List(
                s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}",
                s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}",
                s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}"
              )
            )
          )
        },
        // Regression test: this phrase splits on the word "at" into "tomorrow" / "5pm". The 5pm half isn't a
        // zone, so it must fall back to parsing the whole phrase together -- previously it parsed only the
        // "tomorrow" half and silently dropped the "5pm" (see git history for WorldTimesCommand.scala).
        test("a relative date/time phrase that happens to contain the word 'at' is parsed as one unit") {
          val expected = LocalDate.now(systemZone).plusDays(1).atTime(17, 0).atZone(systemZone)

          textsOf("tomorrow at 5pm").map(texts =>
            assertTrue(
              texts == List(
                s"New York ${render(expected.withZoneSameInstant(newYork.zoneId))}",
                s"Tokyo ${render(expected.withZoneSameInstant(tokyo.zoneId))}",
                s"UTC ${render(expected.withZoneSameInstant(utc.zoneId))}"
              )
            )
          )
        },
        test("a trailing separator with nothing after it still parses the date/time part") {
          val expected = LocalDate.now(systemZone).atTime(17, 0).atZone(systemZone)

          textsOf("5pm at").map(texts =>
            assertTrue(texts.head == s"New York ${render(expected.withZoneSameInstant(newYork.zoneId))}")
          )
        }
      ),
      suite("unrecognized input surfaces an error instead of silently falling back")(
        // Regression test: previously any input that failed to parse (as either a date/time or a zone) fell back
        // to `configuredZones`, which looks identical to the empty-input/current-time response. That makes a typo
        // indistinguishable from a deliberate query for "now".
        test("completely unparseable input reports an error, instead of silently showing the current time") {
          errorMessageOf("asdkjhasdkjh").map(msg => assertTrue(msg.contains("asdkjhasdkjh")))
        },
        // Regression test: previously the "at"/"to" split would find a date on the left and, since the right-hand
        // side didn't resolve as a zone, would show all configured zones using *only* the left-hand date -- as if
        // no zone had been requested at all, even though one clearly was.
        test("a date that parses but an unrecognized target zone reports an error naming the bad zone") {
          errorMessageOf("5pm at tokyo").map(msg => assertTrue(msg.contains("tokyo")))
        },
        test("same as above, using the 'to' separator") {
          errorMessageOf("5pm to nonexistentzone").map(msg => assertTrue(msg.contains("nonexistentzone")))
        },
        test("a recognized zone but an unparseable date/time reports an error naming the bad date/time") {
          errorMessageOf("blah at jst").map(msg => assertTrue(msg.contains("blah")))
        },
        test("a bare unrecognized token doesn't silently show the configured zones either") {
          errorMessageOf("notarealzoneordate").map(msg => assertTrue(msg.contains("notarealzoneordate")))
        },
        // Guards against a false positive from the "no connector word" zone check (e.g. "8am est"): plain
        // multi-word garbage with no date and no zone anywhere in it must still report an error, not crash or
        // silently fall back, even though it also has a "last word" that gets checked as a zone candidate.
        test("multi-word garbage with no date/time and no zone still reports an error") {
          preview("meeting notes").exit.map(exit => assertTrue(exit.isFailure))
        }
      ),
      suite("integration with Command.search")(
        test("matches the command prefix and returns the configured zones") {
          val now = Instant.parse("2024-06-15T12:00:00Z")

          for {
            _       <- TestClock.setTime(now)
            results <- Command.search(Vector(command), Map.empty, "time", defaultCommandContext)
          } yield assertTrue(results.previews.size == 3)
        } @@ useTestClock,
        test("does not match unrelated input") {
          for {
            results <- Command.search(Vector(command), Map.empty, "not a time command", defaultCommandContext)
          } yield assertTrue(results.previews.isEmpty)
        },
        test("an error is still surfaced as a visible preview, not swallowed") {
          for {
            results <- Command.search(Vector(command), Map.empty, "time asdkjhasdkjh", defaultCommandContext)
          } yield assertTrue(
            results.previews.size == 1,
            plainText(results.previews.head).contains("asdkjhasdkjh")
          )
        }
      )
    )
}
