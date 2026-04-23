package torrent.listener

import com.frostwire.jlibtorrent.alerts.*
import com.frostwire.jlibtorrent.{TorrentFlags, TorrentStatus}
import scalafx.Includes.{jfxFloatProperty2sfx, jfxIntegerProperty2sfx, jfxLongProperty2sfx, jfxObjectProperty2sfx}
import scalafx.application.Platform
import torrent.*
import torrent.Hash.hash

import scala.jdk.CollectionConverters.given


private[listener] class MainListener extends TorrentListener:

  listen[AddTorrentAlert]: event =>
    if (event.error.check)
      val hash = event.handle.hash
      Platform.runLater:
        Torrent.all += Torrent(event.handle.hash)


  listen[TorrentRemovedAlert]: event =>
    val hash = event.hash
    Platform.runLater:
      Torrent.all --= Torrent.find(hash)


  listen[MetadataFailedAlert]: event =>
    event.getError.check
    Torrent.session.remove(event.handle)

  listen[MetadataReceivedAlert]: event =>
    val handle = event.handle
    val hash = handle.hash
    handle.setFlags(TorrentFlags.UPLOAD_MODE, TorrentFlags.AUTO_MANAGED or_ TorrentFlags.UPLOAD_MODE)
    Platform.runLater:
      Torrent.find(hash).foreach(_.metadataUpdate())
    //todo save torrentFile


  listen[StateChangedAlert]: event =>
    val hash = event.handle.hash
    val state = event.getState
    Platform.runLater:
      for torrent <- Torrent.find(hash) do
        torrent.state.value = torrent.state.value.copy(value = state)
    if (event.getPrevState == TorrentStatus.State.DOWNLOADING_METADATA)
      event.handle.setFlags(TorrentFlags.STOP_WHEN_READY, TorrentFlags.STOP_WHEN_READY or_ TorrentFlags.UPLOAD_MODE)

  listen[TorrentPausedAlert]: event =>
    val hash = event.handle.hash
    Platform.runLater:
      for torrent <- Torrent.find(hash) do
        torrent.state.value = torrent.state.value.copy(paused = true)

  listen[TorrentResumedAlert]: event =>
    val hash = event.handle.hash
    Platform.runLater:
      for torrent <- Torrent.find(hash) do
        torrent.state.value = torrent.state.value.copy(paused = false)


  listen[StateUpdateAlert]: event =>
    val statuses = for status <- event.status.asScala.toList yield Status(
      status.hash,
      status.downloadPayloadRate,
      status.uploadPayloadRate,
      status.progress,
    )
    Platform.runLater:
      for
        status <- statuses
        torrent <- Torrent.find(status.hash)
      do
        torrent.downSpeed.value = status.downSpeed
        torrent.upSpeed.value = status.upSpeed
        torrent.progress.value = status.progress
        for
          node <- Option(Torrent.selected.value).filter(_.torrent == torrent).flatMap(_.node)
          (file, progress) <- node.files zip torrent.handle.fileProgress
        do
          file.progress.value = progress


private case class Status(hash: Hash, downSpeed: Int, upSpeed: Int, progress: Float)
