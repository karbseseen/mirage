package torrent

import com.frostwire.jlibtorrent.alerts.TorrentRemovedAlert
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import com.frostwire.jlibtorrent.{AddTorrentParams, Priority, SessionManager, TorrentInfo}
import constant.{Tr, Translate}
import core.main.MainApp
import scalafx.Includes.jfxStringProperty2sfx
import scalafx.application.Platform
import torrent.listener.TorrentListener
import util.also

import java.io.File
import java.util.concurrent.CountDownLatch
import scala.util.{Failure, Success, Try}


object Torrent:
  private val session = new SessionManager
  TorrentListener.all.foreach(session.addListener)
  session.start()
  MainApp.shutdownLongHook(session.stop())

  /** UI thread only */
  def add(torrentFile: Array[Byte], saveDir: File): Unit =
    try addInner(torrentFile, saveDir, None)
    catch case error => MainApp.showError(error)

  private def addInner(torrentFile: Array[Byte], saveDir: File, _node: Option[TorrentNode.Root]): Unit =
    val info = TorrentInfo(torrentFile)
    val hash = info.infoHashV1.toHex

    _node.foreach(_.name.value = info.name)
    val node = _node getOrElse {
      if (TorrentNode.getTorrent(hash).nonEmpty) sys.error(Tr.torrentExists.getValue)
      TorrentNode.Root(info.name, hash).also(TorrentNode.roots += _)
    }
    node.setFiles(info)

    val priorities = Array.tabulate(info.numFiles)(_ => Priority.IGNORE)
    session.download(info, saveDir, null, priorities, null, torrent_flags_t())


  /**UI thread only*/
  def add(magnet: String, saveDir: File): Unit =
    try addInner(magnet, saveDir)
    catch case error => MainApp.showError(error)

  private def addInner(magnet: String, saveDir: File): Unit =
    val params = AddTorrentParams.parseMagnetUri(magnet)
    val hash = params.getInfoHashes.getBest.toHex
    if (TorrentNode.getTorrent(hash).nonEmpty) sys.error(Tr.torrentExists.getValue)

    val name = Some(params.name).filter(_.nonEmpty).getOrElse(hash)
    val node = TorrentNode.Root(name, hash)
    TorrentNode.roots += node

    val torrentRemoved = CountDownLatch(1)
    val removeListener = TorrentListener.Part[TorrentRemovedAlert]: event =>
      if (event.infoHash.toHex == hash) torrentRemoved.countDown()
    session.addListener(removeListener)

    val thread = Thread: () =>
      Try(session.fetchMagnet(magnet, Int.MaxValue, saveDir)) match
        case Failure(_) | Success(null) => MainApp.showError(Tr.magnetAddError)
        case Success(torrentFile) =>
          torrentRemoved.await()
          Platform.runLater:
            addInner(torrentFile, saveDir, Some(node))
            session.removeListener(removeListener)
          ()//todo save `torrentFile`
    thread.setDaemon(true)
    thread.start()
