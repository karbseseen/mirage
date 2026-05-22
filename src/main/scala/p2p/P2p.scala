package p2p

import byte_codec.ByteCodec
import p2p.Message.{Bye, MulticastAnnounce, Ping, Pong}
import p2p.P2p.*
import p2p.Peers.PeerImpl

import java.net.*
import java.nio.channels.{DatagramChannel, SelectionKey, Selector}
import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import java.util.function.Consumer
import scala.collection.mutable
import scala.io.StdIn
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.{Random, Try}


class P2p(val roomName: String) extends Tasks with Peers:

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
  private val messageHandlers = mutable.Map.empty[Class[? <: Message], List[MessageHandler[? <: Message]]]
  private var hasActivePeers = false

  private val buffer = ByteBuffer.allocate(2048)

  private var _myId = Peer.Id(Random.nextLong, Random.nextLong)
  def myId: Peer.Id = _myId


  def addMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    threadSafe:
      messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
        listOpt => Some(handler :: listOpt.getOrElse(Nil))

  def removeMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    threadSafe:
      messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
        _.map(_.filter(_ != handler)).filter(_.nonEmpty)

  def multicast(message: Message): Unit =
    println(s"* <- $message")
    val data = ByteBuffer.wrap(ByteCodec.encode(message))
    sockets.foreach(_.channel.send(data.rewind, multicastAddress))


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
    println(s"$address -> $message")
    if (_myId == message.senderId) _myId = Peer.Id(Random.nextLong, Random.nextLong) //Just in case

    val now = System.currentTimeMillis
    val foundPeer = peerImpls.get(message.senderId)

    val (newLatencyX2, needPeer) = message match
      case announce: MulticastAnnounce =>
        if (foundPeer.isEmpty && pingTime.get(announce.senderId).forall(now - _ > Peers.PingWaitTime))
          Peer.send(Ping(myId, roomName, Ping.cookie, 0), address, channel)
          pingTime(announce.senderId) = now
        (0, foundPeer.nonEmpty)
      case ping: Ping =>
        Peer.send(Pong(myId, ping.senderId), address, channel)
        (ping.latency * 2, ping.roomName == roomName && ping.cookie == Ping.cookie)
      case pong: Pong => pingTime.remove(pong.senderId)
        .map(pingTime => (now - pingTime).toInt)
        .filter(_ < Peers.PingWaitTime)
        .fold(0, false)((_, pong.receiverId == myId))
      case bye: Bye => (0, false)
      case _ => (0, foundPeer.nonEmpty)

    def latency = (newLatencyX2 :: foundPeer.map(_.latency).toList)
      .filter(_ > 0)
      .reduceOption((newX2, found) => (found * 6 + newX2) / 8)
      .getOrElse(0)

    val peer = foundPeer match
      case None if needPeer => Some(PeerImpl(message.senderId, this, latency, address, channel))
      case Some(peer) if needPeer => peer.update(latency, address, channel); Some(peer)
      case Some(peer) if !needPeer => peer.kill(); None
      case _ => None

    for
      peer <- peer
      handler <- messageHandlers.getOrElse(message.getClass, Nil)
    do
      handler.asInstanceOf[MessageHandler[Message]].onReceive(message, peer, this)


  protected def onClose(): Unit =
    sendToAll(Bye(myId))
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

    for
      socket <- oldSockets.values
      _ = socket.channel.close()
      peer <- peerImpls.values
      if peer.channel == socket.channel
    do
      peer.kill()

    print(sockets.map(_.interface).mkString("(", ", ", ") "))
    multicast(MulticastAnnounce(myId, roomName, Ping.cookie))

  protected def onHasActivePeerChanged(hasActivePeers: Boolean): Unit =
    println(s"hasActivePeers: $hasActivePeers")
    if (this.hasActivePeers != hasActivePeers)
      this.hasActivePeers = hasActivePeers
      interfaceUpdateTask.cancel()
      interfaceUpdateTask =
        if (hasActivePeers) schedulePeriodic(InterfaceUpdatePeriodBig, InterfaceUpdatePeriodBig)(updateInterfaces())
        else schedulePeriodic(0, InterfaceUpdatePeriodSmall)(updateInterfaces())


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

  start()


object P2p:
  private inline val InterfaceUpdatePeriodSmall = 5_000
  private inline val InterfaceUpdatePeriodBig   = 20_000

  def main(args: Array[String]): Unit =
    val p2p = P2p("Test room")
    println("multicastAddress: " + p2p.multicastAddress)

    p2p.addPeerListener: (peer, added) =>
      println(s"${peer.address} ${if (added) "born" else "died"}")

    for
      line <- Iterator.continually(StdIn.readLine).takeWhile(_ != "q")
      peer <- p2p.peers.values
    do
      peer.send(Message.Test(p2p.myId, line))

    p2p.close()
