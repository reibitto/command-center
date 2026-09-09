package commandcenter.util

import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.test.*

import java.time.ZoneId

object TimeZonesSpec extends CommandBaseSpec {

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("TimeZonesSpec")(
      suite("full IANA zone ids")(
        test("resolves an exact-case id") {
          assertTrue(TimeZones.get("America/New_York").contains(ZoneId.of("America/New_York")))
        },
        test("is case-insensitive") {
          assertTrue(
            TimeZones.get("america/new_york").contains(ZoneId.of("America/New_York")),
            TimeZones.get("AMERICA/NEW_YORK").contains(ZoneId.of("America/New_York")),
            TimeZones.get("Asia/Tokyo").contains(ZoneId.of("Asia/Tokyo")),
            TimeZones.get("asia/tokyo").contains(ZoneId.of("Asia/Tokyo"))
          )
        },
        test("trims surrounding whitespace") {
          assertTrue(
            TimeZones.get("  America/New_York  ").contains(ZoneId.of("America/New_York")),
            TimeZones.get("\tUTC\n").contains(ZoneId.of("UTC"))
          )
        }
      ),
      suite("unrecognized input")(
        test("an unknown zone name returns None") {
          assertTrue(TimeZones.get("Not/AZone").isEmpty, TimeZones.get("gibberish").isEmpty)
        },
        test("empty or blank input returns None") {
          assertTrue(TimeZones.get("").isEmpty, TimeZones.get("   ").isEmpty)
        }
      ),
      suite("UTC/GMT offsets")(
        test("bare utc resolves, and gmt is aliased to the same zero-offset zone") {
          assertTrue(TimeZones.get("utc").contains(ZoneId.of("UTC")), TimeZones.get("gmt") == TimeZones.get("utc"))
        },
        test("utc/gmt with a numeric offset resolve, case-insensitively") {
          assertTrue(
            TimeZones.get("UTC+9").contains(ZoneId.of("UTC+9")),
            TimeZones.get("utc+9").contains(ZoneId.of("UTC+9")),
            TimeZones.get("UTC-05:00").contains(ZoneId.of("UTC-05:00")),
            TimeZones.get("gmt+3").contains(ZoneId.of("GMT+3")),
            TimeZones.get("GMT-11").contains(ZoneId.of("GMT-11"))
          )
        },
        test("a raw numeric offset (no utc/gmt prefix) also resolves") {
          assertTrue(
            TimeZones.get("+09:00").contains(ZoneId.of("+09:00")),
            TimeZones.get("-05:00").contains(ZoneId.of("-05:00"))
          )
        }
      ),
      suite("common abbreviations")(
        test("unambiguous abbreviations resolve to their zone") {
          assertTrue(
            TimeZones.get("JST").contains(ZoneId.of("Asia/Tokyo")),
            TimeZones.get("PST").contains(ZoneId.of("America/Los_Angeles")),
            TimeZones.get("IST").contains(ZoneId.of("Asia/Kolkata")),
            TimeZones.get("CST").contains(ZoneId.of("Asia/Shanghai")),
            TimeZones.get("AET").contains(ZoneId.of("Australia/Sydney"))
          )
        },
        test("abbreviations are case-insensitive") {
          assertTrue(
            TimeZones.get("jst").contains(ZoneId.of("Asia/Tokyo")),
            TimeZones.get("Jst").contains(ZoneId.of("Asia/Tokyo"))
          )
        }
      ),
      // Some abbreviations genuinely name more than one real-world zone (e.g. "AST" is both Atlantic Standard
      // Time and Arabia Standard Time). `TimeZones.get` resolves each to one deliberately-chosen, documented
      // meaning rather than an arbitrary one -- these pin that choice so it can't silently drift, and confirm the
      // other meaning is still reachable by typing its raw UTC offset directly.
      suite("ambiguous abbreviations resolve to one documented meaning, with the other reachable via its offset")(
        test("AMT resolves to Amazon Time; Armenia Time is reachable via its offset") {
          assertTrue(
            TimeZones.get("AMT").contains(ZoneId.of("UTC-04")),
            TimeZones.get("UTC+04").contains(ZoneId.of("UTC+04"))
          )
        },
        test("AST resolves to Atlantic Standard Time; Arabia Standard Time is reachable via its offset") {
          assertTrue(
            TimeZones.get("AST").contains(ZoneId.of("UTC-04")),
            TimeZones.get("UTC+03").contains(ZoneId.of("UTC+03"))
          )
        },
        test("BST resolves to British Summer Time; the other meanings are reachable via their offsets") {
          assertTrue(
            TimeZones.get("BST").contains(ZoneId.of("UTC+01")),
            TimeZones.get("UTC+06").contains(ZoneId.of("UTC+06")),
            TimeZones.get("UTC+11").contains(ZoneId.of("UTC+11"))
          )
        },
        test("CDT resolves to (North American) Central Daylight Time, not Cuba Daylight Time") {
          // Regression: a plain `Map` literal with a duplicate "CDT" key previously let construction order pick
          // Cuba Daylight Time silently; Central Daylight Time is the far more commonly meant one.
          assertTrue(
            TimeZones.get("CDT").contains(ZoneId.of("UTC-05")),
            TimeZones.get("UTC-04").contains(ZoneId.of("UTC-04"))
          )
        },
        test("GST resolves to Gulf Standard Time; South Georgia Time is reachable via its offset") {
          assertTrue(
            TimeZones.get("GST").contains(ZoneId.of("UTC+04")),
            TimeZones.get("UTC-02").contains(ZoneId.of("UTC-02"))
          )
        },
        test("LHST resolves to the standard (non-daylight) offset") {
          // Regression: previously resolved to the daylight-saving variant, which doesn't match what an
          // abbreviation ending in "ST" (not "DT") should mean.
          assertTrue(
            TimeZones.get("LHST").contains(ZoneId.of("UTC+10:30")),
            TimeZones.get("UTC+11").contains(ZoneId.of("UTC+11"))
          )
        },
        test("MST resolves to (North American) Mountain Standard Time; the other meaning is reachable via its offset") {
          assertTrue(
            TimeZones.get("MST").contains(ZoneId.of("UTC-07")),
            TimeZones.get("UTC+08").contains(ZoneId.of("UTC+08"))
          )
        },
        test("SST resolves to Samoa Standard Time, not Singapore Standard Time (use the unambiguous SGT for that)") {
          // Regression: previously resolved to the same UTC+08 offset already covered unambiguously by "SGT",
          // making the dedicated "SST" entry redundant instead of covering its more distinct meaning.
          assertTrue(
            TimeZones.get("SST").contains(ZoneId.of("UTC-11")),
            TimeZones.get("SGT").contains(ZoneId.of("UTC+08"))
          )
        }
      ),
      test("aliases has no duplicate keys (would otherwise silently shadow an earlier entry)") {
        // Forces `TimeZones.aliases` (and its `require` guard) to evaluate; a duplicate key throws there before
        // this assertion even runs.
        assertTrue(TimeZones.get("utc").isDefined)
      }
    )
}
