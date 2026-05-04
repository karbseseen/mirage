package vlc

import scalafx.application.Platform.runLater
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter}


private class VlcHandler(stage: VlcStage) extends MediaPlayerEventAdapter:

  private var isBuffering = false


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
