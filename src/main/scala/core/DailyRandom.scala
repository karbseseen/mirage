package core

import p2p.base.P2p
import scalafx.Includes.jfxProperty2sfx

import java.time.LocalDate
import scala.util.Random
import util.also


class DailyRandom:
  val now: LocalDate = LocalDate.now
  val seed: Long = now.toEpochDay + (P2p.roomName().hashCode.toLong << 32)
  def random: Random = Random(seed).also: random =>
    random.nextInt
    random.nextInt
    random.nextInt
