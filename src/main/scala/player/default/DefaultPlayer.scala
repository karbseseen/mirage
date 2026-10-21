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
import player.{Player, PlayerSync, Media}
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx}
import scalafx.application.Platform.runLater
import scalafx.scene.Node
import scalafx.scene.input.KeyEvent
import scalafx.stage.StageIncludes.jfxStage2sfx
import uk.co.caprica.vlcj.media.{Meta, TrackType}
import uk.co.caprica.vlcj.player.base.{Marquee, MediaPlayer}
import util.also
import util.fx.{ensureRunLater, registerEventHandler}


class DefaultPlayer(media: Media) extends Player(media) with PlayerSync:
  private implicit inline def self: this.type = this

  protected type HandlerType = DefaultHandler
  protected def createHandler: DefaultHandler = new DefaultHandler
  def playing: ReadOnlyBooleanProperty = vlcHandler.playing

  private val _defaultTitle = SimpleStringProperty(this, "defaultTitle", media.name)
  private val _customTitle = SimpleStringProperty(this, "customTitle", "")
  def customTitle: ReadOnlyStringProperty = _customTitle
  val title: StringBinding = new StringBinding:
    def computeValue: String = if (_customTitle().isBlank) _defaultTitle() else _customTitle()
    bind(_defaultTitle, _customTitle)
  stage.addListener: (_, oldStage, newStage) =>
    Option(oldStage).foreach(_.title.unbind())
    Option(newStage).foreach(_.title <== title)

  val phone: Phone = new Phone

  override def close(): Unit =
    PlayerSavedState.save()
    phone.close()
    super[Player].close()
    super[PlayerSync].close()

  scene.registerEventHandler(KeyEvent.KeyPressed)(new KeyHandler)

  private val loading: Loading = new Loading
  private val controls: Controls = new Controls
  override def views: List[Node] = super.views ::: loading :: controls :: controls.extraParts


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


  protected class DefaultHandler extends Handler:

    def playing: ReadOnlyBooleanProperty = _playing
    private val _playing = SimpleBooleanProperty(this, "playing", false)

    override def mediaPlayerReady(player: MediaPlayer): Unit =
      super.mediaPlayerReady(player)
      runLater:
        player.marquee.enable(true)
        Option(player.media.meta.get(Meta.TITLE))
          .filter(title => !title.isBlank && title != "imem://")
          .foreach(_defaultTitle() = _)
        PlayerSavedState.restore()

    override def lengthChanged(player: MediaPlayer, length: Long): Unit =
      super.lengthChanged(player, length)
      runLater { controls.setMaxTime(length) }

    override def timeChanged(player: MediaPlayer, time: Long): Unit =
      super.timeChanged(player, time)
      onTime(time)
      runLater { controls.setTime(time) }

    override def buffering(mediaPlayer: MediaPlayer, progress: Float): Unit =
      super.buffering(mediaPlayer, progress)
      onBuffering(progress < 100)

    override def playing(player: MediaPlayer): Unit =
      super.playing(player)
      onPause(false)
      runLater { _playing() = true }

    override def paused(player: MediaPlayer): Unit =
      super.paused(player)
      onPause(true)
      runLater { _playing() = false }

    override def stopped(player: MediaPlayer): Unit =
      super.stopped(player)
      onBuffering(false)
      runLater { _playing() = false }

    override def finished(player: MediaPlayer): Unit =
      super.finished(player)
      onBuffering(false)
      runLater { _playing() = false }

    override def elementaryStreamAdded(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      super.elementaryStreamAdded(player, trackType, id)
      runLater { controls.addTrack(id, trackType) }

    override def elementaryStreamDeleted(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      super.elementaryStreamDeleted(player, trackType, id)
      runLater { controls.removeTrack(id, trackType) }

    override def elementaryStreamSelected(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
      super.elementaryStreamSelected(player, trackType, id)
      runLater { controls.onTrackSelect(id, trackType) }


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
