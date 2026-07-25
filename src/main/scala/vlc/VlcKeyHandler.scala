package vlc

import javafx.event.EventHandler
import javafx.scene.input.{KeyCode, KeyEvent}


private class VlcKeyHandler(stage: VlcStage) extends EventHandler[KeyEvent]:
  import stage.player
  import player.controls

  def handle(event: KeyEvent): Unit =
    val consume = Some(event.getCode).collect:
      case KeyCode.SPACE          => stage.togglePause()
      case KeyCode.OPEN_BRACKET   => updateSpeed { (math.round(player.status.rate * 10) - 1) max 3 }
      case KeyCode.CLOSE_BRACKET  => updateSpeed { (math.round(player.status.rate * 10) + 1) min 40 }
      case KeyCode.LEFT if seekSingleFrame(event) =>
        for video <- Option(stage.videoTrack.get) do
          controls.skipTime(-1000L * video.frameRateBase / video.frameRate - 1)
      case KeyCode.RIGHT if seekSingleFrame(event) => controls.nextFrame()
      case KeyCode.LEFT => controls.skipTime(-10_000)
      case KeyCode.RIGHT => controls.skipTime(10_000)
    if (consume.nonEmpty) event.consume()

  private def seekSingleFrame(event: KeyEvent) = event.isShiftDown && !player.status.isPlaying

  private def updateSpeed(valueX10: Int): Unit =
    if (controls.setRate(valueX10 / 10f))
      stage.updateState(speedX10 = valueX10.toByte, send = true)
      stage.showNewSpeedText(valueX10.toByte)
