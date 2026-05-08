package vlc

import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.player.base.{MediaPlayer, State}


trait VlcMedia extends CallbackMedia:
  def getName: String
  def shutdown(): Unit


extension (player: MediaPlayer) private def isFinished =
  val state = player.media.info.state
  state == State.STOPPED || state == State.ENDED
