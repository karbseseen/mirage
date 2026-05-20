package p2p

import byte_codec.ByteCodec
import config.Config
import core.main.MainApp
import javafx.beans.property.SimpleObjectProperty

import java.net.{Inet4Address, Inet6Address, InetSocketAddress, StandardProtocolFamily}
import java.nio.ByteBuffer
import java.nio.channels.spi.AbstractSelectableChannel
import java.nio.channels.{DatagramChannel, SelectionKey, Selector}
import java.util.function.Consumer
import scala.collection.mutable
import scala.util.{Random, Try}


val roomName = Config.StringProp(P2p, "roomName", "")

private object P2p:

  inline val PeerLiveTime         = 10_000
  inline val PeerActiveTime       = 4_000
  inline val PeerPingTime         = 1_500
  inline val UndiscoveredPingTime = 150

  val selector: Selector = Selector.open
  val v4RemoteSocket: RemoteSocket = RemoteSocket:
    DatagramChannel.open(StandardProtocolFamily.INET).bind(InetSocketAddress("0.0.0.0", 0))
  val v6RemoteSocket: RemoteSocket = RemoteSocket:
    DatagramChannel.open(StandardProtocolFamily.INET6).bind(InetSocketAddress("::", 0))

  val myId: Id = P2p.Id.random
  var peers = mutable.Map.empty[P2p.Id, Peer]
  var undiscovered = mutable.Map.empty[P2p.Id, Undiscovered]
  roomName.addListener: _ =>
    runLater:
      peers.clear()
      undiscovered.clear()

  private val multicastHandler = new MulticastHandler
  private val handlers = List(multicastHandler, new StunHandler, new DhtHandler)
  private val buffer = ByteBuffer.allocate(2048)

  private val extraTasks = mutable.Buffer.empty[() => Unit]
  def runLater(task: => Unit): Unit =
    synchronized { extraTasks += (() => task) }
    selector.wakeup()

  private def loop(): Unit =

    synchronized:
      extraTasks.foreach(_())
      extraTasks.clear()

    val now = System.currentTimeMillis
    val activePeers = peers.values.filter(now - _.lastSeen < PeerActiveTime).toList
    handlers.foreach(_.loop(now, activePeers))

    def onSelect(channel: DatagramChannel): Unit =
      val address = channel.receive(buffer.rewind)
      for message <- Try(ByteCodec.decode[Message](buffer.array)) do
        val peer = peers.get(message.id)
        peer.foreach(_.lastSeen = now)
        val source: Message.Source = peer getOrElse
          Option.when(channel.eq(v4RemoteSocket.channel) || channel.eq(v6RemoteSocket))(address).collect:
            case inet: InetSocketAddress => inet
        Message.handle(message, source, now)
    
    selector.select(
      key => key.channel match
        case channel: DatagramChannel if key.isReadable => onSelect(channel)
        case _ => (),
      if (undiscovered.nonEmpty) 100 else 750,
    )

  private val thread = Thread(() => while (true) loop())
  thread.setDaemon(true)
  thread.start()


  def send(message: Message, peer: Peer): Unit = send(message, peer.address)
  def send(message: Message, address: Option[InetSocketAddress]): Unit =
    val buffer = ByteBuffer.wrap(ByteCodec.encode(message))
    address match
      case Some(address) => address.getAddress match
        case Some(_: Inet4Address) => v4RemoteSocket.channel.send(buffer, address)
        case Some(_: Inet6Address) => v6RemoteSocket.channel.send(buffer, address)
      case None => multicastHandler.send(buffer)
  

  class Socket(val channel: DatagramChannel):
    channel.configureBlocking(false)
    channel.register(selector, SelectionKey.OP_READ)

  class RemoteSocket(val channel: DatagramChannel) extends Socket(channel):
    val address = SimpleObjectProperty(this, "address", Option.empty[InetSocketAddress])
    var addressTime = 0L

  case class Id(data0: Long, data1: Long)
  object Id:
    def random = Id(Random.nextLong, Random.nextLong)
  
  case class Undiscovered(id: P2p.Id, remoteAddresses: List[InetSocketAddress]):
    var lastTime = 0L
    var tryCount = 0



private class Peer(val id: P2p.Id, var address: Option[InetSocketAddress], var lastSeen: Long)
