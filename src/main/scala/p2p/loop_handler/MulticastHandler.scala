package p2p.loop_handler

import p2p.{P2p, Peer}

import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.{Future, TimeUnit}
import java.util.concurrent.TimeUnit.MILLISECONDS
import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*


private[p2p] class MulticastHandler:

  private inline val PeriodSmall = 2_000
  private inline val PeriodBig = 20_000

  private val multicastIp = Inet4Address.ofLiteral("239.227.162.194")
  private val multicastPort = 42815
  private var sockets: List[InterfaceSocket] = Nil

  private val runnable: Runnable = () => 
    val oldSockets = sockets.map(socket => socket.interface -> socket).to(mutable.Map)
    sockets = for
      interface <- NetworkInterface.getNetworkInterfaces.asScala.toList
      if interface.isUp && !interface.isLoopback && !interface.isVirtual
    yield
      oldSockets.remove(interface) getOrElse InterfaceSocket(interface)
    oldSockets.values.foreach(_.channel.close())

  private var future: Option[Future[?]] = None
  P2p.hasActivePeers.subscribe: hasActivePeers =>
    future.foreach(_.cancel(false))
    future = Some:
      if (hasActivePeers) P2p.scheduler.scheduleWithFixedDelay(runnable, PeriodBig, PeriodBig, MILLISECONDS)
      else P2p.scheduler.scheduleWithFixedDelay(runnable, 0, PeriodSmall, MILLISECONDS)

  def send(data: ByteBuffer): Unit =
    sockets.foreach(_.channel.send(data, InetSocketAddress(multicastIp, multicastPort)))

  private class InterfaceSocket(val interface: NetworkInterface) extends P2p.Socket(
    DatagramChannel
      .open(StandardProtocolFamily.INET)
      .bind(InetSocketAddress("0.0.0.0", multicastPort))
      .setOption(StandardSocketOptions.IP_MULTICAST_IF, interface)
  ):
    channel.join(multicastIp, interface)
