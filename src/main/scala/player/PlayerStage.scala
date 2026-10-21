package player

import player.default.DefaultPlayer
import scalafx.scene.Scene
import scalafx.scene.layout.{Background, StackPane}
import scalafx.scene.paint.Color
import scalafx.stage.WindowEvent
import util.fx.{NotificationStage, addEventHandler, setIcon}


private class PlayerStage(player: Player) extends NotificationStage:
  this.setIcon()
  fullScreenExitHint = ""

  private val root: StackPane = new StackPane:
    background = Background.fill(Color.Black)
    onMouseClicked = event => if (event.getClickCount == 2) fullScreen = !fullScreen()
    children = player.views ::: notificationBox :: Nil

  scene = Scene(root, 800, 600)
  show()


object PlayerStage:

  private var current = Option.empty[(Player, PlayerStage)]
  private var nextTask = Option.empty[() => Unit]

  def play(media: Option[Media]): Unit =
    current match
      case None => media.foreach(newStage)
      case Some(player, stage) if media.exists(_.fileId == player.media.fileId) => stage.requestFocus()
      case Some(player, stage) =>
        nextTask = media.map(media => () => newStage(media))
        stage.close()

  private def newStage(media: Media): Unit =
    val player = DefaultPlayer(media)
    val stage = new PlayerStage(player):
      this.addEventHandler(WindowEvent.WindowHidden): _ =>
        current = None
        nextTask.foreach(_())
        nextTask = None
    current = Some(player, stage)
