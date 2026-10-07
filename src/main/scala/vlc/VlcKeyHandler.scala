package vlc

import javafx.event.EventHandler
import javafx.scene.input.{KeyCode, KeyEvent}


private class VlcKeyHandler(stage: VlcStage) extends EventHandler[KeyEvent]:
  import stage.player
  import player.controls

  def handle(event: KeyEvent): Unit =
    val consume = Some(event.getCode).collect:
      case KeyCode.SPACE          => stage.togglePause()
      case KeyCode.OPEN_BRACKET   => stage.updateSpeed((player.status.rate - 0.1f) max 0.3f,  send = true)
      case KeyCode.CLOSE_BRACKET  => stage.updateSpeed((player.status.rate + 0.1f) min 4f,    send = true)
      case KeyCode.LEFT if seekSingleFrame(event) =>
        for video <- Option(stage.videoTrack.get) do
          controls.skipTime(-1000L * video.frameRateBase / video.frameRate - 1)
      case KeyCode.RIGHT if seekSingleFrame(event) => controls.nextFrame()
      case KeyCode.LEFT => controls.skipTime(-10_000)
      case KeyCode.RIGHT => controls.skipTime(10_000)
    if (consume.nonEmpty) event.consume()

  private def seekSingleFrame(event: KeyEvent) = event.isShiftDown && !player.status.isPlaying
