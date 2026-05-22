package p2p

import p2p.Message.Ping
import p2p.Peers.*

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import scala.collection.mutable


abstract class Peer private[p2p] (
  val id: Peer.Id,
  val lastSeen: Long,
  val latency: Int,
  val address: InetSocketAddress,
  val channel: DatagramChannel,
  val p2p: P2p,
)

object Peer:
  case class Id(part1: Long, part2: Long)


trait Peers private[p2p] extends Tasks:

  protected val peers = mutable.Map.empty[Peer.Id, PeerImpl]
  protected val pingTime = mutable.Map.empty[Peer.Id, Long]

  private var _activePeerNum = 0
  private def activePeerNum: Int = _activePeerNum
  private def activePeerNum_=(value: Int): Unit =
    val hadActive = _activePeerNum > 0
    val hasActive = value > 0
    if (hadActive != hasActive)
      onHasActivePeerChanged(hasActive)
    _activePeerNum = value
  protected def onHasActivePeerChanged(hadActivePeers: Boolean): Unit

  schedulePeriodic(5000 * 60, 5000 * 60):
    val minTime = System.currentTimeMillis - PingWaitTime
    pingTime.filterInPlace { case (_, time) => time > minTime }

  def getPeers: Iterable[Peer] = peers.values


  protected trait PeerImpl extends Peer:
    def cancel(): Unit =
      task.cancel()
      if (active) activePeerNum -= 1

    private var active = true
    activePeerNum += 1

    private var task: Task = pingTask
    private def pingTask = scheduleSingleAt(lastSeen + PingPeriod):
      p2p.send(Ping(p2p.myId, p2p.roomName, Ping.cookie), this)
      pingTime(id) = System.currentTimeMillis
      task = inactiveTask
    private def inactiveTask = p2p.scheduleSingleAt(lastSeen + PingPeriod):
      active = false
      activePeerNum -= 1
      task = dieTask
    private def dieTask = p2p.scheduleSingleAt(lastSeen + PingPeriod):
      peers -= id


object Peers:
  private inline val ActiveTime         = 5_000
  private inline val LiveTime           = 10_000
  private inline val PingPeriod         = 1_500
  private[p2p] inline val PingWaitTime  = 5_000
