package p2p.base

import config.Config
import core.TaskQueue.*
import p2p.base.Message.{Bye, MulticastAnnounce, Ping, Pong}
import p2p.base.P2p.*
import p2p.base.Peers.PeerImpl
import scalafx.Includes.jfxProperty2sfx

import java.net.*
import java.nio.channels.{DatagramChannel, SelectionKey}
import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.{Random, Try}


object P2p extends Peers with Messages:

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
      peerImpls.values.foreach(_.kill())

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
    val address = channel.receive(buffer.rewind)
    if (address == null) return
    for
      address <- Some(address).collect { case inet: InetSocketAddress => inet }
      message <- Try(buffer.decode)
    do
      handleMessage(message, address, channel)

  private def handleMessage(holder: MessageHolder, address: InetSocketAddress, channel: DatagramChannel): Unit =
    if (_myId == holder.senderId) _myId = Peer.Id(Random.nextLong, Random.nextLong) //Just in case

    val foundPeer = peerImpls.get(holder.senderId)

    val (newLatencyX2, needPeer) = holder.message match
      case announce: MulticastAnnounce =>
        if (foundPeer.isEmpty && announce.roomName == _roomName && announce.cookie == MulticastAnnounce.cookie)
          send(Ping(_roomName, 0), address, channel)
          putPingTime(holder.senderId, System.currentTimeMillis)
        (0, foundPeer.nonEmpty)
      case ping: Ping =>
        send(Pong(holder.senderId), address, channel)
        (ping.latency * 2, ping.roomName == _roomName && ping.cookie == Ping.cookie)
      case pong: Pong => getPingWaitTime(holder.senderId).fold(0, false)((_, pong.receiverId == myId))
      case Bye => (0, false)
      case _ => (0, foundPeer.nonEmpty)

    def latency = (newLatencyX2 :: foundPeer.map(_.latency).toList)
      .filter(_ > 0)
      .reduceOption((newX2, found) => (found * 6 + newX2) / 8)
      .getOrElse(0)

    val peer = foundPeer match
      case None if needPeer => Some(PeerImpl(holder.senderId, latency, address, channel))
      case Some(peer) if needPeer => peer.update(latency, address, channel); Some(peer)
      case Some(peer) if !needPeer => peer.kill(); None
      case _ => None

    for
      peer <- peer
      handler <- getMessageHandlers(holder.message.getClass)
    do
      try handler.asInstanceOf[MessageHandler[Message]].onReceive(holder.message, peer)
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
      peer <- peerImpls.values
      if peer.channel == socket.channel
    do
      peer.kill()

    val data = MulticastAnnounce(_roomName).encode
    sockets.foreach(_.channel.send(data.rewind, multicastAddress))

  protected def onHasActivePeerChanged(): Unit =
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
