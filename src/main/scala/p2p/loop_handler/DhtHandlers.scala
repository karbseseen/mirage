package p2p.loop_handler

import byte_codec.ByteCodec
import com.frostwire.jlibtorrent.alerts.{DhtGetPeersReplyAlert, DhtMutableItemAlert, ExternalIpAlert}
import com.frostwire.jlibtorrent.swig.{byte_vector, entry as swigEntry}
import com.frostwire.jlibtorrent.{Ed25519, Entry, SessionHandle, Sha1Hash}
import javafx.beans.InvalidationListener
import p2p.*
import p2p.P2p.{Socket, Undiscovered}
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx}
import sun.nio.cs.UTF_8
import torrent.listener.TorrentListener
import torrent.torrentSession

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, MembershipKey, Selector}
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.util.{Random, Try}


private[p2p] class DhtPutHandler extends LoopHandler:

  private inline val Period = 20 * 60 * 1000

  private val randomPort = Random.nextInt(64000) + 1000

  private var lastAddresses: List[InetSocketAddress] = Nil
  private var torrentRemoteIp: Option[String] = None
  private var lastTime = 0L
  roomName.addListener(_ => P2p.runLater { lastTime = 0 })

  def loop(now: Long, activePeers: List[Peer]): Unit =
    val addresses = List(P2p.v6RemoteSocket, P2p.v4RemoteSocket).flatMap(_.address())
    if (lastAddresses != addresses)
      lastAddresses = addresses
      lastTime = 0

    for
      ip <- torrentRemoteIp
      if now - lastTime > Period
      if addresses.nonEmpty
    do
      import DhtHelp.*
      lastTime = now
      val (publicKey, privateKey) = itemKeyPair(ip, randomPort)
      val myInfo = Undiscovered(P2p.myId, addresses)
      val entry = swigEntry.from_string_bytes(byte_vector(ByteCodec.encode(myInfo)))
      torrentSession.dhtPutItem(publicKey, privateKey, Entry(entry), Array.empty)
      torrentSession.dhtAnnounce(roomHash)

  TorrentListener.listen[ExternalIpAlert]: alert =>
    P2p.runLater:
      torrentRemoteIp = Some(alert.externalAddress.toString)
      lastTime = 0


private[p2p] class DhtGetHandler extends LoopHandler:
  import DhtHelp.*

  private inline val PeriodSmall = 4_000
  private inline val PeriodBig   = 40_000

  private var lastTime = 0L

  def loop(now: Long, activePeers: List[Peer]): Unit =
    if (now - lastTime > (if (activePeers.nonEmpty) PeriodBig else PeriodSmall))
      lastTime = now
      torrentSession.swig.dht_get_peers(roomHash.swig)

  TorrentListener.listen[DhtGetPeersReplyAlert]: alert =>
    if (alert.infoHash == roomHash)
      alert.peers
  
  TorrentListener.listen[DhtMutableItemAlert]: alert =>
    val swigBytes = alert.item.swig().string_bytes
    val bytes = Array.tabulate[Byte](swigBytes.size)(swigBytes.get)
    for
      undiscovered <- Try(ByteCodec.decode[Undiscovered](bytes))
      if undiscovered.id != P2p.myId
      if P2p.peers.get(undiscovered.id).forall(alert.timestamp - _.lastSeen > P2p.PeerActiveTime)
    do
      P2p.undiscovered(undiscovered.id) = undiscovered

  private class TorrentPeer(ip: String, port: Int):
    var 


private object DhtHelp:

  def itemKeyPair(ip: String, port: Int): (Array[Byte], Array[Byte]) =
    val seed = ip + port + roomName() + "Mirage the coolest player in the world"
    val pair = Ed25519.createKeypair(seed.getBytes)
    (pair.first, pair.second)

  private val _roomHash = roomName.map(roomName => Sha1Hash(roomName.getBytes))
  def roomHash: Sha1Hash = _roomHash()


given ByteCodec[InetSocketAddress] with

  def encode(value: InetSocketAddress, output: ByteArrayOutputStream): Unit =
    summon[ByteCodec[Array[Byte]]].encode(value.getAddress.getAddress, output)
    ByteCodec.short.encode(value.getPort.toShort, output)

  def decode(input: ByteArrayInputStream): InetSocketAddress =
    val address = InetAddress.getByAddress(summon[ByteCodec[Array[Byte]]].decode(input))
    val port = ByteCodec.short.decode(input)
    InetSocketAddress(address, port)
