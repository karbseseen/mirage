package player

import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.geometry.Rectangle2D
import javafx.scene.layout as jfxsl
import javafx.{scene as jfxsc, stage as jfxst}
import player.Media
import scalafx.Includes.{jfxProperty2sfx, jfxRegion2sfx}
import scalafx.application.Platform.runLater
import scalafx.scene.Node
import scalafx.scene.image.ImageView
import scalafx.stage.WindowEvent
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.media.{TrackType, VideoTrackInfo}
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter}
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import util.WakeLock
import util.fx.registerEventHandler

import scala.jdk.CollectionConverters.*


private abstract class Player(val media: Media) extends PlayerGetSet:

  private val vlcFactory = new MediaPlayerFactory
  protected val vlc: EmbeddedMediaPlayer = vlcFactory.mediaPlayers.newEmbeddedMediaPlayer

  protected type HandlerType <: Handler
  protected def createHandler: HandlerType
  protected val vlcHandler: HandlerType = createHandler
  def videoTrackInfo: ReadOnlyObjectProperty[VideoTrackInfo] = vlcHandler.videoTrack

  val videoCropCoef = SimpleDoubleProperty(this, "videoCropCoef")

  private val wakeLock = new WakeLock

  def close(): Unit =
    wakeLock.unlock()
    vlc.release()
    vlcFactory.release()
    media.close()

  def views: List[Node] = surface :: Nil

  protected val surface: ImageView = new ImageView:
    vlc.videoSurface.set(ImageViewVideoSurface(this))
    preserveRatio = true

    private val parentRegion = parent.map:
      case region: jfxsl.Region => region
      case _ => null
    fitWidth <== parentRegion.flatMap(_.width).orElse(1)
    fitHeight <== parentRegion.flatMap(_.height).orElse(1)

    viewport <== Bindings.createObjectBinding(
      () =>
        val (videoWidth, videoHeight) = Option(videoTrackInfo.get).fold(1.0, 1.0):
          video => (video.width.toDouble, video.height.toDouble)
        val videoRatio = videoWidth / videoHeight
        val windowRatio = fitWidth() / fitHeight()
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
      videoTrackInfo, videoCropCoef, fitWidth, fitHeight
    )

  def scene: ReadOnlyObjectProperty[jfxsc.Scene] = surface.scene
  val stage: ObservableValue[jfxst.Stage] = scene.flatMap(_.windowProperty).map:
    case stage: jfxst.Stage => stage
    case _ => null
  stage.registerEventHandler(WindowEvent.WindowHiding)(_ => close())

  vlc.media.play(media)


  protected class Handler extends MediaPlayerEventAdapter:
    vlc.events.addMediaPlayerEventListener(this)

    def videoTrack: ReadOnlyObjectProperty[VideoTrackInfo] = _videoTrack
    private val _videoTrack = SimpleObjectProperty(this, "videoTrack", null: VideoTrackInfo)

    override def playing(player: MediaPlayer):  Unit = wakeLock.lock()
    override def paused(player: MediaPlayer):   Unit = wakeLock.unlock()
    override def stopped(player: MediaPlayer):  Unit = wakeLock.unlock()
    override def finished(player: MediaPlayer): Unit = wakeLock.unlock()

    override def elementaryStreamSelected(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      if (trackType == TrackType.VIDEO)
        player.media.info.videoTracks.asScala
          .find(_.id == id)
          .foreach { video => runLater(_videoTrack() = video) }
