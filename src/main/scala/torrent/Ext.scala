package torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.TorrentRemovedAlert
import com.frostwire.jlibtorrent.swig.info_hash_t


private opaque type Hash = Sha1Hash | Sha256Hash

private object Hash:
  extension (hashes: info_hash_t) private def hash: Hash =
    if (hashes.has_v2) Sha256Hash(hashes.getV2).clone else Sha1Hash(hashes.getV1).clone
  extension (info: TorrentInfo)           def hash: Hash = info.infoHashType.hash
  extension (handle: TorrentHandle)       def hash: Hash = handle.swig.info_hashes.hash
  extension (status: TorrentStatus)       def hash: Hash = status.swig.getInfo_hashes.hash
  extension (params: AddTorrentParams)    def hash: Hash = params.swig.getInfo_hashes.hash
  extension (alert: TorrentRemovedAlert)  def hash: Hash = alert.swig.getInfo_hashes.hash

  extension (session: SessionManager)
    def find(hash: Hash): TorrentHandle = hash match
      case sha1:    Sha1Hash    => session.find(sha1)
      case sha256:  Sha256Hash  => session.find(sha256)


extension (handle: TorrentHandle) private def isPaused = handle.flags.and_(TorrentFlags.PAUSED).nonZero
