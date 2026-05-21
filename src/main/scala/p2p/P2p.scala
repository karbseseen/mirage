package p2p

import byte_codec.ByteCodec
import p2p.Message.Pong

import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, SelectionKey, Selector}
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.{Executors, Future, ScheduledExecutorService}
import java.util.function.Consumer
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.{Random, Try}


object P2p:

  private inline val PeerLiveTime         = 10_000
  private inline val PeerActiveTime       = 4_000
  private inline val PeerPingPeriod       = 1_500

  private inline val InterfaceUpdatePeriodSmall = 3_000
  private inline val InterfaceUpdatePeriodBig   = 20_000

  private val multicastIp = Inet4Address.ofLiteral("239.227.162.194")
  private val multicastPort = 42815
  private var sockets: List[MulticastSocket] = Nil
  private val buffer = ByteBuffer.allocate(2048)

  private val selector: Selector = Selector.open
  private var hasActivePeers = false

  val myId: Peer.Id = Peer.Id(Random.nextLong, Random.nextLong)
  var peers = mutable.Map.empty[Peer.Id, Peer]
  val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor


  private val interfaceUpdater: Runnable = () =>
    val oldSockets = sockets.map(socket => socket.interface -> socket).to(mutable.Map)
    sockets = for
      interface <- NetworkInterface.getNetworkInterfaces.asScala.toList
      if interface.isUp && !interface.isLoopback && !interface.isVirtual
    yield
      oldSockets.remove(interface) getOrElse new MulticastSocket(interface)
    oldSockets.values.foreach(_.channel.close())

  private var future: Option[Future[?]] = None
  private def onHasActivePeersChanged(): Unit =
    future.foreach(_.cancel(false))
    future = Some:
      if (hasActivePeers)
        scheduler.scheduleWithFixedDelay(interfaceUpdater, InterfaceUpdatePeriodBig, InterfaceUpdatePeriodBig, MILLISECONDS)
      else
        scheduler.scheduleWithFixedDelay(interfaceUpdater, 0, InterfaceUpdatePeriodSmall, MILLISECONDS)


  private val receiver: Runnable = () =>
    selector.select(
      key => key.channel match
        case channel: DatagramChannel if key.isReadable =>
          val address = channel.receive(buffer.rewind)
          for
            address <- Some(address).collect { case inet: InetSocketAddress => inet }
            message <- Try(ByteCodec.decode[Message](buffer.array))
          do
            Message.handle(message, address, channel)
        case _ => (),
      1000,
    )

  scheduler.scheduleWithFixedDelay(receiver, 10, 0, MILLISECONDS)


  def send(message: Message, peer: Peer): Unit =
    val data = ByteBuffer.wrap(ByteCodec.encode(message))
    peer.channel.send(data, peer.address)

  def sendToAll(data: ByteBuffer): Unit =
    sockets.foreach(_.channel.send(data, InetSocketAddress(multicastIp, multicastPort)))

  private class MulticastSocket(val interface: NetworkInterface):
    val channel: DatagramChannel = DatagramChannel
      .open(StandardProtocolFamily.INET)
      .bind(InetSocketAddress("0.0.0.0", multicastPort))
      .setOption(StandardSocketOptions.IP_MULTICAST_IF, interface)
    channel.configureBlocking(false)
    channel.join(multicastIp, interface)
    channel.register(selector, SelectionKey.OP_READ)


class Peer private[p2p] (
  val id: Peer.Id,
  val lastSeen: Long,
  val latency: Int,
  val verified: Boolean,
  val address: InetSocketAddress,
  private[p2p] val channel: DatagramChannel,
)

object Peer:
  case class Id(part1: Long, part2: Long)
