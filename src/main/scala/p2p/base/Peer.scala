package p2p.base

import core.TaskQueue.*
import p2p.base.Message.Ping
import p2p.base.Peers.*

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import scala.collection.immutable.Queue
import scala.collection.mutable


sealed trait Peer:
  val id: Peer.Id
  def lastSeen: Long
  def latency: Int
  def address: InetSocketAddress
  protected def channel: DatagramChannel

  def send(message: Message): Unit = P2p.send(message, address, channel)


object Peer:
  case class Id(part1: Long, part2: Long)


trait PeerListener:
  def onPeer(peer: Peer, added: Boolean): Unit


trait Peers private[p2p] extends Messages:

  @volatile private var _peers = Map.empty[Peer.Id, PeerImpl]
  protected def peerImpls: Map[Peer.Id, PeerImpl] = _peers
  def peers: Map[Peer.Id, Peer] = _peers

  protected def hasActivePeers: Boolean = _activePeerNum > 0
  private var _activePeerNum = 0
  private def activePeerNum: Int = _activePeerNum
  private def activePeerNum_=(value: Int): Unit =
    val hadActivePeers = hasActivePeers
    _activePeerNum = value
    if (hadActivePeers != hasActivePeers) onHasActivePeerChanged()
  protected def onHasActivePeerChanged(): Unit

  private val pingTime = mutable.Map.empty[Peer.Id, Queue[Long]]
  protected def putPingTime(id: Peer.Id, now: Long): Unit =
    pingTime.updateWith(id) { queue => Some(queue.fold(Queue(now))(_.enqueue(now))) }
  protected def getPingWaitTime(id: Peer.Id): Option[Int] =
    val now = System.currentTimeMillis
    var result = Option.empty[Int]
    pingTime.updateWith(id):
      _
        .map(_.dropWhile(now - _ > PingWaitTime))
        .flatMap(_.dequeueOption)
        .flatMap:
          case (value, queue) =>
            result = Some((now - value).toInt)
            Option.when(queue.nonEmpty)(queue)
    result

  private val listeners = mutable.Buffer.empty[PeerListener]
  def addPeerListener   (listener: PeerListener): Unit = threadSafe(listeners += listener)
  def removePeerListener(listener: PeerListener): Unit = threadSafe(listeners -= listener)

  def sendToAll(message: Message): Unit =
    val data = message.encode
    _peers.values.foreach(peer => peer.channel.send(data, peer.address))


object Peers:

  private inline val ActiveTime = 5_000
  private inline val LiveTime = 10_000
  private inline val MinPingPeriod = 1_500
  private inline val MaxPingPeriod = 15_000
  private[p2p] inline val PingWaitTime = 5_000

  private[p2p] class PeerImpl(
    val id: Peer.Id,
    private var _latency: Int,
    private var _address: InetSocketAddress,
    private var _channel: DatagramChannel,
  ) extends Peer:

    private def peers: Peers = P2p
    private var active = true
    private var lastPingTime = 0L

    peers.activePeerNum += 1
    peers._peers = peers._peers.updatedWith(id): oldPeer =>
      oldPeer.foreach(_.kill())
      Some(this)
    peers.listeners.foreach(_.onPeer(this, added = true))

    private var _lastSeen: Long = System.currentTimeMillis
    def lastSeen: Long = _lastSeen
    def latency: Int = _latency
    def address: InetSocketAddress = _address
    def channel: DatagramChannel = _channel

    def update(
      latency: Int,
      address: InetSocketAddress,
      channel: DatagramChannel,
    ): Unit =
      val now = System.currentTimeMillis

      _lastSeen = now
      _latency  = latency
      _address  = address
      _channel  = channel

      lifecycleTask.cancel()
      lifecycleTask = newLifecycleTask
      if (now - lastPingTime < MaxPingPeriod - MinPingPeriod)
        pingTask.cancel()
        pingTask = newPingTask


    def kill(): Unit =
      if (active)
        active = false
        peers.activePeerNum -= 1
      lifecycleTask.cancel()
      die()

    private def die(): Unit =
      peers._peers -= id
      pingTask.cancel()
      peers.listeners.foreach(_.onPeer(this, added = false))


    private var pingTask = newPingTask
    private def newPingTask = schedulePeriodicAt(lastSeen + MinPingPeriod, MinPingPeriod):
      val now = System.currentTimeMillis
      send(Ping(P2p._roomName, latency))
      peers.putPingTime(id, now)
      lastPingTime = now

    private var lifecycleTask: Task = newLifecycleTask
    private def newLifecycleTask = scheduleSingleAt(lastSeen + ActiveTime):
      active = false
      peers.activePeerNum -= 1
      lifecycleTask = scheduleSingleAt(lastSeen + LiveTime)(die())
