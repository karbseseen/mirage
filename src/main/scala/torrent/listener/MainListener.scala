package torrent.listener

import com.frostwire.jlibtorrent.alerts.*
import com.frostwire.jlibtorrent.swig.status_flags_t
import com.frostwire.jlibtorrent.{Priority, TorrentFlags, TorrentStatus}
import scalafx.Includes.{jfxFloatProperty2sfx, jfxIntegerProperty2sfx, jfxObjectProperty2sfx, jfxStringProperty2sfx}
import scalafx.application.Platform
import torrent.Hash.hash
import torrent.Torrent.FileInfo
import torrent.*

import scala.jdk.CollectionConverters.given


private[listener] class MainListener extends TorrentListener:

  listen[AddTorrentAlert]: event =>
    if (event.error.check)
      val handle = event.handle
      val hash = handle.hash
      val name = Option(event.torrentName).filter(_.nonEmpty).getOrElse(hash.toString)
      val state = State(
        value = handle.status(new status_flags_t).state,
        paused = handle.isPaused,
        isNew = !Torrent.loadingResume,
      )

      val torrent = Torrent(hash, name, state)
      for info <- Option(event.params.torrentInfo) do
        torrent.files = FileInfo(info)
        if (state.isNew)
          handle.prioritizeFiles { Array.tabulate(info.numFiles)(_ => Priority.IGNORE) }

      Platform.runLater:
        Torrent.all += torrent


  listen[TorrentRemovedAlert]: event =>
    val hash = event.hash
    Platform.runLater:
      Torrent.all --= Torrent.find(hash)


  listen[MetadataFailedAlert]: event =>
    if (!event.getError.check)
      Torrent.session.remove(event.handle)

  listen[MetadataReceivedAlert]: event =>
    val handle = event.handle
    val hash = handle.hash
    val name = handle.name
    val info = handle.torrentFile
    val files = FileInfo(info)

    Platform.runLater:
      for torrent <- Torrent.find(hash) do
        torrent.name.value = name
        torrent.files = files

    handle.prioritizeFiles { Array.tabulate(info.numFiles)(_ => Priority.IGNORE) }
    handle.setFlags(TorrentFlags.UPLOAD_MODE, TorrentFlags.AUTO_MANAGED or_ TorrentFlags.UPLOAD_MODE)

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


private case class Status(hash: Hash, downSpeed: Int, upSpeed: Int, progress: Float)
