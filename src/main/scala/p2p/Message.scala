package p2p

import byte_codec.Discriminator as Type

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.util.concurrent.TimeUnit
import scala.collection.mutable


sealed trait Message extends Product:
  def senderId: Peer.Id


object Message:

  object Ping:
    val cookie = 0x4777b31c02b707b5L

  @Type(100) case class Ping(senderId: Peer.Id, cookie: Long) extends Message
  @Type(101) case class Pong(senderId: Peer.Id, receiverId: Peer.Id) extends Message


  private[p2p] def handle(message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =
    val now = System.currentTimeMillis
    val foundPeer = P2p.getPeer(message.senderId)

    val (foundLatency, foundVerified) = foundPeer.fold(0, false)(peer => (peer.latency, peer.verified))
    val (latency, verified) = message match
      case ping: Ping => (foundLatency, foundVerified && ping.cookie == Ping.cookie)
      case pong: Pong => P2p.pingTime.remove(message.senderId)
        .map(pingTime => (now - pingTime).toInt)
        .filter(_ < P2p.PingWaitTime)
        .fold(foundLatency, false)(waitTime => ((foundLatency + waitTime) / 3, pong.receiverId == P2p.myId))    //current latency = waitTime / 2
      case _ => (foundLatency, foundVerified)

    val peer = Peer(message.senderId, now, latency, verified, address, channel)
    P2p.updatePeer(peer)

    message match

      case ping: Ping =>
        if (ping.cookie == Ping.cookie)
          P2p.send(Pong(P2p.myId, ping.senderId), peer)

      case _ => ()
