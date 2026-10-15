package p2p.base

import core.TaskQueue.*
import p2p.base.Message.{Ping, Pong}
import util.also

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import scala.collection.mutable
import scala.util.Random


class Peer private[p2p] (
  val id: Peer.Id,
  private var _latency: Float,
  private var _address: InetSocketAddress,
  private var _channel: DatagramChannel,
):
  import Peer.*

  def latency: Int = _latency.toInt
  def address: InetSocketAddress = _address
  private[p2p] def channel: DatagramChannel = _channel

  def send(message: Message): Unit = Message.send(message, address, channel)

  private var
    lastSeen,
    lastPingTime,
    lastPongTime,
    lastCustomReceiveTime = System.currentTimeMillis

  private var _active = false
  private def active = _active
  private def active_=(value: Boolean): Unit =
    if (_active != value)
      _active = value
      if (value)
        activeNum += 1
        if (activeNum == 1) P2p.onHasActivePeerChanged(true)
      else
        activeNum -= 1
        if (activeNum == 0) P2p.onHasActivePeerChanged(false)

  active = true
  map = map.updatedWith(id): oldPeer =>
    oldPeer.foreach(_.kill())
    Some(this)
  listeners.foreach(_.onPeer(this, added = true))

  private[p2p] def update(
    address: InetSocketAddress,
    channel: DatagramChannel,
    newLatency: Option[Float] = None,
    pongSent: Boolean = false,
    customReceived: Boolean = false,
  ): this.type = this.also: _ =>
    _address = address
    _channel = channel
    newLatency.foreach: newLatency =>
      _latency += (newLatency - _latency) * 0.1f

    lastSeen = System.currentTimeMillis
    if (pongSent) lastPongTime = lastSeen
    if (customReceived) lastCustomReceiveTime = lastSeen

    if (!active)
      active = true
      lifecycleTask.cancel()
      lifecycleTask = newLifecycleTask

  def kill(): Unit =
    active = false
    lifecycleTask.cancel()
    die()

  private def die(): Unit =
    map -= id
    pingTask.cancel()
    listeners.foreach(_.onPeer(this, added = false))

  private var pingTask = newPingTask
  private var pingRandom = 0L
  private def nextPingTime =
    val pingPeriod = if (active) ActivePingPeriod else InactivePingPeriod
    (lastCustomReceiveTime.max(lastPingTime) + (pingPeriod + pingRandom).max(MinPingPeriod))
      .max(lastPongTime + pingPeriod / 2)
      .min(lastPingTime + MaxPingPeriod)
  private def newPingTask: Task = scheduleSingleAt(nextPingTime):
    val now = System.currentTimeMillis
    if (now >= nextPingTime)
      Peer.ping(id, latency, address, channel, now)
      lastPingTime = now
      pingRandom = if (Random.nextBoolean) latency else -latency
    pingTask = newPingTask

  private var lifecycleTask: Task = newLifecycleTask
  private def newLifecycleTask: Task = scheduleSingleAt(lastSeen + ActiveTime):
    val now = System.currentTimeMillis
    if (now >= lastSeen + ActiveTime)
      active = false
      lifecycleTask = scheduleSingleAt(lastSeen + LiveTime)(die())
    else
      lifecycleTask = newLifecycleTask


object Peer:
  case class Id(part1: Long, part2: Long)

  trait Listener:
    def onPeer(peer: Peer, added: Boolean): Unit


  private var activeNum = 0
  @volatile private var map = Map.empty[Peer.Id, Peer]
  private val listeners = mutable.Buffer.empty[Listener]

  private val pingTime = mutable.Map[(Peer.Id, Ping.Id), Long]()

  private[p2p] def ping(
    id: Peer.Id,
    latency: Int,
    address: InetSocketAddress,
    channel: DatagramChannel,
    now: Long = System.currentTimeMillis,
  ): Unit =
    val pingId = Ping.Id.random
    Message.send(Ping(pingId, latency), address, channel)
    pingTime((id, pingId)) = now

  private[p2p] def getPingWaitTime(peerId: Peer.Id, pingId: Ping.Id): Option[Int] =
    val now = System.currentTimeMillis
    pingTime.filterInPlace((_, time) => now - time < PingWaitTime)
    pingTime.remove(peerId, pingId).map(time => (now - time).toInt)


  private[p2p] trait Trait:
    def peers: Map[Peer.Id, Peer] = map

    def addPeerListener(listener: Listener):    Listener = { threadSafe(listeners += listener); listener }
    def removePeerListener(listener: Listener): Listener = { threadSafe(listeners -= listener); listener }

    def sendToAll(message: Message): Unit =
      val data = message.encode
      map.values.foreach(peer => peer.channel.send(data, peer.address))


  private inline val ActiveTime = 5_000
  private inline val LiveTime = 10_000
  private inline val ActivePingPeriod = 2_000
  private inline val InactivePingPeriod = 500
  private inline val MinPingPeriod = 400
  private inline val MaxPingPeriod = 15_000
  private inline val PingWaitTime = 5_000
