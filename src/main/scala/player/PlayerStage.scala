package player

import core.main.MainApp.decoratorSize
import javafx.beans.property.{ReadOnlyObjectProperty, SimpleObjectProperty}
import javafx.beans.value.ObservableValue
import javafx.beans.{InvalidationListener, Observable}
import javafx.scene as jfxs
import player.Media.FileId
import player.default.DefaultPlayer
import scalafx.Includes.jfxProperty2sfx
import scalafx.scene.Scene
import scalafx.scene.layout.{Background, StackPane}
import scalafx.scene.paint.Color
import scalafx.stage.{Screen, WindowEvent}
import util.fx.{NotificationStage, addEventHandler, setIcon}


private class PlayerStage(val fileId: FileId) extends NotificationStage:
  this.setIcon()
  fullScreenExitHint = ""

  val player = SimpleObjectProperty(this, "player", null: Player)
  player.addListener: (_, oldPlayer, newPlayer) =>
    Option(oldPlayer).foreach(_._stage() = null)
    Option(newPlayer).foreach(_._stage() = this)

  val root: StackPane = new StackPane:
    background = Background.fill(Color.Black)
    onMouseClicked = event => if (event.getClickCount == 2) fullScreen = !fullScreen()
    children = notificationBox

  scene = Scene(root)
  show()


  private var programChange = false
  private var sizeChanged = false
  private var sizeRatio = 0.0

  new InvalidationListener:
    width.addListener(this)
    height.addListener(this)
    def invalidated(o: Observable): Unit =
      if (!programChange)
        sizeChanged = true
        width.removeListener(this)
        height.removeListener(this)

  updateSize(16 / 9.0)
  def updateSize(ratio: Double): Unit =
    if (!sizeChanged && (sizeRatio - ratio).abs > 1e-9)
      programChange = true
      sizeRatio = ratio
      val screenBounds = Screen.primary.visualBounds
      val w = (screenBounds.width min (screenBounds.height * ratio)) * 0.6
      width = w + decoratorSize.width
      height = w / ratio + decoratorSize.height
      centerOnScreen()
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
      player() = IntroPlayer.fileConfig() match
        case "" => DefaultPlayer(media)
        case _  => IntroPlayer(media)
      player().start()


  trait Child:
    self: Player =>

    private[PlayerStage] val _stage = SimpleObjectProperty(this, "stage", null: PlayerStage)
    def stage: ReadOnlyObjectProperty[PlayerStage] = _stage
    val scene: ObservableValue[jfxs.Scene] = stage.flatMap(_.scene)
