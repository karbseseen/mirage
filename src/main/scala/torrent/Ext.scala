package torrent

import byte_codec.ByteCodec
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.TorrentRemovedAlert
import com.frostwire.jlibtorrent.swig.{byte_vector, info_hash_t}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.math.Ordered.orderingToOrdered


opaque type Hash <: AnyRef = Sha1Hash | Sha256Hash

object Hash:

  implicit def apply(value: Sha1Hash | Sha256Hash): Hash = value
  val empty: Hash = Sha1Hash.min

  extension (hashes: info_hash_t) private def hash: Hash =
    if (hashes.has_v2) Sha256Hash(hashes.getV2).clone else Sha1Hash(hashes.getV1).clone
  extension (info: TorrentInfo)           def hash: Hash = info.infoHashType.hash
  extension (handle: TorrentHandle)       def hash: Hash = handle.swig.info_hashes.hash
  extension (status: TorrentStatus)       def hash: Hash = status.swig.getInfo_hashes.hash
  extension (params: AddTorrentParams)    def hash: Hash = params.swig.getInfo_hashes.hash
  extension (alert: TorrentRemovedAlert)  def hash: Hash = alert.swig.getInfo_hashes.hash

  extension (hash: Hash)
    def swigBytes: byte_vector = hash match
      case hash: Sha1Hash   => hash.swig.to_bytes
      case hash: Sha256Hash => hash.swig.to_bytes
  
  extension (session: SessionManager)
    def find(hash: Hash): TorrentHandle = hash match
      case sha1:    Sha1Hash    => session.find(sha1)
      case sha256:  Sha256Hash  => session.find(sha256)

  implicit val ordering: Ordering[Hash] = Ordering.by(_.swigBytes.asScala)

  implicit val byteCodec: ByteCodec[Hash] = new ByteCodec:
    def encode(hash: Hash, output: ByteArrayOutputStream): Unit =
      val bytes = hash.swigBytes
      ByteCodec.compactUInt.encode(bytes.size, output)
      bytes.forEach(ByteCodec.byte.encode(_, output))
    def decode(input: ByteArrayInputStream): Hash =
      val array = ByteCodec.decode[Array[Byte]](input)
      array.length match
        case 20 => Sha1Hash(array)
        case 32 => Sha256Hash(array)


extension (handle: TorrentHandle) private def isPaused = handle.flags.and_(TorrentFlags.PAUSED).nonZero
