package util

import java.time.{LocalDate, Month}


enum EasterType:
  case Catholic, Orthodox

def getEasterDate(year: Int, easterType: EasterType): LocalDate =
  val (m, n) = easterType match
    case EasterType.Catholic =>
      val k = year / 100
      val p = (13 + 8 * k) / 25
      val q = k / 4
      val M = (15 - p + k - q) % 30
      val N = (4 + k - q) % 7
      (M, N)
    case EasterType.Orthodox =>
      (15, 6)

  val a = year % 19
  val b = year % 4
  val c = year % 7
  val d = (19 * a + m) % 30
  val e = (2 * b + 4 * c + 6 * d + n) % 7

  val offset = easterType match
    case EasterType.Catholic if d == 29 && e == 6 => -7
    case EasterType.Catholic if d == 28 && e == 6 && (11 * m + 11) % 30 < 19 => -7
    case EasterType.Orthodox if year >= 1900 => 13
    case _ => 0

  LocalDate.of(year, Month.MARCH, 22).plusDays(d + e + offset)
