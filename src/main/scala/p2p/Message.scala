package p2p

import byte_codec.Discriminator as Type


sealed trait Message extends Product:
  def senderId: Peer.Id


trait MessageHandler[M <: Message]:
  def onReceive(message: M, peer: Peer, p2p: P2p): Unit


object Message:

  @Type(99) case class MulticastAnnounce(senderId: Peer.Id, roomName: String, cookie: Long) extends Message
  @Type(98) case class Ping(senderId: Peer.Id, roomName: String, cookie: Long, latency: Int) extends Message
  @Type(97) case class Pong(senderId: Peer.Id, receiverId: Peer.Id) extends Message
  @Type(96) case class Bye(senderId: Peer.Id) extends Message
  @Type(100) case class Test(senderId: Peer.Id, text: String) extends Message

  object Ping:
    val cookie = 0x4777b31c02b707b5L
