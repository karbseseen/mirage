package p2p

import byte_codec.ByteCodec
import p2p.Message.{Ping, Pong}
import p2p.P2p.*

import java.net.*
import java.nio.channels.{DatagramChannel, SelectionKey, Selector}
import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import java.util.function.Consumer
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.{Random, Try}


class P2p(val roomName: String) extends Tasks:

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
  private val pingTime = mutable.Map.empty[Peer.Id, Long]
  private val messageHandlers = mutable.Map.empty[Class[? <: Message], List[MessageHandler[? <: Message]]]
  private var hasActivePeers = false

  private val buffer = ByteBuffer.allocate(2048)

  private var _myId = Peer.Id(Random.nextLong, Random.nextLong)
  def myId: Peer.Id = _myId


  def addMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
      listOpt => Some(handler :: listOpt.getOrElse(Nil)) 

  def removeMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
      _.map(_.filter(_ != handler)).filter(_.nonEmpty)

  def send(message: Message, peer: Peer): Unit =
    val data = ByteBuffer.wrap(ByteCodec.encode(message))
    peer.channel.send(data, peer.address)

  def sendToAll(data: ByteBuffer): Unit =
    sockets.foreach(_.channel.send(data, multicastAddress))


  protected def loop(timeUntilNext: Option[Long]): Unit =
    selector.select(
      key => key.channel match
        case channel: DatagramChannel if key.isReadable => receiveMessage(channel)
        case _ => (),
      timeUntilNext.map(_ + 10).fold(5000L)(_ min 5000L),
    )

  private def receiveMessage(channel: DatagramChannel): Unit =
    val address = channel.receive(buffer.rewind)
    for
      address <- Some(address).collect { case inet: InetSocketAddress => inet }
      message <- Try(ByteCodec.decode[Message](buffer.array, 0, buffer.position))
    do
      handleMessage(message, address, channel)

  private def handleMessage(message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =
    if (_myId == message.senderId) _myId = Peer.Id(Random.nextLong, Random.nextLong) //Just in case

    val peer = peers.updateWith(message.senderId): foundPeer =>
      val now = System.currentTimeMillis
      val foundLatency = foundPeer.fold(0)(_.latency)
      val (latency, needPeer) = message match
        case ping: Ping => (foundLatency, ping.roomName == roomName && ping.cookie == Ping.cookie)
        case pong: Pong => pingTime.remove(message.senderId)
          .map(pingTime => (now - pingTime).toInt)
          .filter(_ < PingWaitTime)
          .fold(foundLatency, false)(waitTime => ((foundLatency + waitTime) / 3, pong.receiverId == myId)) //current latency = waitTime / 2
        case _ => (foundLatency, foundPeer.nonEmpty)
      Option.when(needPeer):
        Peer(message.senderId, now, latency, address, channel)

    for peer <- peer do
      Some(message).collect { case ping: Ping => send(Pong(myId, ping.senderId), peer) }
      messageHandlers.getOrElse(message.getClass, Nil)
        .foreach(_.asInstanceOf[MessageHandler[Message]].onReceive(message, peer, this))


  protected def onClose(): Unit =
    selector.close()
    sockets.foreach(_.channel.close())


  private var interfaceUpdateTask: Task = schedulePeriodic(0, InterfaceUpdatePeriodSmall)(updateInterfaces())

  private def updateInterfaces(): Unit =
    val oldSockets = sockets.map(socket => socket.interface -> socket).to(mutable.Map)
    sockets = for
      interface <- NetworkInterface.getNetworkInterfaces.asScala.toList
      if interface.isUp && !interface.isLoopback && !interface.isVirtual
      socket <- oldSockets.remove(interface).orElse:
        try Some(new MulticastSocket(interface))
        catch case error: Throwable => { error.printStackTrace(); None }
    yield socket
    oldSockets.values.foreach(_.channel.close())

    val ping = ByteBuffer.wrap(ByteCodec.encode[Message](Ping(myId, roomName, Ping.cookie)))
    sockets.foreach(_.channel.send(ping, multicastAddress))

  private def updateActivePeers(): Unit =
    val now = System.currentTimeMillis
    val hasActivePeers = peers.values.exists(now - _.lastSeen < PeerActiveTime)
    if (this.hasActivePeers != hasActivePeers)
      this.hasActivePeers = hasActivePeers
      interfaceUpdateTask.cancel()
      interfaceUpdateTask =
        if (hasActivePeers) schedulePeriodic(InterfaceUpdatePeriodBig, InterfaceUpdatePeriodBig)(updateInterfaces())
        else schedulePeriodic(0, InterfaceUpdatePeriodSmall)(updateInterfaces())


  schedulePeriodic(PeerLiveTime, PeerLiveTime):
    val now = System.currentTimeMillis
    peers.filterInPlace: (_, peer) =>
      val age = now - peer.lastSeen
      if (age > PeerPingPeriod)
        send(Ping(myId, roomName, Ping.cookie), peer)
        pingTime(peer.id) = now
      age < PeerLiveTime
    updateActivePeers()


  schedulePeriodic(5000 * 60, 5000 * 60):
    val minTime = System.currentTimeMillis - PingWaitTime
    pingTime.filterInPlace { case (_, time) => time > minTime }


  loopThread.start()

  private class MulticastSocket(val interface: NetworkInterface):
    val channel: DatagramChannel = DatagramChannel
      .open(StandardProtocolFamily.INET)
      .setOption(StandardSocketOptions.SO_REUSEADDR, true)
      .bind(InetSocketAddress("0.0.0.0", multicastAddress.getPort))
      .setOption(StandardSocketOptions.IP_MULTICAST_IF, interface)
      .setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false)
    channel.configureBlocking(false)
    channel.join(multicastAddress.getAddress, interface)
    channel.register(selector, SelectionKey.OP_READ)


object P2p:
  inline val PeerActiveTime  = 5_000
  private inline val PeerLiveTime    = 10_000
  private inline val PeerPingPeriod  = 1_500
  private inline val PingWaitTime    = 8_000

  private inline val InterfaceUpdatePeriodSmall = 4_000
  private inline val InterfaceUpdatePeriodBig   = 20_000


class Peer private[p2p] (
  val id: Peer.Id,
  val lastSeen: Long,
  val latency: Int,
  val address: InetSocketAddress,
  private[p2p] val channel: DatagramChannel,
)

object Peer:
  case class Id(part1: Long, part2: Long)
