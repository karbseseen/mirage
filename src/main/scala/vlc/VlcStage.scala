package vlc

import byte_codec.ByteCodec.CompactULong
import constant.Tr
import core.TaskQueue.{Task, schedulePeriodic}
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.geometry.Rectangle2D
import javafx.scene.input.KeyEvent
import p2p.RoomSync
import p2p.base.Message.PlayerState
import p2p.base.P2p
import scalafx.Includes.jfxNumberBinding2sfx
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.Label
import scalafx.scene.image.ImageView
import scalafx.scene.layout.{Background, BackgroundFill, CornerRadii, StackPane}
import scalafx.scene.paint.Color
import scalafx.stage.{Stage, WindowEvent}
import torrent.Hash
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.base.Marquee
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import vlc.VlcStage.MinTimeSyncPeriod

import java.util.function.UnaryOperator


class VlcStage private (val media: VlcMedia, playerOptions: String*) extends Stage:

  title = media.getName
  icons.addAll(fx.getIconImages)
  fullScreenExitHint = ""

  private val factory = new MediaPlayerFactory
  val player: EmbeddedMediaPlayer = factory.mediaPlayers.newEmbeddedMediaPlayer
  private var pauseTask = Option.empty[Task]
  private val eventHandler = VlcHandler(this)
  def isBuffering: Boolean = eventHandler.isBuffering

  media.onStageShow()
  player.events.addMediaPlayerEventListener(eventHandler)
  onHiding = _ =>
    media.onStageHide()
    player.release()
    factory.release()
    pauseTask.foreach(_.cancel())

  private[vlc] val root: StackPane = new StackPane:
    background = Background.fill(Color.Black)
    onMouseClicked = event => if (event.getClickCount == 2) fullScreen = !fullScreen()

  private[vlc] val videoSize = SimpleObjectProperty(this, "videoSize", (1.0, 1.0))
  private[vlc] val videoCropCoef = SimpleDoubleProperty(this, "videoCropCoef")
  private[vlc] val imageView: ImageView = new ImageView:
    fitWidth <== root.width
    fitHeight <== root.height
    preserveRatio = true
    player.videoSurface.set(ImageViewVideoSurface(this))
    viewport <== Bindings.createObjectBinding(
      () =>
        val (videoWidth, videoHeight) = videoSize.get
        val videoRatio = videoWidth / videoHeight
        val windowRatio = root.width() / root.height()
        if (videoRatio > windowRatio)
          val videoCropWidth = videoHeight * windowRatio
          Rectangle2D(
            (videoWidth - videoCropWidth) / 2 * videoCropCoef.get,
            0,
            videoWidth * (1 - videoCropCoef.get) + videoCropWidth * videoCropCoef.get,
            videoHeight,
          )
        else
          val videoCropHeight = videoWidth / windowRatio
          Rectangle2D(
            0,
            (videoHeight - videoCropHeight) / 2 * videoCropCoef.get,
            videoWidth,
            videoHeight * (1 - videoCropCoef.get) + videoCropHeight * videoCropCoef.get,
          ),
      videoSize, videoCropCoef, root.width, root.height
    )

  private[vlc] val controls: Controls = new Controls(this)

  private[vlc] val loadingLabel = new Label:
    alignmentInParent = Pos.TopLeft
    alignment = Pos.Center
  private[vlc] val loading: StackPane = new StackPane:
    private val rootSize = Bindings.createDoubleBinding(
      () => root.width() min root.height() min 750,
      root.width, root.height
    )
    private val size = rootSize * 0.25
    maxWidth <== size
    maxHeight <== size
    alignmentInParent = Pos.Center
    visible = false
    managed <== visible

    background = Background(Array(BackgroundFill(
      Color.White,
      CornerRadii(100, asPercent = true),
      Insets.Empty,
    )))

    private val image = new ImageView("/vlc/loading.gif"):
      private val size = rootSize * 0.125
      fitWidth <== size
      fitHeight <== size
      preserveRatio = true
      alignmentInParent = Pos.Center

    loadingLabel.translateY <== image.layoutY + image.fitHeight
    loadingLabel.prefWidth <== this.width
    loadingLabel.prefHeight <== this.height - loadingLabel.translateY

    children = Seq(image, loadingLabel)

  scene = new Scene(root, 800, 600):
    content = imageView :: loading :: controls :: controls.extraParts
    this.addEventFilter(KeyEvent.KEY_PRESSED, VlcKeyHandler(VlcStage.this))

  applyControlHide(this)

  show()
  player.media.play(media, playerOptions*)


  def togglePause(): Unit =
    if (player.isFinished) player.media.play(media)
    else
      val setPause = player.status.isPlaying
      player.controls.setPause(setPause)
      updateState(pause = Some(setPause), send = true)
      if (setPause) startPauseTask: thisTask =>
        val state = RoomSync.mergedState.get
        if (state.pause) P2p.sendToAll(state)
        else thisTask.cancel()

  def showNewSpeedText(speedX10: Byte): Unit =
    player.marquee.set:
      Marquee.marquee
        .text(s"${Tr.speed.get} = ${speedX10 / 10f}")
        .location(50, 50)
        .timeout(2500)

  def updateState(
    pause: Option[Boolean] = None,
    time: Long = player.status.time,
    speedX10: Byte = -1,
    seek: Boolean = false,
    send: Boolean,
  ): PlayerState =
    inline def count(counter: Byte, condition: Boolean): Byte =
      if (condition) PlayerState.count(counter) else counter
    val newState = RoomSync.mergedState.updateAndGet: oldState =>
      if (oldState.hash eq Hash.empty) oldState
      else oldState.copy(
        time          = time,
        speedX10      = if (speedX10 > 0) speedX10 else oldState.speedX10,
        seekCounter   = count(oldState.seekCounter, seek),
        speedCounter  = count(oldState.speedCounter, speedX10 > 0 && speedX10 != oldState.speedX10),
        pauseCounter  = count(oldState.pauseCounter, pause.exists(_ != oldState.pause)),
      )
    if (!newState.pause) pauseTask.foreach(_.cancel())
    if (send && (newState.hash ne Hash.empty)) P2p.sendToAll(newState)
    newState

  def startPauseTask(task: Task => Unit): Unit =
    class TaskHolder:
      val pauseTask: Task = schedulePeriodic(MinTimeSyncPeriod, MinTimeSyncPeriod)(task(pauseTask))
    pauseTask.foreach(_.cancel())
    pauseTask = Some(TaskHolder().pauseTask)


object VlcStage:

  private[vlc] inline val MinTimeSyncPeriod = 1500

  private var next                  = Option.empty[(VlcMedia, List[String])]
  @volatile private var _instance   = Option.empty[VlcStage]
  def instance: Option[VlcStage] = _instance

  def play(media: Option[VlcMedia], options: List[String] = Nil): Unit =
    _instance match
      case None => media.foreach(newStage(_, options))
      case Some(stage) if media.contains(stage.media) => stage.requestFocus()
      case Some(stage) =>
        next = media.map((_, options))
        stage.close()

  private def newStage(media: VlcMedia, options: List[String]): Unit =
    val stage = VlcStage(media, options*)
    stage.addEventHandler(WindowEvent.WindowHidden, _ =>
      _instance = None
      next.foreach(newStage)
      next = None
    )
    stage.show()
    _instance = Some(stage)
