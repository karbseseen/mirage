package vlc

import scalafx.Includes.jfxProperty2sfx
import scalafx.application.Platform.runLater
import uk.co.caprica.vlcj.media.{Meta, TrackType}
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter}

import scala.jdk.CollectionConverters.*


private class VlcHandler(stage: VlcStage) extends MediaPlayerEventAdapter:

  override def lengthChanged(player: MediaPlayer, length: Long): Unit =
    runLater:
      stage.controls.seek.max = length

  override def timeChanged(player: MediaPlayer, time: Long): Unit =
    val bufferEnd = isBuffering
    if (bufferEnd) isBuffering = false
    runLater:
      stage.controls.seek.value = time
      if (bufferEnd)
        stage.loading.managed = false
        stage.loading.visible = false

  override def buffering(player: MediaPlayer, progress: Float): Unit =
    if (!isBuffering)
      isBuffering = true
      runLater:
        stage.loading.managed = true
        stage.loading.visible = true

  override def mediaPlayerReady(player: MediaPlayer): Unit =
    runLater:
      Option(player.media.meta.get(Meta.TITLE))
        .filter(title => !title.isBlank && title != "imem://")
        .foreach(stage.title = _)

  override def playing(player: MediaPlayer):  Unit = stage.wakeLock.lock()
  override def paused(player: MediaPlayer):   Unit = stage.wakeLock.unlock()
  override def stopped(player: MediaPlayer):  Unit = stage.wakeLock.unlock()
  override def finished(player: MediaPlayer): Unit = stage.wakeLock.unlock()

  override def elementaryStreamAdded(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.addItem(id)) }

  override def elementaryStreamDeleted(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.removeItem(id)) }

  override def elementaryStreamSelected(player: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater:
      tracksControl(trackType).foreach(_.selectedId() = id)
      if (trackType == TrackType.VIDEO)
        for video <- player.media.info.videoTracks.asScala.find(_.id == id)
          do stage.videoSize() = (video.width, video.height)


  private var isBuffering = false

  private def tracksControl(trackType: TrackType) =
    Some(trackType).collect:
      case TrackType.VIDEO => stage.controls.video
      case TrackType.AUDIO => stage.controls.audio
      case TrackType.TEXT => stage.controls.title
