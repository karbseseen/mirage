package vlc

import javafx.beans.property.SimpleObjectProperty
import javafx.stage.WindowEvent
import org.kordamp.ikonli.fluentui.FluentUiFilledMZ
import p2p.RoomSync
import p2p.base.P2p
import scalafx.Includes.{jfxProperty2sfx, jfxScene2sfx, observableList2ObservableBuffer}
import scalafx.application.Platform.runLater
import scalafx.scene.Node
import uk.co.caprica.vlcj.media.{Meta, TrackType, VideoTrackInfo}
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter}
import util.{WakeLock, also}
import vlc.VlcStage.MinTimeSyncPeriod
import vlc.loading.Impl

import scala.jdk.CollectionConverters.*


private class VlcHandler(stage: VlcStage) extends MediaPlayerEventAdapter:

  private var length = 0L
  private var lastTimeSync = 0L
  private var microphoneWasEnabled = false
  private val wakeLock = new WakeLock
  private var loading: Option[Node] = None

  val videoTrack = SimpleObjectProperty(this, "videoTrack", null: VideoTrackInfo)
  @volatile private var _isBuffering = false
  def isBuffering: Boolean = _isBuffering


  stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ => wakeLock.unlock())


  override def lengthChanged(player: MediaPlayer, length: Long): Unit =
    runLater:
      this.length = length
      stage.controls.seek.max = length

  override def timeChanged(player: MediaPlayer, time: Long): Unit =
    val bufferTask = unsetBufferingUi
    runLater:
      stage.controls.seek.value = time
      bufferTask match
        case Some(task) =>
          lastTimeSync = time
          task()
        case None =>
          val syncTimeDiff = time - lastTimeSync
          if (syncTimeDiff < 0 || syncTimeDiff > MinTimeSyncPeriod)
            lastTimeSync = time
            stage.updateState(pause = Some(false), time = time, send = true)

  override def buffering(player: MediaPlayer, progress: Float): Unit =
    if (progress < 100)
      val task = setBufferingUi(progress)
      runLater(task())
    else
      unsetBufferingUi.foreach(task => runLater(task()))

  override def mediaPlayerReady(player: MediaPlayer): Unit =
    runLater:
      Option(player.media.meta.get(Meta.TITLE))
        .filter(title => !title.isBlank && title != "imem://")
        .foreach(stage.title = _)
      player.marquee.enable(true)
      val speedX10 = (player.status.rate * 10).toByte
      if (speedX10 != 10) stage.showNewSpeedText(speedX10)

  override def playing(player: MediaPlayer): Unit =
    wakeLock.lock()
    runLater:
      stage.controls.playPauseIcon.setIconCode(FluentUiFilledMZ.PAUSE_48)

      stage.controls.microphone.disable() = true
      microphoneWasEnabled = stage.phone.microphoneEnabled()
      stage.phone.microphoneEnabled() = false

      stage.updateState(pause = Some(false), send = true)

  override def paused(player: MediaPlayer): Unit =
    wakeLock.unlock()
    runLater:
      stage.controls.playPauseIcon.setIconCode(FluentUiFilledMZ.PLAY_48)

      stage.controls.microphone.disable() = false
      if (microphoneWasEnabled)
        microphoneWasEnabled = false
        stage.phone.microphoneEnabled() = true

      stage.updateState(pause = Some(true), send = true)
      stage.startPauseTask: thisTask =>
        val state = RoomSync.mergedState.get
        if (state.pause) P2p.sendToAll(state)
        else thisTask.cancel()

  override def stopped(player: MediaPlayer): Unit =
    wakeLock.unlock()

  override def elementaryStreamAdded(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.addItem(id)) }

  override def elementaryStreamDeleted(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.removeItem(id)) }

  override def elementaryStreamSelected(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater:
      tracksControl(trackType).foreach(_.selectedId() = id)
      if (trackType == TrackType.VIDEO)
        for video <- player.media.info.videoTracks.asScala.find(_.id == id)
          do videoTrack() = video


  private def tracksControl(trackType: TrackType) =
    Some(trackType).collect:
      case TrackType.VIDEO => stage.controls.video
      case TrackType.AUDIO => stage.controls.audio
      case TrackType.TEXT => stage.controls.title

  private def setBufferingUi(progress: Float): () => Unit =
    val bufferStart = !isBuffering
    if (bufferStart) _isBuffering = true
    () =>
      if (bufferStart)
        stage.loading.visible = true
        stage.updateState(pause = Some(true), send = true)
        stage.startPauseTask: thisTask =>
          if (isBuffering)
            val state = RoomSync.mergedState.updateAndGet: state =>
              if (state.pause) state else state.copy(pauseCounter = state.pauseCounter.inc)
            P2p.sendToAll(state)
          else thisTask.cancel()

  private def unsetBufferingUi: Option[() => Unit] =
    Option.when(isBuffering):
      _isBuffering = false
      () =>
        stage.loading.visible = false
        if (stage.player.status.isPlaying)
          stage.updateState(pause = Some(false), send = true)
