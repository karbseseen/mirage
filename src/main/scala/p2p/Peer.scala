package p2p

import byte_codec.ByteCodec
import p2p.Message.Ping
import p2p.Peers.*

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.collection.mutable


sealed trait Peer:
  val id: Peer.Id
  val p2p: P2p
  def lastSeen: Long
  def latency: Int
  def address: InetSocketAddress
  protected def channel: DatagramChannel

  def send(message: Message): Unit = Peer.send(message, address, channel)


object Peer:
  case class Id(part1: Long, part2: Long)

  private[p2p] def send(message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =
    val data = ByteBuffer.wrap(ByteCodec.encode(message))
    channel.send(data, address)


trait Peers private[p2p] extends Tasks:

  protected val pingTime = mutable.Map.empty[Peer.Id, Long]
  private val _peers = mutable.Map.empty[Peer.Id, PeerImpl]
  protected def peerImpls: collection.Map[Peer.Id, PeerImpl] = _peers
  def peers: collection.Map[Peer.Id, Peer] = _peers

  private var _activePeerNum = 0
  private def activePeerNum: Int = _activePeerNum
  private def activePeerNum_=(value: Int): Unit =
    val hadActive = _activePeerNum > 0
    val hasActive = value > 0
    if (hadActive != hasActive)
      onHasActivePeerChanged(hasActive)
    _activePeerNum = value
  protected def onHasActivePeerChanged(hasActivePeers: Boolean): Unit

  schedulePeriodic(5000 * 60, 5000 * 60):
    val minTime = System.currentTimeMillis - PingWaitTime
    pingTime.filterInPlace { case (_, time) => time > minTime }


object Peers:

  private inline val ActiveTime = 5_000
  private inline val LiveTime = 10_000
  private inline val PingPeriod = 1_500
  private[p2p] inline val PingWaitTime = 5_000

  private[p2p] class PeerImpl(
    val id: Peer.Id,
    val p2p: P2p,
    private var _lastSeen: Long,
    private var _latency: Int,
    private var _address: InetSocketAddress,
    private var _channel: DatagramChannel,
  ) extends Peer:

    private def peers: Peers = p2p
    private var active = true

    peers._peers(id) = this
    peers.activePeerNum += 1

    def lastSeen: Long = _lastSeen
    def latency: Int = _latency
    def address: InetSocketAddress = _address
    def channel: DatagramChannel = _channel

    def update(
      lastSeen: Long,
      latency: Int,
      address: InetSocketAddress,
      channel: DatagramChannel,
    ): Unit =
      this._lastSeen = lastSeen
      this._latency  = latency
      this._address  = address
      this._channel  = channel
      pingTask.cancel()
      pingTask = newPingTask
      lifecycleTask.cancel()
      lifecycleTask = newLifecycleTask


    def cancel(): Unit =
      peers._peers -= id
      if (active) peers.activePeerNum -= 1
      pingTask.cancel()
      lifecycleTask.cancel()


    private var pingTask = newPingTask
    private def newPingTask = p2p.schedulePeriodicAt(lastSeen + PingPeriod, PingPeriod):
      send(Ping(p2p.myId, p2p.roomName, Ping.cookie, latency))
      peers.pingTime(id) = System.currentTimeMillis

    private var lifecycleTask: Task = newLifecycleTask
    private def newLifecycleTask = p2p.scheduleSingleAt(lastSeen + ActiveTime):
      active = false
      peers.activePeerNum -= 1
      lifecycleTask = p2p.scheduleSingleAt(lastSeen + LiveTime):
        peers._peers -= id
        pingTask.cancel()
