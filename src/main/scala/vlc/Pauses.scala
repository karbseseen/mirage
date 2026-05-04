package vlc

import javafx.beans.property.SimpleBooleanProperty
import uk.co.caprica.vlcj.player.base.MediaPlayer
import util.also


private class Pauses(player: MediaPlayer):

  private var requests = 0
  private var requestBit = 1

  class Requester private[Pauses] (val requestBit: Int):
    def requestPause(pause: Boolean): Unit =
      val newRequests = if (pause) requests | requestBit else requests & ~requestBit
      if (requests == 0 && newRequests != 0) player.controls.setPause(true)
      else if (requests != 0 && newRequests == 0) player.controls.setPause(false)
      requests = newRequests

  def createRequester: Requester =
    if (requestBit == 0) throw Exception("Reached out of Requesters")
    else Requester(requestBit).also(_ => requestBit <<= 1)

  class RequestProp private[Pauses] extends SimpleBooleanProperty(Pauses.this, "part"):
    val requester: Requester = createRequester
    addListener { (_,_,value) => requester.requestPause(value) }

  def createRequestProp = new RequestProp
