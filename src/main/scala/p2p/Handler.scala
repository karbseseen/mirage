package p2p

import byte_codec.ByteCodec
import com.frostwire.jlibtorrent.alerts.DhtMutableItemAlert
import com.frostwire.jlibtorrent.swig.{byte_vector, entry as swigEntry}
import com.frostwire.jlibtorrent.{Ed25519, Entry, SessionHandle}
import p2p.P2p.Undiscovered
import torrent.listener.TorrentListener
import torrent.torrentSession

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.*
import scala.jdk.CollectionConverters.*
import scala.util.{Random, Try}


private trait Handler:
  def handle(now: Long, hasActivePeers: Boolean): Unit


private class LocalAddressHandler extends Handler:

  private inline val PeriodSmall  = 3_000
  private inline val PeriodBig    = 30_000

  private var _value: List[InetSocketAddress] = Nil
  private var lastTime = 0L

  def value: List[InetSocketAddress] = _value
  def handle(now: Long, hasActivePeers: Boolean): Unit =
    if (now - lastTime > (if (hasActivePeers) PeriodSmall else PeriodBig))
      val addresses = for
        interface <- NetworkInterface.getNetworkInterfaces.asScala
        if interface.isUp && !interface.isLoopback && !interface.isVirtual
        address <- interface.getInetAddresses.asScala
      yield
        val socket = address match
          case v4: Inet4Address => P2p.socketV4
          case v6: Inet6Address => P2p.socketV6
        InetSocketAddress(address, socket.channel.socket.getLocalPort)
      _value = addresses.toList
      lastTime = now


private class StunHandler extends Handler:

  private inline val Period = 3_000
  private inline val DiePeriod = 30_000

  def handle(now: Long, hasActivePeers: Boolean): Unit =
    for socket <- List(P2p.socketV4, P2p.socketV6) do
      if (!hasActivePeers && now - socket.remoteAddressTime > Period)
        Stun.sendRequest(socket.channel)
        socket.remoteAddressTime = now
      if (now - socket.remoteAddressTime > DiePeriod)
        socket.remoteAddress = None


private class DhtHandler extends Handler:

  private var getRandom = 0L
  private var putRandom = 0L
  private def getPeriodSmall = 4_000  + getRandom
  private def getPeriodBig   = 40_000 + getRandom * 5
  private def putPeriodSmall = 4_000  + putRandom
  private def putPeriodBig   = 60_000 + putRandom * 5

  private val salt = "Mirage the coolest player in the world".getBytes
  private val (publicKey, privateKey) =
    val pair = Ed25519.createKeypair("Room".getBytes)
    (pair.first, pair.second)

  private var getTime = 0L
  private var putTime = 0L

  def handle(now: Long, hasActivePeers: Boolean): Unit =

    if (now - getTime > (if (hasActivePeers) getPeriodBig else getPeriodSmall))
      SessionHandle(torrentSession.swig).dhtGetItem(publicKey, salt)
      getRandom = Random.nextLong(2_000)
      getTime = now

    if (now - putTime > (if (hasActivePeers) putPeriodBig else putPeriodSmall))
      val myInfo = Undiscovered(P2p.myId, P2p.localAddresses, List(P2p.socketV6, P2p.socketV4).flatMap(_.remoteAddress))
      val entry = swigEntry.from_string_bytes(byte_vector(ByteCodec.encode(myInfo)))
      torrentSession.dhtPutItem(publicKey, privateKey, Entry(entry), salt)
      putRandom = Random.nextLong(2_000)
      putTime = now

  new TorrentListener:
    listen[DhtMutableItemAlert]: alert =>
      val swigBytes = alert.item.swig().string_bytes
      val bytes = Array.tabulate[Byte](swigBytes.size)(swigBytes.get)
      for
        undiscovered <- Try(ByteCodec.decode[Undiscovered](bytes))
        if undiscovered.id != P2p.myId
        if P2p.peers.get(undiscovered.id).forall(alert.timestamp - _.lastSeen > P2p.PeerActiveTime)
      do
        P2p.undiscovered(undiscovered.id) = undiscovered
    register()


private class PeerHandler extends Handler:

  def handle(now: Long, hasActivePeers: Boolean): Unit =
    P2p.undiscovered.values
    


given ByteCodec[InetSocketAddress] with

  def encode(value: InetSocketAddress, output: ByteArrayOutputStream): Unit =
    summon[ByteCodec[Array[Byte]]].encode(value.getAddress.getAddress, output)
    ByteCodec.short.encode(value.getPort.toShort, output)

  def decode(input: ByteArrayInputStream): InetSocketAddress =
    val address = InetAddress.getByAddress(summon[ByteCodec[Array[Byte]]].decode(input))
    val port = ByteCodec.short.decode(input)
    InetSocketAddress(address, port)
