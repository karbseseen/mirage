package vlc

import javafx.scene.input.KeyCode
import scalafx.scene.Scene
import scalafx.scene.input.KeyEvent
import util.fx.addEventHandler


private trait VlcKeyHandler(using stage: VlcStage) extends Scene:
  import stage.player
  import player.controls

  this.addEventHandler(KeyEvent.KeyPressed): event =>
    Some(event.getCode).collect:
      case KeyCode.SPACE          => stage.togglePause()
      case KeyCode.OPEN_BRACKET   => stage.updateSpeed((player.status.rate - 0.1f) max 0.3f,  send = true)
      case KeyCode.CLOSE_BRACKET  => stage.updateSpeed((player.status.rate + 0.1f) min 4f,    send = true)
      case KeyCode.LEFT           => seek(-10_000, -1, event.isShiftDown)
      case KeyCode.RIGHT          => seek( 10_000,  1, event.isShiftDown)

  private def seek(timeOffset: Long, frameOffset: Long, isFrame: Boolean): Unit =
    if (isFrame) player.controls.setPause(true)
    val newTime = Option(stage.videoTrack.get) match
      case Some(video) if isFrame =>
        val time = player.status.time
        val frame = (time * video.frameRate) roundDiv (video.frameRateBase * 1000)
        val newFrame = frame + frameOffset
        (newFrame * 1000 * video.frameRateBase) roundDiv video.frameRate
      case _ =>
        player.status.time + timeOffset
    controls.setTime(newTime)
    stage.updateState(time = newTime, seek = true, send = true)

  extension (long: Long)
    private infix def roundDiv(other: Long) =
      if ((long >> 63) == (other >> 63))
        (long + (other / 2)) / other
      else
        (long - (other / 2)) / other
