package commandcenter.util

import java.time.ZoneId
import java.util.Locale
import scala.jdk.CollectionConverters.*
import scala.util.Try

object TimeZones {

  def get(zone: String): Option[ZoneId] = {
    val zoneNormalized = zone.trim.toLowerCase(Locale.ENGLISH)

    all.get(zoneNormalized).orElse {
      // java.time needs these uppercase in order for it to work
      val z =
        if (zoneNormalized.startsWith("utc"))
          s"UTC${zoneNormalized.substring(3)}"
        else if (zoneNormalized.startsWith("gmt"))
          s"GMT${zoneNormalized.substring(3)}"
        else
          zoneNormalized

      Try(ZoneId.of(z)).toOption
    }
  }

  private lazy val all: Map[String, ZoneId] =
    ZoneId.getAvailableZoneIds.asScala.map { z =>
      z.toLowerCase(Locale.ENGLISH) -> ZoneId.of(z)
    }.toMap ++ aliases

  private lazy val aliases: Map[String, ZoneId] = {
    // Abbreviations are inherently ambiguous in the real world -- e.g. "CDT" can mean either Central Daylight
    // Time (North America) or Cuba Daylight Time. Where a key below has more than one common real-world meaning,
    // we resolve it to whichever is most likely meant in English-language usage, and the entry's comment names
    // the alternative(s). To target an alternative explicitly, type its raw UTC offset instead of the
    // abbreviation (e.g. "UTC-04") -- `TimeZones.get` already understands that syntax unambiguously, no special
    // syntax needed.
    //
    // Every key here must be unique: a duplicate key would silently shadow an earlier entry if this were a `Map`
    // literal, so this is a plain list instead, and `require` below fails loudly if one sneaks in. If you're
    // adding an abbreviation that collides with an existing one, resolve it the same way: pick the more common
    // meaning and note the alternative in a comment, rather than adding a second entry for the same key.
    val entries = List(
      "ACDT" -> "UTC+10:30",
      "ACST" -> "UTC+09:30",
      "ACT" -> "Australia/Darwin", // Australian Central Time. Also used for Acre Time (Brazil, UTC-05).
      "ACWST" -> "UTC+08:45",
      "ADT" -> "UTC-03",
      "AEDT" -> "UTC+11",
      "AEST" -> "UTC+10",
      "AET" -> "Australia/Sydney",
      "AFT" -> "UTC+04:30",
      "AGT" -> "America/Argentina/Buenos_Aires",
      "AKDT" -> "UTC-08",
      "AKST" -> "UTC-09",
      "ALMT" -> "UTC+06",
      "AMST" -> "UTC-03",
      "AMT" -> "UTC-04", // Amazon Time (Brazil). Alternative: Armenia Time is UTC+04.
      "ANAT" -> "UTC+12",
      "AQTT" -> "UTC+05",
      "ART" -> "Africa/Cairo",
      "AST" -> "UTC-04", // Atlantic Standard Time. Alternative: Arabia Standard Time is UTC+03.
      "AWST" -> "UTC+08",
      "AZOST" -> "UTC+0",
      "AZOT" -> "UTC-01",
      "AZT" -> "UTC+04",
      "BET" -> "America/Sao_Paulo",
      "BNT" -> "UTC+08",
      "BIOT" -> "UTC+06",
      "BIT" -> "UTC-12",
      "BOT" -> "UTC-04",
      "BRST" -> "UTC-02",
      "BRT" -> "UTC-03",
      // British Summer Time. Alternatives: Bangladesh Standard Time is UTC+06, Bougainville Standard Time is
      // UTC+11.
      "BST" -> "UTC+01",
      "BTT" -> "UTC+06",
      "CAT" -> "Africa/Harare",
      "CCT" -> "UTC+06:30",
      "CDT" -> "UTC-05", // Central Daylight Time (North America). Alternative: Cuba Daylight Time is UTC-04.
      "CEST" -> "UTC+02",
      "CET" -> "UTC+01",
      "CHADT" -> "UTC+13:45",
      "CHAST" -> "UTC+12:45",
      "CHOT" -> "UTC+08",
      "CHOST" -> "UTC+09",
      "CHST" -> "UTC+10",
      "CHUT" -> "UTC+10",
      "CIST" -> "UTC-08",
      "CKT" -> "UTC-10",
      "CLST" -> "UTC-03",
      "CLT" -> "UTC-04",
      "COST" -> "UTC-04",
      "COT" -> "UTC-05",
      // China Standard Time. Alternatives: Central Standard Time (North America) is UTC-06, Cuba Standard Time is
      // UTC-05.
      "CST" -> "Asia/Shanghai",
      "CT" -> "UTC-06",
      "CVT" -> "UTC-01",
      "CWST" -> "UTC+08:45",
      "CXT" -> "UTC+07",
      "DAVT" -> "UTC+07",
      "DDUT" -> "UTC+10",
      "DFT" -> "UTC+01",
      "EASST" -> "UTC-05",
      "EAST" -> "UTC-06",
      "EAT" -> "UTC+03",
      // Central European Time (as "ECT" = European Central Time). Alternatives: Eastern Caribbean Time is UTC-04,
      // Ecuador Time is UTC-05.
      "ECT" -> "Europe/Paris",
      "EDT" -> "UTC-04",
      "EEST" -> "UTC+03",
      "EET" -> "UTC+02",
      "EGST" -> "UTC+0",
      "EGT" -> "UTC-01",
      "EST" -> "UTC-05",
      "FET" -> "UTC+03",
      "FJT" -> "UTC+12",
      "FKST" -> "UTC-03",
      "FKT" -> "UTC-04",
      "FNT" -> "UTC-02",
      "GALT" -> "UTC-06",
      "GAMT" -> "UTC-09",
      "GET" -> "UTC+04",
      "GFT" -> "UTC-03",
      "GILT" -> "UTC+12",
      "GIT" -> "UTC-09",
      "GMT" -> "UTC+0",
      "GST" -> "UTC+04", // Gulf Standard Time. Alternative: South Georgia Time is UTC-02.
      "GYT" -> "UTC-04",
      "HDT" -> "UTC-09",
      "HAEC" -> "UTC+02",
      "HST" -> "UTC-10",
      "HKT" -> "UTC+08",
      "HMT" -> "UTC+05",
      "HOVST" -> "UTC+08",
      "HOVT" -> "UTC+07",
      "ICT" -> "UTC+07",
      "IDLW" -> "UTC-12",
      "IDT" -> "UTC+03",
      "IOT" -> "UTC+03",
      "IRDT" -> "UTC+04:30",
      "IRKT" -> "UTC+08",
      "IRST" -> "UTC+03:30",
      // India Standard Time. Alternatives: Irish Standard Time is UTC+01, Israel Standard Time is UTC+02.
      "IST" -> "Asia/Kolkata",
      "JST" -> "Asia/Tokyo",
      "KALT" -> "UTC+02",
      "KGT" -> "UTC+06",
      "KOST" -> "UTC+11",
      "KRAT" -> "UTC+07",
      "KST" -> "UTC+09",
      "LHST" -> "UTC+10:30", // Lord Howe Standard Time. Alternative (its own daylight saving variant): UTC+11.
      "LINT" -> "UTC+14",
      "MAGT" -> "UTC+12",
      "MART" -> "UTC-09:30",
      "MAWT" -> "UTC+05",
      "MDT" -> "UTC-06",
      "MET" -> "UTC+01",
      "MEST" -> "UTC+02",
      "MHT" -> "UTC+12",
      "MIST" -> "UTC+11",
      "MIT" -> "Pacific/Apia",
      "MMT" -> "UTC+06:30",
      "MSK" -> "UTC+03",
      "MST" -> "UTC-07", // Mountain Standard Time (North America). Alternative: UTC+08.
      "MUT" -> "UTC+04",
      "MVT" -> "UTC+05",
      "MYT" -> "UTC+08",
      "NCT" -> "UTC+11",
      "NDT" -> "UTC-02:30",
      "NFT" -> "UTC+11",
      "NOVT" -> "UTC+07",
      "NET" -> "Asia/Yerevan",
      "NPT" -> "UTC+05:45",
      "NST" -> "Pacific/Auckland",
      "NT" -> "UTC-03:30",
      "NUT" -> "UTC-11",
      "NZDT" -> "UTC+13",
      "NZST" -> "UTC+12",
      "OMST" -> "UTC+06",
      "ORAT" -> "UTC+05",
      "PDT" -> "UTC-07",
      "PET" -> "UTC-05",
      "PETT" -> "UTC+12",
      "PGT" -> "UTC+10",
      "PHOT" -> "UTC+13",
      "PHT" -> "UTC+08",
      "PKT" -> "UTC+05",
      "PMDT" -> "UTC-02",
      "PMST" -> "UTC-03",
      "PNT" -> "America/Phoenix",
      "PONT" -> "UTC+11",
      "PRT" -> "America/Puerto_Rico",
      "PST" -> "America/Los_Angeles", // Pacific Standard Time (North America). Alternative: UTC+08.
      "PWT" -> "UTC+09",
      "PYST" -> "UTC-03",
      "PYT" -> "UTC-04",
      "RET" -> "UTC+04",
      "ROTT" -> "UTC-03",
      "SAKT" -> "UTC+11",
      "SAMT" -> "UTC+04",
      "SAST" -> "UTC+02",
      "SBT" -> "UTC+11",
      "SCT" -> "UTC+04",
      "SDT" -> "UTC-10",
      "SGT" -> "UTC+08",
      "SLST" -> "UTC+05:30",
      "SRET" -> "UTC+11",
      "SRT" -> "UTC-03",
      // Samoa Standard Time. Alternative: Singapore Standard Time is UTC+08 (prefer the unambiguous "SGT" alias
      // for that).
      "SST" -> "UTC-11",
      "SYOT" -> "UTC+03",
      "TAHT" -> "UTC-10",
      "THA" -> "UTC+07",
      "TFT" -> "UTC+05",
      "TJT" -> "UTC+05",
      "TKT" -> "UTC+13",
      "TLT" -> "UTC+09",
      "TMT" -> "UTC+05",
      "TRT" -> "UTC+03",
      "TOT" -> "UTC+13",
      "TVT" -> "UTC+12",
      "ULAST" -> "UTC+09",
      "ULAT" -> "UTC+08",
      "UTC" -> "UTC+0",
      "UYST" -> "UTC-02",
      "UYT" -> "UTC-03",
      "UZT" -> "UTC+05",
      "VET" -> "UTC-04",
      "VLAT" -> "UTC+10",
      "VOLT" -> "UTC+04",
      "VOST" -> "UTC+06",
      "VST" -> "Asia/Ho_Chi_Minh",
      "VUT" -> "UTC+11",
      "WAKT" -> "UTC+12",
      "WAST" -> "UTC+02",
      "WAT" -> "UTC+01",
      "WEST" -> "UTC+01",
      "WET" -> "UTC+0",
      "WIB" -> "UTC+07",
      "WIT" -> "UTC+09",
      "WITA" -> "UTC+08",
      "WGST" -> "UTC-02",
      "WGT" -> "UTC-03",
      "WST" -> "UTC+08",
      "YAKT" -> "UTC+09",
      "YEKT" -> "UTC+05"
    )

    val duplicateKeys = entries.groupBy(_._1).collect { case (k, vs) if vs.size > 1 => k }
    require(
      duplicateKeys.isEmpty,
      s"TimeZones.aliases has duplicate keys, which would silently shadow earlier entries: ${duplicateKeys.mkString(", ")}"
    )

    entries.flatMap { case (k, v) =>
      Try(ZoneId.of(v)).toOption.map { zone =>
        k.toLowerCase(Locale.ENGLISH) -> zone
      }.toMap
    }.toMap
  }
}
