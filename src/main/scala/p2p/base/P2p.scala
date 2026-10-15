package p2p.base

import config.Config
import core.TaskQueue.*
import p2p.base.Message.*
import p2p.base.P2p.*
import scalafx.Includes.jfxProperty2sfx

import java.net.*
import java.nio.channels.{DatagramChannel, SelectionKey}
import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.Random


object P2p extends Peer.Trait with Message.Trait with Nick.Trait:

  private inline val InterfaceUpdatePeriodSmall = 5_000
  private inline val InterfaceUpdatePeriodBig   = 20_000

  val roomName = Config.StringProp(this, "roomName")
  private[p2p] var _roomName = roomName()
  private var multicastAddress = getMulticastAddress(_roomName)

  private var sockets: List[MulticastSocket] = Nil

  private val buffer = ByteBuffer.allocate(2048)

  private var _myId = Peer.Id(Random.nextLong, Random.nextLong)
  def myId: Peer.Id = _myId


  roomName.addListener: (_, _, roomName) =>
    scheduleSingleAt(0):
      _roomName = roomName
      multicastAddress = getMulticastAddress(roomName)
      sockets.foreach(_.channel.close())
      sockets = Nil
      peers.values.foreach(_.kill())

  private def getMulticastAddress(roomName: String) =
    val roomBytes = ByteBuffer
      .wrap(MessageDigest.getInstance("MD5").digest(roomName.getBytes))
      .order(ByteOrder.BIG_ENDIAN)
    val port = (roomBytes.getShort.toInt & 0xffff) match
      case port if port < 1024 => 65535 - port
      case port => port
    val ipv4 = Array.tabulate(4):
      case 0 => 239.toByte
      case _ => roomBytes.get
    InetSocketAddress(InetAddress.getByAddress(ipv4), port)


  onClose(sendToAll(Bye))

  private def receiveMessage(channel: DatagramChannel): Unit =
    for
      address <- Iterator
        .continually(channel.receive(buffer.rewind))
        .takeWhile(_ != null)
        .collect { case inet: InetSocketAddress => inet }
      message <- Message.decode(buffer.slice(0, buffer.position))
    do
      handleMessage(message, address, channel)

  private def handleMessage(holder: Message.Holder, address: InetSocketAddress, channel: DatagramChannel): Unit =
    if (_myId == holder.senderId) _myId = Peer.Id(Random.nextLong, Random.nextLong) //Just in case

    val foundPeer = peers.get(holder.senderId)

    def createOrUpdatePeer(nick: String, latency: Float, pongSent: Boolean) = foundPeer match
      case Some(peer) => peer.update(address, channel, nick = Some(nick), newLatency = Some(latency), pongSent = pongSent)
      case None => Peer(holder.senderId, nick, latency, address, channel)

    val peer = holder.message match
      case announce: MulticastAnnounce =>
        if (foundPeer.isEmpty && announce.roomName == _roomName && announce.cookie == MulticastAnnounce.cookie)
          Peer.ping(holder.senderId, 0, address, channel)
        foundPeer.map(_.update(address, channel))
      case ping: Ping =>
        Option.when(ping.roomName == _roomName && ping.cookie == Ping.cookie):
          send(Pong(ping.id, Nick._value), address, channel)
          createOrUpdatePeer(ping.senderNick, ping.latency, pongSent = true)
      case pong: Pong =>
        Peer.getPingWaitTime(holder.senderId, pong.pingId).map: latencyX2 =>
          createOrUpdatePeer(pong.senderNick, latencyX2 * 0.5f, pongSent = false)
      case Bye => None
      case custom => foundPeer.map(_.update(address, channel, customReceived = true))

    (foundPeer, peer) match
      case (Some(oldPeer), None) => oldPeer.kill()
      case _ => ()

    for
      peer <- peer
      handler <- Message.getHandlers(holder.message.getClass)
    do
      try handler.asInstanceOf[Message.Handler[Message]].onReceive(holder.message, peer)
      catch case error: Throwable => error.printStackTrace()


  private var interfaceUpdateTask: Task = schedulePeriodic(0, InterfaceUpdatePeriodSmall)(updateInterfaces())

  private def updateInterfaces(): Unit =
    val oldSockets = sockets.map(socket => socket.interface -> socket).to(mutable.Map)

    sockets = for
      interface <- NetworkInterface.getNetworkInterfaces.asScala.toList
      if interface.isUp && !interface.isLoopback && !interface.isVirtual &&
        interface.getInetAddresses.asScala.exists(_.isInstanceOf[Inet4Address])
      socket <- oldSockets.remove(interface).orElse:
        try Some(new MulticastSocket(interface))
        catch case error: Throwable => { error.printStackTrace(); None }
    yield socket

    for
      socket <- oldSockets.values
      _ = socket.channel.close()
      peer <- peers.values
      if peer.channel == socket.channel
    do
      peer.kill()

    val data = MulticastAnnounce(_roomName).encode
    sockets.foreach(_.channel.send(data.rewind, multicastAddress))

  private[p2p] def onHasActivePeerChanged(hasActivePeers: Boolean): Unit =
    interfaceUpdateTask.cancel()
    interfaceUpdateTask =
      if (hasActivePeers) schedulePeriodic(InterfaceUpdatePeriodBig, InterfaceUpdatePeriodBig)(updateInterfaces())
      else schedulePeriodic(0, InterfaceUpdatePeriodSmall)(updateInterfaces())


  private class MulticastSocket(val interface: NetworkInterface):
    val channel: DatagramChannel = DatagramChannel
      .open(StandardProtocolFamily.INET)
      .setOption(StandardSocketOptions.SO_REUSEADDR, true)
      .bind(InetSocketAddress("0.0.0.0", multicastAddress.getPort))
    channel.configureBlocking(false)
    register(channel, SelectionKey.OP_READ)(receiveMessage(channel))
    if (interface.supportsMulticast)
      channel.join(multicastAddress.getAddress, interface)
      channel
        .setOption(StandardSocketOptions.IP_MULTICAST_IF, interface)
        .setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false)
