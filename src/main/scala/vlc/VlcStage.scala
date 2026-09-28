package vlc

import byte_codec.ByteCodec.CompactULong
import constant.Tr
import core.TaskQueue.{Task, schedulePeriodic}
import core.main.MainApp
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.geometry.Rectangle2D
import javafx.scene.input.KeyEvent
import p2p.RoomSync
import p2p.base.Message.{Counter, PlayerState}
import p2p.base.P2p
import p2p.phone.Phone
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
import uk.co.caprica.vlcj.media.VideoTrackInfo
import uk.co.caprica.vlcj.player.base.Marquee
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import vlc.VlcStage.MinTimeSyncPeriod
import vlc.loading.Loading

import java.util.function.UnaryOperator


class VlcStage private (val media: VlcMedia, playerOptions: String*) extends Stage:

  title = media.getName
  icons.addAll(fx.getIconImages)
  fullScreenExitHint = ""

  private val factory = new MediaPlayerFactory
  val player: EmbeddedMediaPlayer = factory.mediaPlayers.newEmbeddedMediaPlayer
  private var pauseTask = Option.empty[Task]

  private val eventHandler = VlcHandler(this)
  def videoTrack: ReadOnlyObjectProperty[VideoTrackInfo] = eventHandler.videoTrack
  def isBuffering: Boolean = eventHandler.isBuffering

  val phone: Phone = new Phone

  media.onStageShow()
  player.events.addMediaPlayerEventListener(eventHandler)
  onHiding = _ =>
    media.onStageHide()
    player.release()
    factory.release()
    pauseTask.foreach(_.cancel())
    phone.close()

  private[vlc] val root: StackPane = new StackPane:
    background = Background.fill(Color.Black)
    onMouseClicked = event => if (event.getClickCount == 2) fullScreen = !fullScreen()

  private[vlc] val videoCropCoef = SimpleDoubleProperty(this, "videoCropCoef")
  private[vlc] val imageView: ImageView = new ImageView:
    fitWidth <== root.width
    fitHeight <== root.height
    preserveRatio = true
    player.videoSurface.set(ImageViewVideoSurface(this))
    viewport <== Bindings.createObjectBinding(
      () =>
        val (videoWidth, videoHeight) = Option(videoTrack.get).fold(1.0, 1.0):
          video => (video.width.toDouble, video.height.toDouble)
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
      videoTrack, videoCropCoef, root.width, root.height
    )

  private[vlc] val controls: Controls = new Controls(this)
  private[vlc] val loading: Loading = new Loading

  scene = new Scene(root, 800, 600):
    content = imageView :: loading :: controls :: controls.extraParts
    this.addEventFilter(KeyEvent.KEY_PRESSED, VlcKeyHandler(VlcStage.this))

  applyControlHide(this)

  show()
  player.media.play(media, playerOptions*)


  def togglePause(): Unit =
    if (player.isFinished) player.media.play(media)
    else player.controls.setPause(player.status.isPlaying)

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
    extension (counter: Counter) inline def incIf(condition: Boolean): Counter =
      if (condition) counter.inc else counter
    val newState = RoomSync.mergedState.updateAndGet: oldState =>
      if (oldState.hash eq Hash.empty) oldState
      else oldState.copy(
        time          = time,
        speedX10      = if (speedX10 > 0) speedX10 else oldState.speedX10,
        seekCounter   = oldState.seekCounter.incIf(seek),
        speedCounter  = oldState.speedCounter.incIf(speedX10 > 0 && speedX10 != oldState.speedX10),
        pauseCounter  = oldState.pauseCounter.incIf(pause.exists(_ != oldState.pause)),
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
