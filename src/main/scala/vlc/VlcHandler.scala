package vlc

import scalafx.Includes.jfxProperty2sfx
import scalafx.application.Platform.runLater
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter}


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

  override def elementaryStreamAdded(mediaPlayer: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.addItem(id)) }

  override def elementaryStreamSelected(mediaPlayer: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.selectedId() = id) }

  override def elementaryStreamDeleted(mediaPlayer: MediaPlayer, trackType: TrackType, id: Int): Unit =
    runLater { tracksControl(trackType).foreach(_.removeItem(id)) }


  private var isBuffering = false

  private def tracksControl(trackType: TrackType) =
    Some(trackType).collect:
      case TrackType.VIDEO => stage.controls.video
      case TrackType.AUDIO => stage.controls.audio
      case TrackType.TEXT => stage.controls.title
