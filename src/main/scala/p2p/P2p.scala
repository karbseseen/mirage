package p2p

import byte_codec.ByteCodec
import p2p.Message.{Ping, Pong}
import p2p.P2p.*

import java.net.*
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.{DatagramChannel, SelectionKey, Selector}
import java.security.MessageDigest
import java.util.concurrent.TimeUnit.{MILLISECONDS, MINUTES}
import java.util.concurrent.{Executors, Future, ScheduledExecutorService}
import java.util.function.Consumer
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.{Random, Try}


class P2p(val roomName: String):

  private val multicastAddress =
    val roomBytes = ByteBuffer
      .wrap(MessageDigest.getInstance("MD5").digest(roomName.getBytes))
      .order(ByteOrder.BIG_ENDIAN)
    val port = roomBytes.getShort.toInt match
      case port if port < 1024 => 65535 - port
      case port => port
    val ipv4 = Array.tabulate(4):
      case 0 => 239.toByte
      case _ => roomBytes.get
    InetSocketAddress(InetAddress.getByAddress(ipv4), port)

  private val selector: Selector = Selector.open
  private var sockets: List[MulticastSocket] = Nil
  private val peers = mutable.Map.empty[Peer.Id, Peer]
  private[p2p] val pingTime = mutable.Map.empty[Peer.Id, Long]
  private var hasActivePeers = false

  private val buffer = ByteBuffer.allocate(2048)

  private var _myId = Peer.Id(Random.nextLong, Random.nextLong)
  def myId: Peer.Id = _myId
  val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor


  private val interfaceUpdater: Runnable = () =>
    val oldSockets = sockets.map(socket => (socket.interface, socket.ip) -> socket).to(mutable.Map)
    sockets = for
      interface <- NetworkInterface.getNetworkInterfaces.asScala.toList
      if interface.isUp && !interface.isLoopback && !interface.isVirtual
      ip <- interface.getInetAddresses.asScala.collect { case v4: Inet4Address => v4 }
    yield
      oldSockets.remove(interface, ip) getOrElse new MulticastSocket(interface, ip)
    oldSockets.values.foreach(_.channel.close())

    val ping = ByteBuffer.wrap(ByteCodec.encode[Message](Ping(myId, roomName)))
    sockets.foreach(_.channel.send(ping, multicastAddress))

  private var interfaceUpdateTask: Future[?] =
    scheduler.scheduleWithFixedDelay(interfaceUpdater, 0, InterfaceUpdatePeriodSmall, MILLISECONDS)

  private def updateActivePeers(): Unit =
    val hasActivePeers = peers.values.exists(_.isActive)
    if (this.hasActivePeers != hasActivePeers)
      this.hasActivePeers = hasActivePeers
      interfaceUpdateTask.cancel(false)
      interfaceUpdateTask =
        if (hasActivePeers)
          scheduler.scheduleWithFixedDelay(interfaceUpdater, InterfaceUpdatePeriodBig, InterfaceUpdatePeriodBig, MILLISECONDS)
        else
          scheduler.scheduleWithFixedDelay(interfaceUpdater, 0, InterfaceUpdatePeriodSmall, MILLISECONDS)


  private[p2p] def getPeer(id: Peer.Id): Option[Peer] = peers.get(id)

  private[p2p] def updatePeer(peer: Peer): Unit =
    peers(peer.id) = peer
    updateActivePeers()

  private val peerKiller: Runnable = () =>
    val now = System.currentTimeMillis
    peers.filterInPlace: (_, peer) =>
      val age = now - peer.lastSeen
      if (age > PeerPingPeriod || !peer.verified)
        send(Ping(myId, roomName), peer)
        pingTime(peer.id) = System.currentTimeMillis
      age < PeerLiveTime
    updateActivePeers()

  scheduler.scheduleWithFixedDelay(peerKiller, PeerLiveTime, PeerLiveTime, MILLISECONDS)


  private val receiver: Runnable = () =>
    selector.select(
      key => key.channel match
        case channel: DatagramChannel if key.isReadable =>
          val address = channel.receive(buffer.rewind)
          for
            address <- Some(address).collect { case inet: InetSocketAddress => inet }
            message <- Try(ByteCodec.decode[Message](buffer.array))
          do
            if (_myId == message.senderId) _myId = Peer.Id(Random.nextLong, Random.nextLong)    //Just in case
            Message.handle(this, message, address, channel)
        case _ => (),
      1000,
    )

  scheduler.scheduleWithFixedDelay(receiver, 50, 1, MILLISECONDS)


  def send(message: Message, peer: Peer): Unit =
    val data = ByteBuffer.wrap(ByteCodec.encode(message))
    peer.channel.send(data, peer.address)

  def sendToAll(data: ByteBuffer): Unit =
    sockets.foreach(_.channel.send(data, multicastAddress))

  private val pingTimeCleaner: Runnable = () =>
    val minTime = System.currentTimeMillis - PingWaitTime
    pingTime.filterInPlace { case (_, time) => time > minTime }

  scheduler.scheduleWithFixedDelay(pingTimeCleaner, 5, 5, MINUTES)


  private class MulticastSocket(val interface: NetworkInterface, val ip: Inet4Address):
    val channel: DatagramChannel = DatagramChannel
      .open(StandardProtocolFamily.INET)
      .bind(InetSocketAddress(ip, multicastAddress.getPort))
      .setOption(StandardSocketOptions.IP_MULTICAST_IF, interface)
      .setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false)
    channel.configureBlocking(false)
    channel.join(multicastAddress.getAddress, interface)
    channel.register(selector, SelectionKey.OP_READ)


object P2p:
  private[p2p] inline val PeerLiveTime    = 10_000
  private[p2p] inline val PeerActiveTime  = 5_000
  private[p2p] inline val PeerPingPeriod  = 1_500
  private[p2p] inline val PingWaitTime    = 8_000

  private inline val InterfaceUpdatePeriodSmall = 4_000
  private inline val InterfaceUpdatePeriodBig   = 20_000


class Peer private[p2p] (
  val id: Peer.Id,
  val lastSeen: Long,
  val latency: Int,
  val verified: Boolean,
  val address: InetSocketAddress,
  private[p2p] val channel: DatagramChannel,
):
  def isActive: Boolean = verified && System.currentTimeMillis - lastSeen < PeerActiveTime

object Peer:
  case class Id(part1: Long, part2: Long)
