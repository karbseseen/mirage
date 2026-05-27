package vlc

import javafx.event.EventHandler
import javafx.scene.input.{KeyCode, KeyEvent}


private class VlcKeyHandler(stage: VlcStage) extends EventHandler[KeyEvent]:
  import stage.player
  import player.controls

  def handle(event: KeyEvent): Unit =
    val consume = Some(event.getCode).collect:
      case KeyCode.SPACE          => stage.togglePause()
      case KeyCode.LEFT           => controls.skipTime(-10_000)
      case KeyCode.RIGHT          => if (event.isShiftDown) controls.nextFrame() else controls.skipTime(10_000)
      case KeyCode.OPEN_BRACKET   => updateSpeed { (math.round(player.status.rate * 10) - 1) max 3 }
      case KeyCode.CLOSE_BRACKET  => updateSpeed { (math.round(player.status.rate * 10) + 1) min 40 }
    if (consume.nonEmpty) event.consume()

  private def updateSpeed(valueX10: Int): Unit =
    if (controls.setRate(valueX10 / 10f))
      stage.updateState(speedX10 = valueX10.toByte, send = true)
      stage.showNewSpeedText(valueX10.toByte)
