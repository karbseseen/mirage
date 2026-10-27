package player

import core.TaskQueue
import core.main.MainApp
import player.Media.FileId
import player.default.DefaultPlayer
import scalafx.Includes.{jfxProperty2sfx, jfxScene2sfx}
import scalafx.application.Platform.runLater
import scalafx.scene.Scene
import scalafx.scene.layout.{Background, StackPane}
import scalafx.scene.paint.Color
import scalafx.stage.{Screen, WindowEvent}
import util.also
import util.fx.{NotificationStage, addEventHandler, selfRemoveListener, setIcon}


private class PlayerStage(val fileId: FileId) extends NotificationStage:
  this.setIcon()
  fullScreenExitHint = ""

  val root: StackPane = new StackPane:
    background = Background.fill(Color.Black)
    onMouseClicked = event => if (event.getClickCount == 2) fullScreen = !fullScreen()
    children = notificationBox

  scene = Scene(root)
  show()


  private var programChange = false
  private var sizeChanged = false
  private var sizeRatio = 0.0

  TaskQueue.scheduleSingle(1000):
    runLater:
      selfRemoveListener(width, height): _ =>
        (!programChange).also:
          if (_) sizeChanged = true

  updateRatio(16 / 9.0)
  centerOnScreen()
  def updateRatio(ratio: Double): Unit =
    if (!sizeChanged && (sizeRatio - ratio).abs > 1e-9)
      programChange = true
      sizeRatio = ratio
      val screenBounds = Screen.primary.visualBounds
      val w = (screenBounds.width min (screenBounds.height * ratio)) * 0.6
      width   = w         + (MainApp.stage.width()  - MainApp.stage.scene().width())
      height  = w / ratio + (MainApp.stage.height() - MainApp.stage.scene().height())
      programChange = false


object PlayerStage:

  private var current = Option.empty[PlayerStage]
  private var nextMedia = Option.empty[Media]

  def play(media: Option[Media]): Unit =
    current match
      case None =>
        current = media.map(newStage)
      case Some(stage) if Option(stage).zip(media).exists(_.fileId == _.fileId) =>
        stage.requestFocus()
      case Some(stage) =>
        nextMedia = media
        stage.close()

  private def newStage(media: Media): PlayerStage =
    new PlayerStage(media.fileId):
      this.addEventHandler(WindowEvent.WindowHidden): _ =>
        current = nextMedia.map(newStage)
        nextMedia = None

      private val player = IntroPlayer.fileConfig() match
        case "" => DefaultPlayer(media)
        case _  => IntroPlayer(media)
      player.stage() = this
      player.start()
