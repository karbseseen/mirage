package p2p

import byte_codec.Discriminator as Type

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel


sealed trait Message extends Product:
  def senderId: Peer.Id


object Message:

  @Type(100) case class Ping(senderId: Peer.Id, roomName: String) extends Message
  @Type(101) case class Pong(senderId: Peer.Id, receiverId: Peer.Id) extends Message


  private[p2p] def handle(p2p: P2p, message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =

    val now = System.currentTimeMillis
    val foundPeer = p2p.getPeer(message.senderId)

    val (foundLatency, foundVerified) = foundPeer.fold(0, false)(peer => (peer.latency, peer.verified))
    val (latency, verified) = message match
      case ping: Ping => (foundLatency, foundVerified && ping.roomName == p2p.roomName)
      case pong: Pong => p2p.pingTime.remove(message.senderId)
        .map(pingTime => (now - pingTime).toInt)
        .filter(_ < P2p.PingWaitTime)
        .fold(foundLatency, false)(waitTime => ((foundLatency + waitTime) / 3, pong.receiverId == p2p.myId))    //current latency = waitTime / 2
      case _ => (foundLatency, foundVerified)

    val peer = Peer(message.senderId, now, latency, verified, address, channel)
    p2p.updatePeer(peer)

    message match

      case ping: Ping =>
        if (ping.roomName == p2p.roomName)
          p2p.send(Pong(p2p.myId, ping.senderId), peer)

      case _ => ()
