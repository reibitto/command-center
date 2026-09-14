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
  private val tokyo = TimeZone(ZoneId.of("Asia/Tokyo"), Some("Tokyo"), alternateZoneIds = List("japan"))
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

  private def offsetSuffix(now: ZonedDateTime, target: ZonedDateTime): String =
    WorldTimesCommand.relativeOffset(now, target).plainText

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
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 at Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("'to' is also accepted as a separator") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 to Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("'@' is also accepted as a separator") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 @ Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("'>' is also accepted as a separator") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 > Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("the target zone can be given as a common abbreviation") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 at jst")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        }
      ) @@ useTestClock,
      suite("a bare trailing zone with no connector names the source, broadcasting to every configured zone")(
        test("an explicit date/time followed directly by a zone abbreviation") {
          val zone = TimeZones.get("est").get
          val now = LocalDateTime.parse("2024-06-15T05:30:00").atZone(zone)
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(zone)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("2024-06-15 08:00:00 est")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        },
        test("the zone abbreviation is case-insensitive") {
          val zone = TimeZones.get("est").get
          val now = LocalDateTime.parse("2024-06-15T05:30:00").atZone(zone)
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(zone)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("2024-06-15 08:00:00 EST")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        },
        test("the zone can be a full IANA id instead of an abbreviation") {
          val now = LocalDateTime.parse("2024-06-15T05:30:00").atZone(tokyo.zoneId)
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(tokyo.zoneId)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("2024-06-15 08:00:00 Asia/Tokyo")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        },
        test("a relative time followed by a zone abbreviation, as typically typed (e.g. '8am est')") {
          val zone = TimeZones.get("est").get
          val now = LocalDate.now(systemZone).atTime(5, 30).atZone(zone)
          val from = LocalDate.now(systemZone).atTime(8, 0).atZone(zone)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("8am est")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        },
        test("a multi-word configured display name resolves as the source too, e.g. 'new york'") {
          val zone = newYork.zoneId
          val now = LocalDateTime.parse("2024-06-15T05:30:00").atZone(zone)
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(zone)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("2024-06-15 08:00:00 new york")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        }
      ) @@ useTestClock,
      suite("a connector word names a target zone, converting from local time")(
        test("'in' is accepted as a separator, same as 'at'/'to'/'@'/'>'") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 in Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        }
      ) @@ useTestClock,
      // "<time> <source zone> (at|to|in|@|>) <target zone>" -- both ends of the conversion are named explicitly,
      // e.g. "8pm est to jst" means 8pm EST (not local), converted to JST.
      suite("both a source and target zone can be named explicitly")(
        test("converts from the named source zone, not local time") {
          val zone = TimeZones.get("est").get
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T20:00:00").atZone(zone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 20:00:00 est to Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("'in' works as the target connector here too") {
          val zone = TimeZones.get("est").get
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T20:00:00").atZone(zone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 20:00:00 est in Asia/Tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("the source zone can be a common abbreviation and the target a full IANA id, or vice versa") {
          val zone = TimeZones.get("jst").get
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T09:00:00").atZone(zone)
          val to = from.withZoneSameInstant(newYork.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 09:00:00 jst to America/New_York")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        },
        test("a multi-word source zone before the connector is found too, e.g. 'new york to jst'") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T09:00:00").atZone(newYork.zoneId)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 09:00:00 new york to jst")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        }
      ) @@ useTestClock,
      suite("a configured zone's display name or alternate ids resolve like any other zone name")(
        test("a bare display name resolves to that zone's current time") {
          val now = Instant.parse("2024-06-15T12:00:00Z")

          for {
            _    <- TestClock.setTime(now)
            text <- textOf("tokyo")
          } yield assertTrue(text == s"Tokyo ${render(now.atZone(tokyo.zoneId))}")
        } @@ useTestClock,
        test("a display name works as a connector target") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val to = from.withZoneSameInstant(tokyo.zoneId)

          for {
            _ <- TestClock.setTime(now.toInstant)
            t <- textOf("2024-06-15 14:30:00 to tokyo")
          } yield assertTrue(t == s"${render(from)} is ${render(to)}${offsetSuffix(now, from)}")
        } @@ useTestClock,
        test("a configured alternateZoneIds entry resolves the same as the display name") {
          val now = LocalDateTime.parse("2024-06-15T05:30:00").atZone(tokyo.zoneId)
          val from = LocalDateTime.parse("2024-06-15T08:00:00").atZone(tokyo.zoneId)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("2024-06-15 08:00:00 japan")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        } @@ useTestClock
      ),
      suite("a date/time phrase with no target zone converts to every configured zone")(
        test("an explicit absolute date/time") {
          val now = LocalDateTime.parse("2024-06-15T12:00:00").atZone(systemZone)
          val from = LocalDateTime.parse("2024-06-15T14:30:00").atZone(systemZone)
          val suffix = offsetSuffix(now, from)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("2024-06-15 14:30:00")
          } yield assertTrue(
            texts == List(
              s"New York ${render(from.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(from.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(from.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        },
        test("a relative date/time phrase that happens to contain the word 'at' is parsed as one unit") {
          val now = LocalDate.now(systemZone).atTime(14, 30).atZone(systemZone)
          val expected = LocalDate.now(systemZone).plusDays(1).atTime(17, 0).atZone(systemZone)
          val suffix = offsetSuffix(now, expected)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("tomorrow at 5pm")
          } yield assertTrue(
            texts == List(
              s"New York ${render(expected.withZoneSameInstant(newYork.zoneId))}$suffix",
              s"Tokyo ${render(expected.withZoneSameInstant(tokyo.zoneId))}$suffix",
              s"UTC ${render(expected.withZoneSameInstant(utc.zoneId))}$suffix"
            )
          )
        },
        test("a trailing separator with nothing after it still parses the date/time part") {
          val now = LocalDate.now(systemZone).atTime(14, 30).atZone(systemZone)
          val expected = LocalDate.now(systemZone).atTime(17, 0).atZone(systemZone)

          for {
            _     <- TestClock.setTime(now.toInstant)
            texts <- textsOf("5pm at")
          } yield assertTrue(
            texts.head == s"New York ${render(expected.withZoneSameInstant(newYork.zoneId))}${offsetSuffix(now, expected)}"
          )
        },
        test("a relative phrase using the word 'in' isn't mistaken for a connector") {
          preview("in 3 hours").exit.map(exit => assertTrue(exit.isSuccess))
        }
      ) @@ useTestClock,
      suite("unrecognized input surfaces an error instead of silently falling back")(
        test("completely unparseable input reports an error, instead of silently showing the current time") {
          errorMessageOf("asdkjhasdkjh").map(msg => assertTrue(msg.contains("asdkjhasdkjh")))
        },
        test("a date that parses but an unrecognized target zone reports an error naming the bad zone") {
          errorMessageOf("5pm at faketown").map(msg => assertTrue(msg.contains("faketown")))
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
