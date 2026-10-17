package player

import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.player.base.{MediaPlayer, State}

import scala.math.Integral.Implicits.infixIntegralOps


trait VlcMedia extends CallbackMedia:
  def getName: String
  def getHash: (String, Int)
  def onStageShow(): Unit
  def onStageHide(): Unit


extension (player: MediaPlayer) private def isFinished =
  val state = player.media.info.state
  state == State.STOPPED || state == State.ENDED


private def time2str(msTotal: Long): String =
  val sTotal = msTotal / 1000
  val (mTotal, s) = sTotal /% 60
  val (h, m) = mTotal /% 60
  if (h > 0) f"$h%d:$m%02d:$s%02d" else f"$m%02d:$s%02d"
