package player

import byte_codec.ByteCodec.CompactUInt
import byte_codec.Discriminator
import torrent.{Hash, TorrentMedia}
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia
import util.also

import scala.language.implicitConversions
import scala.util.{Try, Using}


trait Media extends CallbackMedia:
  def fileId: Media.FileId
  def name: String
  def close(): Unit

object Media:
  sealed trait FileId extends Comparable[FileId]:
    def configId: (String, String)

  @Discriminator(1) case class TorrentFileId(hash: torrent.Hash, index: CompactUInt) extends FileId:
    def configId: (String, String) = (hash.toString, index.toString)
    def compareTo(other: FileId): Int = other match
      case other: TorrentFileId =>
        val byHash = Hash.ordering.compare(hash, other.hash)
        if (byHash != 0) byHash else index - other.index
      //Uncomment when there is more FileId children  //case _ => getClass.getName compare other.getClass.getName

  def play(fileId: FileId): Unit = fileId match
    case torrent: TorrentFileId => TorrentMedia.play(torrent)


class ResourceMedia(resource: String) extends SeekableCallbackMedia:

  private val data = Using(getClass.getResourceAsStream(resource))(_.readAllBytes).toOption
  private var pos = 0

  def onOpen(): Boolean = data.nonEmpty
  def onClose(): Unit = ()
  def onGetSize: Long = data.fold(0)(_.length)

  def onRead(buffer: Array[Byte], bufferSize: Int): Int =
    data.fold(-1): data =>
      val size = bufferSize min (data.length - pos)
      Array.copy(data, pos, buffer, 0, size)
      pos += size
      size

  def onSeek(offset: Long): Boolean =
    data.exists(offset >= 0 && offset <= _.length).also:
      if (_) pos = offset.toInt
