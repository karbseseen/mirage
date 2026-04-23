package torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.swig.status_flags_t
import core.main.MainApp
import javafx.beans.property.*
import scalafx.Includes.{jfxLongProperty2sfx, jfxObjectProperty2sfx}
import scalafx.beans.property.PropertyIncludes.jfxStringProperty2sfx
import scalafx.collections.ObservableBuffer
import torrent.listener.TorrentListener
import util.also

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable


object Torrent:

  private[torrent] val session = new SessionManager
  TorrentListener.all.foreach(session.addListener)
  session.start()
  MainApp.shutdownLongHook(session.stop())

  @volatile private var loadingResume = false

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


  def pause(hash: Hash): Unit = Option(session.find(hash)).foreach(_.pause())
  def resume(hash: Hash): Unit = Option(session.find(hash)).foreach(_.resume())


  /**UI thread only*/ val all: ObservableBuffer[Torrent] = ObservableBuffer.empty
  /**UI thread only*/ def find(hash: Hash): Option[Torrent] =
    map.get(hash).also: torrent =>
      if (torrent.isEmpty)
        Exception(s"Couldn't find torrent with hash $hash, ${hash.getClass.getSimpleName}").printStackTrace()

  private val map = mutable.Map.empty[Hash, Torrent]
  all.onChange: (_, changes) =>
    changes.foreach:
      case ObservableBuffer.Add(_, added) => map ++= added.map { torrent => torrent.hash -> torrent }
      case ObservableBuffer.Remove(_, removed) => map --= removed.map { torrent => torrent.hash }
      case _ => ()



class Torrent(val hash: Hash):

  val handle: TorrentHandle = Torrent.session.find(hash)

  val state     = SimpleObjectProperty(this, "state", State(
    value = handle.status(new status_flags_t).state,
    paused = handle.isPaused,
    isNew = !Torrent.loadingResume,
  ))
  val name      = SimpleStringProperty(this, "name")
  val progress  = SimpleFloatProperty(this, "progress")
  val size      = SimpleLongProperty(this, "size")
  val downSpeed = SimpleIntegerProperty(this, "download")
  val upSpeed   = SimpleIntegerProperty(this, "upload")

  private[torrent] def metadataUpdate(): Unit =
    name.value = Option(handle.name).filter(_.nonEmpty).getOrElse(hash.toString)
    for info <- Option(handle.torrentFile) do
      size.value = info.totalSize
      if (state.getValue.isNew) handle.prioritizeFiles { Array.tabulate(info.numFiles)(_ => Priority.IGNORE) }
      if (Option(Torrent.selected.value).exists(_.torrent == this)) Torrent.selected.value = select
  metadataUpdate()

  private[torrent] def select: Torrent.Selected =
    if (handle.isValid) Torrent.Selected(this, Option(handle.torrentFile).map(TorrentNode.Root(_)))
    else null
