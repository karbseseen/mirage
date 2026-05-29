package torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.swig.*
import config.Conf
import constant.{Constants, Tr}
import core.main.MainApp
import javafx.beans.property.*
import scalafx.Includes.jfxLongProperty2sfx
import scalafx.application.Platform
import scalafx.beans.property.PropertyIncludes.jfxStringProperty2sfx
import scalafx.collections.ObservableBuffer
import torrent.Hash.hash
import torrent.Torrent.Known
import torrent.listener.TorrentListener
import torrent.view.TorrentView
import util.{JavaUtil, also}

import java.io.File
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import scala.collection.mutable
import scala.util.{Failure, Random, Success, Try}


object Torrent:

  val session = new SessionManager
  TorrentListener.all.foreach(session.addListener)
  session.start()
  Conf.torrentLocalDiscovery.subscribe: enable =>
    session.applySettings(session.settings.also(_.setEnableLsd(enable)))
  Conf.torrentDhtDiscovery.subscribe:
    if (_) session.startDht() else session.stopDht()

  val directory = File("torrent")
  directory.mkdir()

  private val restored = directory.list.toList
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
        val known: Known = (torrent, _) =>
          torrent.nextResumeSaveTime = System.currentTimeMillis + Random.nextLong(Constants.resumeSavePeriod)
        info.hash -> known
      .toOption


  trait Known:
    def onMetadata(torrent: Torrent, info: TorrentInfo): Unit
  object Known:
    val default: Known = (torrent, info) =>
      torrent.handle.prioritizeFiles { Array.fill(info.numFiles)(Priority.IGNORE) }
      torrent.nextResumeSaveTime = System.currentTimeMillis
      Torrent.save(info)
  val knownTorrents = AtomicReference(restored.toMap)


  private[torrent] val exitResumeDone = CountDownLatch(1)
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

    create.add_tracker("udp://tracker.ducks.party:1984/announce")
    create.add_tracker("udp://ipv4announce.sktorrent.eu:6969/announce")
    create.add_tracker("udp://tracker.torrent.eu.org:451/announce")
    create.add_tracker("udp://open.demonii.com:1337/announce")
    create.add_tracker("udp://seedpeer.net:6969/announce")
    create.add_tracker("udp://tracker.bluefrog.pw:2710/announce")
    create.add_tracker("udp://torrentclub.online:54123/announce")

    val error = new error_code
    libtorrent.set_piece_hashes_ex(create, file.getParent, new set_piece_hashes_listener, error)
    if (error.failed) throw Exception(error.message)

    val info = TorrentInfo.bdecode(Vectors.byte_vector2bytes(create.generate.bencode))
    if (!info.isValid || info.numFiles != 1) throw Exception(Tr.addFileError.getValue)

    val known: Known = (torrent, info) =>
      save(info)
      torrent.nextResumeSaveTime = System.currentTimeMillis
    knownTorrents.updateAndGet(_ + (info.hash -> known))
    session.download(info, file.getParentFile, null, null, null, TorrentFlags.SEED_MODE)


  private def save(info: TorrentInfo): Unit =
    try Files.write(directory.toPath.resolve(s"${info.hash}.torrent"), info.bencode)
    catch case error: Throwable => MainApp.showError(error)


  val all: ObservableBuffer[Torrent] = ObservableBuffer.empty



private class Torrent(val hash: Hash):

  val handle: TorrentHandle = Torrent.session.find(hash)

  /**Edit only in alert thread*/
  var nextResumeSaveTime: Long = Long.MaxValue

  private val known = Torrent.knownTorrents.getAndUpdate(_.removed(hash)).get(hash)
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


  metadataUpdate()
  def metadataUpdate(): Unit =
    Option(handle.torrentFile).foreach(known.getOrElse(Known.default).onMetadata(this, _))
    Platform.runLater:
      name.value = Option(handle.name).filter(_.nonEmpty).getOrElse(hash.toString)
      for info <- Option(handle.torrentFile) do
        size.value = info.totalSize
        if (TorrentView.selected.exists(_.torrent == this)) TorrentView.selectedExpr.invalidate()


  private val readRequests = mutable.Map.empty[Int, CompletableFuture[Array[Byte]]]

  def putPieceRequest(piece: Int): CompletableFuture[Array[Byte]] =
    readRequests.synchronized:
      readRequests.getOrElseUpdate(piece, new CompletableFuture[Array[Byte]])

  def putPieceResponse(piece: Int)(data: => Array[Byte]): Unit =
    readRequests.synchronized:
      for future <- readRequests.remove(piece) do
        Try(data) match
          case Success(result) => future.complete(result)
          case Failure(error) => future.completeExceptionally(error)

  def cancelPieceRequests(): Unit =
    readRequests.synchronized:
      readRequests.values.foreach(_.cancel(true))
      readRequests.clear()
