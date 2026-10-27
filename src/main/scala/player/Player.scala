package player

import com.sun.jna.{Native, NativeLong, Pointer}
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.geometry.Rectangle2D
import javafx.scene as jfxs
import javafx.scene.image.{PixelBuffer, PixelFormat, WritableImage}
import javafx.scene.layout as jfxsl
import player.Player.FrameBuffer
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx, jfxRegion2sfx}
import scalafx.application.Platform.runLater
import scalafx.scene.Node
import scalafx.scene.image.ImageView
import scalafx.stage.WindowEvent
import uk.co.caprica.vlcj.binding.internal.{libvlc_display_callback_t, libvlc_lock_callback_t, libvlc_video_cleanup_cb, libvlc_video_format_cb}
import uk.co.caprica.vlcj.binding.lib.LibVlc.{libvlc_video_set_callbacks, libvlc_video_set_format_callbacks}
import uk.co.caprica.vlcj.binding.lib.{Kernel32, LibC}
import uk.co.caprica.vlcj.binding.support.types.size_t
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.media.{TrackType, VideoTrackInfo}
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter, State, TrackDescription}
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters.getVideoSurfaceAdapter
import util.SystemInfo.OS
import util.fx.SfxEvents.registerEventHandler
import util.fx.selfRemoveListener
import util.{SystemInfo, WakeLock, also, let}

import java.nio.{ByteBuffer, ByteOrder}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*


