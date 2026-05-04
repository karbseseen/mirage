package torrent.listener

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.*
import com.frostwire.jlibtorrent.swig.libtorrent_errors
import constant.Constants
import core.main.MainApp
import scalafx.Includes.{jfxFloatProperty2sfx, jfxIntegerProperty2sfx, jfxLongProperty2sfx, jfxObjectProperty2sfx}
import scalafx.application.Platform
import torrent.*
import torrent.Hash.hash
import torrent.view.TorrentView

import java.io.File
import java.nio.file.Files
import scala.collection.mutable
import scala.jdk.CollectionConverters.given


private[listener] class MainListener extends TorrentListener:

  listen[AddTorrentAlert]: event =>
    if (event.error.check)
      val torrent = Torrent(event.handle.hash)
      map(torrent.hash) = torrent
      Platform.runLater:
        Torrent.all += torrent

  listen[TorrentRemovedAlert]: event =>
    val hash = event.hash
    forTorrentUi(hash)(Torrent.all -= _)
    for fileName <- List(s"$hash.torrent", s"$hash.resume") do
      File(Torrent.directory, fileName).delete()


  listen[MetadataFailedAlert]: event =>
    event.getError.check
    Torrent.session.remove(event.handle)

  listen[MetadataReceivedAlert]: event =>
    val handle = event.handle
    handle.setFlags(TorrentFlags.STOP_WHEN_READY, TorrentFlags.AUTO_MANAGED or_ TorrentFlags.STOP_WHEN_READY)
    map.get(handle.hash).foreach(_.metadataUpdate())


  listen[StateChangedAlert]: event =>
    val handle = event.handle
    val state = event.getState
    forTorrentUi(handle.hash): torrent =>
      torrent.state() = torrent.state().copy(value = state)

  listen[TorrentPausedAlert]: event =>
    forTorrentUi(event.handle.hash): torrent =>
      torrent.state() = torrent.state().copy(paused = true)

  listen[TorrentResumedAlert]: event =>
    forTorrentUi(event.handle.hash): torrent =>
      torrent.state() = torrent.state().copy(paused = false)


  listen[StateUpdateAlert]: event =>
    val now = event.timestamp
    val statuses = for
      status <- event.status.asScala.toList
      torrent <- map.get(status.hash)
    yield
      if (torrent.nextResumeSaveTime < now)
        torrent.handle.saveResumeData(TorrentHandle.ONLY_IF_MODIFIED)
      Status(
        torrent,
        status.downloadPayloadRate,
        status.uploadPayloadRate,
        status.progress,
        status.numPieces,
        status.numPeers,
      )

    Platform.runLater:
      for status <- statuses do
        val torrent = status.torrent
        torrent.downSpeed() = status.downSpeed
        torrent.upSpeed() = status.upSpeed
        torrent.progress() = status.progress
        torrent.peerNum() = status.peerNum
        for root <- TorrentView.selected.filter(_.torrent == torrent).flatMap(_.node) do
          root.updateProgresses(status.pieceNum)

  listen[FilePrioAlert]: event =>
    if (event.error.check)
      val handle = event.handle
      val filePriorities = handle.filePriorities

      for root <- TorrentView.selected.filter(_.torrent.hash == handle.hash).flatMap(_.node) do
        Platform.runLater:
          root.setPriorities(filePriorities)

      val files = handle.torrentFile.files
      val pieceLength = handle.torrentFile.pieceLength.toLong
      def byteToPieceDown(byteIndex: Long) = (byteIndex / pieceLength).toInt
      def byteToPieceUp(byteIndex: Long) = ((byteIndex - 1) / pieceLength + 1).toInt
      filePriorities.zipWithIndex.foldLeft(0L) { case (beginByte, (filePriority, fileIndex)) =>
        val endByte = beginByte + files.fileSize(fileIndex)
        if (filePriority == Priority.NORMAL)
          val beginFromPiece = byteToPieceDown(beginByte)
          val endToPiece = byteToPieceUp(endByte)
          val beginToPiece = byteToPieceUp(beginByte + Constants.prioritizeFirstBytes) min endToPiece
          val endFromPiece = byteToPieceDown(endByte - Constants.prioritizeLastBytes) max beginToPiece
          (beginFromPiece until beginToPiece).foreach(handle.piecePriority(_, Priority.SIX))
          (endFromPiece until endToPiece).foreach(handle.piecePriority(_, Priority.SIX))
        endByte
      }

  listen[PieceFinishedAlert]: event =>
    val handle = event.handle
    for root <- TorrentView.selected.filter(_.torrent.hash == handle.hash).flatMap(_.node) do
      root.pieceNum += 1
      val slices = handle.torrentFile.mapBlock(event.pieceIndex, 0, handle.torrentFile.pieceSize(event.pieceIndex))
      Platform.runLater:
        slices.forEach: slice =>
          val doneBytes = root.files(slice.fileIndex).doneBytes
          doneBytes() = doneBytes() + slice.size


  listen[SaveResumeDataAlert]: event =>
    val success = try
      val path = Torrent.directory.toPath.resolve(s"${event.handle.hash}.resume")
      val data = AddTorrentParams.writeResumeData(event.params).bencode
      Files.write(path, data)
      true
    catch case error: Throwable =>
      MainApp.showError(error)
      false
    afterResumeSave(event, success)

  listen[SaveResumeDataFailedAlert]: event =>
    val error = event.error
    val success = error.value == libtorrent_errors.resume_data_not_modified.swigValue
    if (!success) error.check
    afterResumeSave(event, success)


  private case class Status(torrent: Torrent, downSpeed: Int, upSpeed: Int, progress: Float, pieceNum: Int, peerNum: Int)

  private val map = mutable.Map.empty[Hash, Torrent]
  private def forTorrentUi(hash: Hash)(func: Torrent => Unit): Unit =
    for torrent <- map.get(hash) do
      Platform.runLater:
        func(torrent)

  private def afterResumeSave(event: TorrentAlert[?], success: Boolean): Unit =
    val hash = event.handle.hash
    if (!success) File(Torrent.directory, s"$hash.resume").delete()
    if (MainApp.isExiting)
      map -= hash
      if (map.isEmpty) Torrent.exitResumeDone.countDown()
    else
      val untilNext = if (success) Constants.resumeSavePeriod else Constants.resumeRetryTime
      map.get(hash).foreach(_.nextResumeSaveTime = event.timestamp + untilNext)
