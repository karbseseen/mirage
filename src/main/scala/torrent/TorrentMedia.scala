package torrent

import com.frostwire.jlibtorrent.swig.deadline_flags_t
import com.frostwire.jlibtorrent.{Priority, TorrentHandle}
import com.sun.jna.Pointer
import constant.Constants
import uk.co.caprica.vlcj.media.callback.AbstractCallbackMedia
import vlc.VlcMedia

import scala.compiletime.uninitialized
import scala.util.Try


class TorrentMedia(torrent: Torrent, fileIndex: Int) extends AbstractCallbackMedia(true) with VlcMedia:

  private val info = torrent.handle.torrentFile
  private val files = info.files
  private val fileSize = files.fileSize(fileIndex)
  private val pieceSize = info.pieceLength
  private val simultaneousPieceNum = Constants.playerSimultaneousBytes / pieceSize

  private var current: Pos = uninitialized
  private var currentData = Data(-1, Array.empty)
  private val end = Pos.fromFileOffset(fileSize)


  def onGetSize: Long = fileSize

  def onOpen: Boolean =
    torrent.handle.filePriority(fileIndex, Priority.SEVEN)
    onSeek(0)
    true

  def onClose(): Unit =
    torrent.handle.clearPieceDeadlines()
    torrent.handle.filePriority(fileIndex, Priority.NORMAL)

  def onRead(buffer: Pointer, bufferSize: Int): Int =
    Try(read(buffer, bufferSize)).getOrElse(-1)

  def onSeek(offset: Long): Boolean =
    if (offset < 0 || offset > fileSize) false
    else
      current = Pos.fromFileOffset(offset)
      torrent.handle.clearPieceDeadlines()
      setDeadlines()
      true

  def getName: String = files.fileName(fileIndex)

  def shutdown(): Unit =
    torrent.cancelPieceRequests()


  private def read(buffer: Pointer, bufferSize: Int): Int =

    if (currentData.piece != current.piece)
      val future = torrent.putPieceRequest(current.piece)
      if (torrent.handle.havePiece(current.piece)) torrent.handle.readPiece(current.piece)
      else setDeadlines(requestFirst = true)
      currentData = Data(current.piece, future.get())

    val (toRead, nextCurrent) =
      def default = (bufferSize, Pos(current.piece, current.pieceOffset + bufferSize))
      if (current.piece == end.piece)
        val pieceAvailable = end.pieceOffset - current.pieceOffset
        if (pieceAvailable <= bufferSize) (pieceAvailable, end) else default
      else
        val pieceAvailable = pieceSize - current.pieceOffset
        if (pieceAvailable <= bufferSize) (pieceAvailable, Pos(current.piece + 1, 0)) else default

    buffer.write(0, currentData.value, current.pieceOffset, toRead)
    current = nextCurrent
    toRead


  private def setDeadlines(requestFirst: Boolean = false): Unit =
    val iUntil = simultaneousPieceNum min Pos.fromFileOffset(fileSize - 1).piece - current.piece + 1
    for i <- 0 until iUntil do
      torrent.handle.setPieceDeadline(
        current.piece + i,
        (i + 1) * Constants.playerSimultaneousTime / simultaneousPieceNum,
        if (i == 0 && requestFirst) TorrentHandle.ALERT_WHEN_AVAILABLE else new deadline_flags_t,
      )


  private class Pos(val piece: Int, val pieceOffset: Int)
  private object Pos:
    def fromFileOffset(fileOffset: Long): Pos =
      val torrentOffset = files.fileOffset(fileIndex) + fileOffset
      Pos((torrentOffset / pieceSize).toInt, (torrentOffset % pieceSize).toInt)

  private class Data(val piece: Int, val value: Array[Byte])
