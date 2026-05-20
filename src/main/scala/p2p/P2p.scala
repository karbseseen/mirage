package p2p

import core.main.MainApp

import java.net.{InetSocketAddress, StandardProtocolFamily}
import java.nio.channels.{DatagramChannel, SelectionKey, Selector}
import scala.collection.mutable
import scala.util.Random


private object P2p:

  inline val PeerLiveTime         = 10_000
  inline val PeerActiveTime       = 4_000
  inline val PeerPingTime         = 1_500
  inline val UndiscoveredPingTime = 150

  private val selector: Selector = Selector.open
  val socketV4 = P2pSocket(selector, isV6 = false)
  val socketV6 = P2pSocket(selector, isV6 = true)
  MainApp.shutdownHook:
    socketV4.channel.close()
    socketV6.channel.close()

  val myId: Id = P2p.Id.random
  var peers = mutable.Map.empty[P2p.Id, Peer]
  var undiscovered = mutable.Map.empty[P2p.Id, Undiscovered]

  private val localAddressHandler = new LocalAddressHandler
  def localAddresses: List[InetSocketAddress] = localAddressHandler.value

  private val handlers = List(localAddressHandler, new StunHandler, new DhtHandler)

  private val thread = Thread: () =>
    val now = System.currentTimeMillis
    val hasActivePeers = peers.values.exists(now - _.lastSeen < PeerActiveTime)
    handlers.foreach(_.handle(now, hasActivePeers))
    selector.select()
    ()


  thread.setDaemon(true)
  thread.start()


  case class Id(data0: Long, data1: Long)
  object Id:
    def random = Id(Random.nextLong, Random.nextLong)
  
  case class Undiscovered(id: P2p.Id, localAddresses: List[InetSocketAddress], remoteAddresses: List[InetSocketAddress]):
    var lastTime = 0L
    var tryCount = 0


private class P2pSocket(selector: Selector, isV6: Boolean):

  val channel: DatagramChannel = DatagramChannel
    .open(if (isV6) StandardProtocolFamily.INET6 else StandardProtocolFamily.INET)
    .bind(InetSocketAddress(if (isV6) "::" else "0.0.0.0", 0))
  channel.configureBlocking(false)
  channel.register(selector, SelectionKey.OP_READ)

  var remoteAddress: Option[InetSocketAddress] = None
  var remoteAddressTime = 0L


private class Peer(val id: P2p.Id, var address: InetSocketAddress, var lastSeen: Long)
