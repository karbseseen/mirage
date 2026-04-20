package torrent.listener

import com.frostwire.jlibtorrent.alerts.*
import scalafx.Includes.{jfxFloatProperty2sfx, jfxIntegerProperty2sfx, jfxObjectProperty2sfx}
import scalafx.application.Platform
import torrent.State
import torrent.TorrentNode.{getTorrent, roots}

import scala.jdk.CollectionConverters.given


private[listener] class MainListener extends TorrentListener:

  listen[TorrentRemovedAlert]: event =>
    val hash = event.infoHash.toHex
    Platform.runLater:
      roots --= getTorrent(hash).filter(_.files.nonEmpty)


  listen[StateChangedAlert]: event =>
    val hash = event.handle.infoHash.toHex
    val state = State(event.getState)
    Platform.runLater:
      for torrent <- getTorrent(hash) do
        torrent.state.value = torrent.state.value match
          case _: State.Resumed => state
          case _: State.Paused => State.Paused(state)

  listen[TorrentPausedAlert]: event =>
    val hash = event.handle.infoHash.toHex
    Platform.runLater:
      for torrent <- getTorrent(hash) do
        Some(torrent.state.value).collect:
          case resumed: State.Resumed => torrent.state.value = State.Paused(resumed)

  listen[TorrentResumedAlert]: event =>
    val hash = event.handle.infoHash.toHex
    Platform.runLater:
      for torrent <- getTorrent(hash) do
        Some(torrent.state.value).collect:
          case paused: State.Paused => torrent.state.value = paused.resumed


  listen[StateUpdateAlert]: event =>
    val statuses = for status <- event.status.asScala.toList yield Status(
      status.infoHash.toHex,
      status.downloadPayloadRate,
      status.uploadPayloadRate,
      status.progress,
    )
    Platform.runLater:
      for
        status <- statuses
        torrent <- getTorrent(status.hash)
      do
        torrent.downSpeed.value = status.downSpeed
        torrent.upSpeed.value = status.upSpeed
        torrent.progress.value = status.progress


private case class Status(hash: String, downSpeed: Int, upSpeed: Int, progress: Float)
