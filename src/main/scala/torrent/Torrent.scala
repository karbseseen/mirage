package torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.swig.*
import constant.{Constants, Tr}
import core.main.MainApp
import javafx.beans.property.*
import scalafx.Includes.{jfxLongProperty2sfx, jfxObjectProperty2sfx}
import scalafx.application.Platform
import scalafx.beans.property.PropertyIncludes.jfxStringProperty2sfx
import scalafx.collections.ObservableBuffer
import torrent.Hash.hash
import torrent.listener.TorrentListener
import torrent.view.TorrentView
import util.{JavaUtil, also}

import java.io.File
import java.nio.file.{Files, Path}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable
import scala.util.{Random, Try}


private object Torrent:

  val session = new SessionManager
  TorrentListener.all.foreach(session.addListener)
  session.start()

  private class Known(val needSave: Boolean)
  private val knownTorrents = mutable.Map.empty[Hash, Known]

  val directory = File(JavaUtil.jarFile.getParentFile, "torrent")
  directory.mkdir()
  knownTorrents ++= directory.list.toList
    .collect:
      case s"$name.torrent" => name
    .distinct
    .flatMap: name =>
      val torrentFile = File(directory, s"$name.torrent")
      val resumeFile = File(directory, s"$name.resume")
      if (!torrentFile.isFile || !resumeFile.isFile) None
      else Try:
        val info = TorrentInfo(torrentFile)
        session.download(info, null, resumeFile, null, null, new torrent_flags_t)
        info.hash
      .toOption
    .map(_ -> Known(needSave = false))

  val exitResumeDone = CountDownLatch(1)
  MainApp.shutdownLongHook:
    session.pause()
    session.getTorrentHandles.foreach(_.saveResumeData())
    exitResumeDone.await(2, TimeUnit.SECONDS)
    session.stop()


  def add(torrentFile: Path, saveDir: File): Unit =
    val torrentFileData =
      try Files.readAllBytes(torrentFile)
      catch case error: Throwable => throw error.also(MainApp.showError)
    val info = TorrentInfo(torrentFileData)
    session.download(info, saveDir, null, null, null, TorrentFlags.STOP_WHEN_READY)

  def add(magnet: String, saveDir: File): Unit =
    session.download(magnet, saveDir, TorrentFlags.UPLOAD_MODE)

  def create(file: File): Unit =
    Thread: () =>
      try createInner(file)
      catch case error: Throwable => MainApp.showError(error)
    .start()

  private def createInner(file: File): Unit =
    val create = create_torrent:
      file_storage().also:
        libtorrent.add_files(_, file.getAbsolutePath)
    val error = new error_code
    libtorrent.set_piece_hashes_ex(create, file.getParent, new set_piece_hashes_listener, error)
    if (error.failed) {
      println("error.failed")
      throw Exception(error.message)
    }

    val info = TorrentInfo.bdecode(Vectors.byte_vector2bytes(create.generate.bencode))
    if (!info.isValid || info.numFiles != 1) throw Exception(Tr.addFileError.getValue)

    knownTorrents.synchronized { knownTorrents(info.hash) = Known(needSave = true) }
    session.download(info, file.getParentFile, null, null, null, TorrentFlags.SEED_MODE)


  val all: ObservableBuffer[Torrent] = ObservableBuffer.empty



private class Torrent(val hash: Hash):

  val handle: TorrentHandle = Torrent.session.find(hash)

  /**Edit only in alert thread*/
  var nextResumeSaveTime: Long = Long.MaxValue

  private val known = Torrent.knownTorrents.synchronized { Torrent.knownTorrents.remove(hash) }
  private val initState = State(
    value = handle.status(new status_flags_t).state,
    paused = handle.isPaused,
    isNew = known.isEmpty,
  )

  val state     = SimpleObjectProperty(this, "state", initState)
  val name      = SimpleStringProperty(this, "name")
  val progress  = SimpleFloatProperty(this, "progress")
  val size      = SimpleLongProperty(this, "size")
  val downSpeed = SimpleIntegerProperty(this, "downSpeed")
  val upSpeed   = SimpleIntegerProperty(this, "upSpeed")
  val peerNum   = SimpleIntegerProperty(this, "peerNum")

  def metadataUpdate(): Unit = metadataUpdateInner(init = false)
  private def metadataUpdateInner(init: Boolean): Unit =
    def ui(): Unit =
      name.value = Option(handle.name).filter(_.nonEmpty).getOrElse(hash.toString)
      for info <- Option(handle.torrentFile) do
        size.value = info.totalSize
        if (TorrentView.selected.exists(_.torrent == this)) TorrentView.selectedExpr.invalidate()
    if (init) ui() else Platform.runLater(ui())

    for info <- Option(handle.torrentFile) do
      if (state().isNew)
        handle.prioritizeFiles { Array.tabulate(info.numFiles)(_ => Priority.IGNORE) }
        nextResumeSaveTime = System.currentTimeMillis
        save(info)
      else
        nextResumeSaveTime = System.currentTimeMillis + Random.nextLong(Constants.resumeSavePeriod)
  private def save(info: TorrentInfo): Unit =
    try Files.write(Torrent.directory.toPath.resolve(s"${info.hash}.torrent"), info.bencode)
    catch case error: Throwable => MainApp.showError(error)
  metadataUpdateInner(init = true)
  known.filter(_.needSave).flatMap(_ => Option(handle.torrentFile)).foreach(save)
