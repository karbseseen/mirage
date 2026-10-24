package player.default

import constant.Tr
import javafx.beans.binding.StringBinding
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.scene.input as jfxsi
import javafx.scene.input.KeyCode
import p2p.phone.Phone
import player.PlayerSync.Pause.Toggle
import player.PlayerSync.SetFlag
import player.default.control.Controls
import player.loading.Loading
import player.{Media, Player, PlayerSync}
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx}
import scalafx.application.Platform.runLater
import scalafx.scene.Node
import scalafx.scene.input.KeyEvent
import uk.co.caprica.vlcj.media.{Meta, TrackType}
import uk.co.caprica.vlcj.player.base.{Marquee, MediaPlayer, MediaPlayerEventAdapter}
import util.also
import util.fx.JfxEvents.registerEventHandler
import util.fx.SfxSuperBind.<~=
import util.fx.ensureRunLater


class DefaultPlayer(protected val media: Media) extends Player with PlayerSync:
  private implicit inline def self: this.type = this
  def fileId: Media.FileId = media.fileId

  def playing: ReadOnlyBooleanProperty = _playing
  private val _playing = SimpleBooleanProperty(this, "playing", false)

  private val _defaultTitle = SimpleStringProperty(this, "defaultTitle", media.name)
  private val _customTitle = SimpleStringProperty(this, "customTitle", "")
  def customTitle: ReadOnlyStringProperty = _customTitle
  val title: StringBinding = new StringBinding:
    def computeValue: String = if (_customTitle().isBlank) _defaultTitle() else _customTitle()
    bind(_defaultTitle, _customTitle)
  stage.map(_.title) <~= title

  def buffering: ReadOnlyBooleanProperty = loading.visible

  val phone: Phone = new Phone

  private val loading: Loading = new Loading
  private val controls: Controls = new Controls
  override def views: List[Node] = super.views ::: loading :: controls :: controls.extraParts

  override def start(): Unit =
    super.start()
    startSync()

  override protected def close(): Unit =
    PlayerSavedState.save()
    phone.close()
    super[PlayerSync].close()
    super[Player].close()


  override def setPause(pause: PlayerSync.Pause, flag: SetFlag): Boolean =
    if (restartIfFinished()) false
    else super.setPause(pause, flag)

  override def setSpeedX10(speedX10: Int, flag: SetFlag): Boolean =
    super.setSpeedX10(speedX10, flag).also:
      if (_) ensureRunLater:
        val speed = speedX10 / 10f
        controls.setSpeed(speed)
        if (!controls.isSpeedShown) vlc.marquee.set:
          Marquee.marquee
            .text(s"${Tr.speed.get} = $speed")
            .location(50, 50)
            .timeout(2500)

  override def setCustomTitle(title: String, flag: SetFlag): Unit =
    ensureRunLater { _customTitle() = title }
    super.setCustomTitle(title, flag)

  override protected def onBuffering(buffering: Boolean): Boolean =
    super.onBuffering(buffering).also:
      if (_) runLater(loading.visible = buffering)


  this addHandler new MediaPlayerEventAdapter:
    override def mediaPlayerReady(player: MediaPlayer): Unit =
      runLater:
        player.marquee.enable(true)
        Option(player.media.meta.get(Meta.TITLE))
          .filter(title => !title.isBlank && title != "imem://")
          .foreach(_defaultTitle() = _)
        PlayerSavedState.restore()

    override def lengthChanged(player: MediaPlayer, length: Long): Unit =
      runLater { controls.setMaxTime(length) }

    override def timeChanged(player: MediaPlayer, time: Long): Unit =
      onTime(time)
      runLater { controls.setTime(time) }

    override def buffering(mediaPlayer: MediaPlayer, progress: Float): Unit =
      onBuffering(progress < 100)

    override def playing(player: MediaPlayer): Unit =
      onPause(false)
      runLater { _playing() = true }

    override def paused(player: MediaPlayer): Unit =
      onPause(true)
      runLater { _playing() = false }

    override def stopped(player: MediaPlayer): Unit =
      onBuffering(false)
      runLater { _playing() = false }

    override def finished(player: MediaPlayer): Unit =
      onBuffering(false)
      runLater { _playing() = false }

    override def elementaryStreamAdded(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      runLater { controls.addTrack(id, trackType) }

    override def elementaryStreamDeleted(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      runLater { controls.removeTrack(id, trackType) }

    override def elementaryStreamSelected(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      runLater { controls.onTrackSelect(id, trackType) }


  scene.registerEventHandler(KeyEvent.KeyPressed)(new KeyHandler)
  private class KeyHandler extends EventHandler[jfxsi.KeyEvent]:

    def handle(event: jfxsi.KeyEvent): Unit =
      Some(event.getCode).collect:
        case KeyCode.SPACE          => setPause(Toggle)
        case KeyCode.OPEN_BRACKET   => setSpeedX10((speedX10 - 1) max 3)
        case KeyCode.CLOSE_BRACKET  => setSpeedX10((speedX10 + 1) min 40)
        case KeyCode.LEFT           => seek(-10_000, -1, event.isShiftDown)
        case KeyCode.RIGHT          => seek( 10_000,  1, event.isShiftDown)

    private def seek(timeOffset: Long, frameOffset: Long, isFrame: Boolean): Unit =
      if (isFrame) setPause(true)
      val newTime = Option(videoTrackInfo()) match
        case Some(video) if isFrame =>
          val frame = (time * video.frameRate) roundDiv (video.frameRateBase * 1000)
          val newFrame = frame + frameOffset
          (newFrame * 1000 * video.frameRateBase) roundDiv video.frameRate
        case _ =>
          time + timeOffset
      setTime(newTime)

    extension (long: Long)
      private infix def roundDiv(other: Long) =
        if ((long >> 63) == (other >> 63))
          (long + (other / 2)) / other
        else
          (long - (other / 2)) / other


object DefaultPlayer:
  class Preloaded(media: Media) extends DefaultPlayer(media):
    vlc.media.play(media, "start-paused")
    override def start(): Unit =
      setPause(false)
      startSync()
