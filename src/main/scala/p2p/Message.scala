package p2p

import byte_codec.Discriminator as Type

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.util.concurrent.TimeUnit
import scala.collection.mutable


sealed trait Message extends Product:
  def id: Peer.Id


object Message:

  object Ping:
    val cookie = 0x4777b31c02b707b5L

  @Type(100) case class Ping(id: Peer.Id, cookie: Long) extends Message
  @Type(101) case class Pong(id: Peer.Id, senderId: Peer.Id) extends Message


  private inline val PingWaitTime = 8000

  private val pingTime = mutable.Map.empty[Peer.Id, Long]
  private val pingTimeCleaner: Runnable = () =>
    val minTime = System.currentTimeMillis - PingWaitTime
    pingTime.filterInPlace { case (_, time) => time > minTime }
  P2p.scheduler.scheduleWithFixedDelay(pingTimeCleaner, 10, 10, TimeUnit.MINUTES)

  private[p2p] def handle(message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =
    val now = System.currentTimeMillis
    val foundPeer = P2p.peers.get(message.id)

    val (foundLatency, foundVerified) = foundPeer.fold(0, false)(peer => (peer.latency, peer.verified))
    val (latency, verified) = message match
      case ping: Ping => (foundLatency, foundVerified && ping.cookie == Ping.cookie)
      case pong: Pong => pingTime.remove(message.id)
        .map(pingTime => (now - pingTime).toInt)
        .filter(_ < PingWaitTime)
        .fold(foundLatency, false)(waitTime => ((foundLatency + waitTime) / 3, pong.senderId == P2p.myId))    //current latency = waitTime / 2
      case _ => (foundLatency, foundVerified)

    val peer = Peer(message.id, now, latency, verified, address, channel)
    P2p.peers(message.id) = peer

    message match

      case ping: Ping =>
        if (ping.cookie == Ping.cookie)
          P2p.send(Pong(P2p.myId, ping.id), peer)

      case _ => ()
