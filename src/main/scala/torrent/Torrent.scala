package torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.swig.{status_flags_t, torrent_flags_t}
import constant.Constants
import core.MainMenu
import core.main.MainApp
import javafx.beans.property.*
import scalafx.Includes.{jfxLongProperty2sfx, jfxObjectProperty2sfx}
import scalafx.application.Platform
import scalafx.beans.property.PropertyIncludes.jfxStringProperty2sfx
import scalafx.collections.ObservableBuffer
import torrent.listener.TorrentListener
import util.{JavaUtil, also}

import java.io.File
import java.nio.file.{Files, Path}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.util.{Random, Try}


object Torrent:

  private[torrent] val session = new SessionManager
  TorrentListener.all.foreach(session.addListener)
  session.start()

  private[torrent] val directory = File(JavaUtil.jarFile.getParentFile, "torrent")
  directory.mkdir()
  /**Edit only in alert thread*/
  private[torrent] var loadingResumeCount = directory.list.toList
    .collect:
      case s"$name.torrent" => name
    .distinct
    .count: name =>
      val torrentFile = File(directory, s"$name.torrent")
      val resumeFile = File(directory, s"$name.resume")
      torrentFile.isFile &&
        resumeFile.isFile &&
        Try(
          session.download(TorrentInfo(torrentFile), null, resumeFile, null, null, new torrent_flags_t)
        ).isSuccess
  if (loadingResumeCount == 0)
    Platform.runLater:
      MainMenu.addMenu.visible = true

  private[torrent] val exitResumeDone = CountDownLatch(1)
  MainApp.shutdownLongHook:
    session.pause()
    session.getTorrentHandles.foreach(_.saveResumeData())
    exitResumeDone.await(2, TimeUnit.SECONDS)
    session.stop()


  private[torrent] class Selected private[Torrent] (val torrent: Torrent, val node: Option[TorrentNode.Root])
  private[torrent] val selected = SimpleObjectProperty[Selected](this, "selected")


  def add(torrentFile: Path, saveDir: File): Unit =
    val torrentFileData =
      try Files.readAllBytes(torrentFile)
      catch case error: Throwable => throw error.also(MainApp.showError)
    val info = TorrentInfo(torrentFileData)
    session.download(info, saveDir, null, null, null, TorrentFlags.STOP_WHEN_READY)

  def add(magnet: String, saveDir: File): Unit =
    session.download(magnet, saveDir, TorrentFlags.UPLOAD_MODE)


  val all: ObservableBuffer[Torrent] = ObservableBuffer.empty



class Torrent(val hash: Hash, isNew: Boolean):

  val handle: TorrentHandle = Torrent.session.find(hash)

  /**Edit only in alert thread*/
  @volatile var nextResumeSaveTime: Long = Long.MaxValue

  private val initState = State(
    value = handle.status(new status_flags_t).state,
    paused = handle.isPaused,
    isNew = isNew,
  )

  val state     = SimpleObjectProperty(this, "state", initState)
  val name      = SimpleStringProperty(this, "name")
  val progress  = SimpleFloatProperty(this, "progress")
  val size      = SimpleLongProperty(this, "size")
  val downSpeed = SimpleIntegerProperty(this, "download")
  val upSpeed   = SimpleIntegerProperty(this, "upload")

  private[torrent] def metadataUpdate(): Unit =
    name.value = Option(handle.name).filter(_.nonEmpty).getOrElse(hash.toString)
    for info <- Option(handle.torrentFile) do
      size.value = info.totalSize
      if (Option(Torrent.selected.value).exists(_.torrent == this)) Torrent.selected.value = select
      if (state().isNew)
        handle.prioritizeFiles { Array.tabulate(info.numFiles)(_ => Priority.IGNORE) }
        nextResumeSaveTime = System.currentTimeMillis
      else
        nextResumeSaveTime = System.currentTimeMillis + Random.nextLong(Constants.resumeSavePeriod)
  metadataUpdate()

  private[torrent] def select: Torrent.Selected =
    if (handle.isValid)
      val node = Option(handle.torrentFile).map(TorrentNode.Root(_))
      node.foreach(_.setFilePriority(handle.filePriorities))
      node.foreach(_.setFileProgress(handle.fileProgress))
      Torrent.Selected(this, node)
    else null
