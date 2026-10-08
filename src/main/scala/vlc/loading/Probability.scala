package vlc.loading

import util.{EasterType, getEasterDate}

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate, Month}
import scala.language.implicitConversions
import scala.util.Random


type Probabilities = List[Probability]

private class Probability(val value: Int, val group: ProbabilityGroup)

private object Probability:
  inline def maxValue: Int = Int.MaxValue
  implicit inline def apply(value: Int): Probabilities = default(value)
  implicit inline def toList(single: Probability): List[Probability] = single :: Nil

  val default: ProbabilityGroup = _ => 10

  val christmas: ProbabilityGroup = (today: LocalDate) =>
    (today.getDayOfMonth, today.getMonth) match
      case (day, Month.DECEMBER) if day < 23 => 40 + day
      case (_, Month.DECEMBER) | (1, Month.JANUARY) => Probability.maxValue
      case (day, Month.JANUARY) => 41 - day
      case (day, Month.FEBRUARY) => 10 - day / 3
      case _ => 0

  val maslenitsa: ProbabilityGroup = (today: LocalDate) =>
    today.getMonth match
      case Month.FEBRUARY | Month.MARCH =>
        val values = List(
          (EasterType.Orthodox, -56, DayOfWeek.SUNDAY),
          (EasterType.Catholic, -48, DayOfWeek.TUESDAY),
        ).flatMap: (easterType, diffFromEaster, mainDay) =>
          val start = getEasterDate(today.getYear, easterType).plusDays(diffFromEaster)
          val weekDay = ChronoUnit.DAYS.between(today, start).toInt + 1
          Option.when(weekDay >= 1 && weekDay <= 7):
            if (DayOfWeek.of(weekDay) == mainDay) Probability.maxValue
            else 60
        values.maxOption getOrElse 0
      case _ => 0


private abstract class ProbabilityGroup private:
  def apply(value: Int): Probability = new Probability(value, this)
  def probability(date: LocalDate): Int


extension [T](it: Iterable[T])
  private def selectWithProbability(probability: T => Int)(using random: Random): T =
    val allVector = it
      .map(item => (item, probability(item)))
      .filter(_._2 > 0)
      .toVector
    val maxVector = allVector
      .filter(_._2 == Probability.maxValue)
      .map((item, _) => (item, 1))
    val vector = if (maxVector.nonEmpty) maxVector else allVector

    val halfSums = vector
      .scanLeft(0)(_ + _._2)
      .drop(1)
    val rand = random.nextInt(halfSums.last)
    val index = halfSums.search(rand + 1).insertionPoint
    vector(index)._1
