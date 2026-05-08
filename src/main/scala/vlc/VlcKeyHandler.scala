package vlc

import constant.Tr
import javafx.event.EventHandler
import javafx.scene.input.{KeyCode, KeyEvent}
import uk.co.caprica.vlcj.player.base.Marquee


private class VlcKeyHandler(stage: VlcStage) extends EventHandler[KeyEvent]:
  import stage.player
  import player.controls

  def handle(event: KeyEvent): Unit =
    Some(event.getCode).collect:
      case KeyCode.SPACE          => stage.togglePause()
      case KeyCode.LEFT           => controls.skipTime(-10_000)
      case KeyCode.RIGHT          => if (event.isShiftDown) controls.nextFrame() else controls.skipTime(10_000)
      case KeyCode.OPEN_BRACKET   => updateSpeed { ((Math.round(player.status.rate * 10) - 1).toFloat / 10) max 0.3 }
      case KeyCode.CLOSE_BRACKET  => updateSpeed { ((Math.round(player.status.rate * 10) + 1).toFloat / 10) min 4 }

  private def updateSpeed(value: Float): Unit =
    controls.setRate(value)
    player.marquee.set(Marquee.marquee
      .text(s"${Tr.speed.get} = $value")
      .location(50, 50)
    )