private abstract class Player(updateStageSize: Boolean = false):
  protected val media: CallbackMedia

  private val wakeLock = new WakeLock
  private val vlcFactory = new MediaPlayerFactory
  protected val vlc: EmbeddedMediaPlayer = vlcFactory.mediaPlayers.newEmbeddedMediaPlayer
  def start(): Unit = vlc.media.play(media)

  val videoCropCoef = SimpleDoubleProperty(this, "videoCropCoef")

  def videoTrackInfo: ReadOnlyObjectProperty[VideoTrackInfo] = _videoTrackInfo
  private val _videoTrackInfo = SimpleObjectProperty(this, "videoTrackInfo", null: VideoTrackInfo)

  val stage = SimpleObjectProperty(this, "stage", null: PlayerStage)
  val scene: ObservableValue[jfxs.Scene] = stage.flatMap(_.scene)


  if (updateStageSize)
    selfRemoveListener(stage, videoTrackInfo): _ =>
      val opt = Option(stage()) zip Option(videoTrackInfo())
      opt.foreach { (stage, info) => stage.updateRatio(info.width / info.height.toDouble) }
      opt.nonEmpty

  def views: List[Node] = surfaceView :: Nil
  stage.addListener: (_, oldStage, newStage) =>
    val views = this.views.map(_.delegate)
    Option(oldStage).foreach { _.root.children.removeAll(views*) }
    Option(newStage).foreach { _.root.children.insertAll(0, views.filter(_.getParent == null)) }

  stage.registerEventHandler(WindowEvent.WindowHiding)(_ => close())
  private var closed = false
  final def close(): Boolean =
    (!closed).also:
      if (_)
        closed = true
        onClose()
  protected def onClose(): Unit =
    wakeLock.unlock()
    vlc.release()
    vlcFactory.release()

  private val surfaceView: ImageView = new ImageView:
    vlc.videoSurface.set(new Surface)
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


  def isPlaying: Boolean = vlc.status.isPlaying
  def time: Long = vlc.status.time
  def length: Long = vlc.status.length
  def speedX10: Int = (vlc.status.rate * 10).round

  def volume: Int = vlc.audio.volume
  def videoTrack: Int = vlc.video.track
  def audioTrack: Int = vlc.audio.track
  def subtitleTrack: Int = vlc.subpictures.track

  def volume_=(value: Int): Boolean = vlc.audio.setVolume(value)
  def videoTrack_=(value: Int): Int = vlc.video.setTrack(value)
  def audioTrack_=(value: Int): Int = vlc.audio.setTrack(value)
  def subtitleTrack_=(value: Int): Int = vlc.subpictures.setTrack(value)

  def videoTrackDescriptions: Iterable[TrackDescription] = vlc.video.trackDescriptions.asScala
  def audioTrackDescriptions: Iterable[TrackDescription] = vlc.audio.trackDescriptions.asScala
  def subtitleTrackDescriptions: Iterable[TrackDescription] = vlc.subpictures.trackDescriptions.asScala

  def isFinished: Boolean = vlc.media.info.state match
    case State.STOPPED | State.ENDED | State.ERROR => true
    case _ => false
  def restartIfFinished(): Boolean = isFinished.also(if (_) vlc.media.play(media))


  protected infix def addHandler(handler: MediaPlayerEventAdapter): Unit =
    vlc.events.addMediaPlayerEventListener(handler)

  this addHandler new MediaPlayerEventAdapter:
    override def playing(player: MediaPlayer):  Unit = wakeLock.lock()
    override def paused(player: MediaPlayer):   Unit = wakeLock.unlock()
    override def stopped(player: MediaPlayer):  Unit = wakeLock.unlock()
    override def finished(player: MediaPlayer): Unit = wakeLock.unlock()
    override def elementaryStreamSelected(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      if (trackType == TrackType.VIDEO)
        player.media.info.videoTracks.asScala
          .find(_.id == id)
          .foreach { video => runLater(_videoTrackInfo() = video) }


  protected def bufferNum: Int = 1
  protected def onRender(buffer: ByteBuffer, width: Int, height: Int): Unit = ()

  private class Surface extends VideoSurface(getVideoSurfaceAdapter):
    private var buffers: IArray[FrameBuffer] = uninitialized
    private var bufferIndex = 0

    private val setupCallback: libvlc_video_format_cb = (opaque, chroma, width, height, pitches, lines) =>
      chroma.getPointer.write(0, "RV32".getBytes, 0, 4)
      pitches.getPointer.setInt(0, width.getValue * 4)
      lines.getPointer.setInt(0, height.getValue)
      buffers = IArray.fill(bufferNum) { FrameBuffer(width.getValue, height.getValue) }
      1

    private val cleanupCallback: libvlc_video_cleanup_cb = _ =>
      buffers.foreach(_.close())

    private val lockCallback: libvlc_lock_callback_t = (_, planes) =>
      bufferIndex = (bufferIndex + 1).let(i => if (i == buffers.length) 0 else i)
      planes.getPointer.setPointer(0, buffers(bufferIndex).pointer)
      Pointer.createConstant(bufferIndex)

    private val displayCallback: libvlc_display_callback_t = (_, picture) =>
      val buffer: FrameBuffer = buffers(Pointer.nativeValue(picture).toInt)
      onRender(buffer.byteBuffer, buffer.pixelBuffer.getWidth, buffer.pixelBuffer.getHeight)
      runLater:
        buffer.pixelBuffer.updateBuffer(_ => null)
        surfaceView.setImage(buffer.image)

    def attach(player: MediaPlayer): Unit =
      libvlc_video_set_format_callbacks(player.mediaPlayerInstance, setupCallback, cleanupCallback)
      libvlc_video_set_callbacks(player.mediaPlayerInstance, lockCallback, null, displayCallback, null)


private object Player:

  private class FrameBuffer(width: Int, height: Int):
    private inline val vlcAlignment = 32

    val byteBuffer: ByteBuffer = ByteBuffer
      .allocateDirect(width * height * 4 + vlcAlignment)
      .alignedSlice(vlcAlignment)
      .order(ByteOrder.nativeOrder)
    val pointer: Pointer = Native.getDirectBufferPointer(byteBuffer)
    val pixelBuffer = PixelBuffer(width, height, byteBuffer, PixelFormat.getByteBgraPreInstance)
    val image = WritableImage(pixelBuffer)

    SystemInfo.os match
      case OS.Windows => Kernel32.INSTANCE.VirtualLock(pointer, size_t(byteBuffer.capacity))
      case OS.MacOS | OS.Linux => LibC.INSTANCE.mlock(pointer, NativeLong(byteBuffer.capacity))

    def close(): Unit = SystemInfo.os match
      case OS.Windows => Kernel32.INSTANCE.VirtualUnlock(pointer, size_t(byteBuffer.capacity))
      case OS.MacOS | OS.Linux => LibC.INSTANCE.munlock(pointer, NativeLong(byteBuffer.capacity))
