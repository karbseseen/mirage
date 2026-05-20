package p2p

import byte_codec.Discriminator as Type

import java.net.InetSocketAddress


sealed trait Message extends Product:
  def id: P2p.Id


object Message:

  type Source = Peer | Option[InetSocketAddress]

  @Type(100) case class Ping(id: P2p.Id, time: Long) extends Message
  @Type(101) case class Pong(id: P2p.Id, time: Long) extends Message

  def handle(message: Message, source: Source, now: Long): Unit = message match

    case message: Ping => P2p.send(Pong(P2p.myId, now), getAddress(source))

    case message: Pong => source match
      case address: Option[InetSocketAddress] =>
        P2p.peers(message.id) = Peer(message.id, address, now)
      case _ => ()

  
  private def getAddress(source: Source) =
    source match
      case peer: Peer => peer.address
      case address: Option[InetSocketAddress] => address
