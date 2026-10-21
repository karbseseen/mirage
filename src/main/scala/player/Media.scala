package player

import byte_codec.ByteCodec.CompactUInt
import byte_codec.Discriminator
import torrent.{Hash, TorrentMedia}
import uk.co.caprica.vlcj.media.callback.CallbackMedia

import scala.language.implicitConversions


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
