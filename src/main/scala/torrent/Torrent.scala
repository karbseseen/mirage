package torrent

import com.frostwire.jlibtorrent.*
import core.main.MainApp
import javafx.beans.property.{SimpleFloatProperty, SimpleIntegerProperty, SimpleObjectProperty, SimpleStringProperty}
import javafx.scene.control as jfxsc
import scalafx.Includes.jfxTreeItem2sfx
import scalafx.collections.ObservableBuffer
import torrent.listener.TorrentListener
import util.{also, toIArray}

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable


object Torrent:

  private[torrent] val session = new SessionManager
  TorrentListener.all.foreach(session.addListener)
  session.start()
  MainApp.shutdownLongHook(session.stop())

  @volatile private[torrent] var loadingResume = false


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



class Torrent(val hash: Hash, _name: String, _state: State):

  val state     = new SimpleObjectProperty(this, "state", _state)
  val name      = new SimpleStringProperty(this, "name", _name)
  val progress  = new SimpleFloatProperty(this, "progress")
  val downSpeed = new SimpleIntegerProperty(this, "download")
  val upSpeed   = new SimpleIntegerProperty(this, "upload")

  private[torrent] val tree = new jfxsc.TreeItem[TorrentNode]
  private var _files = IArray.empty[TorrentNode.File]
  def files: IArray[TorrentNode.File] = _files
  private[torrent] def files_=(info: FileInfo): Unit =
    tree.children = info.treeChildren
    _files = info.files
