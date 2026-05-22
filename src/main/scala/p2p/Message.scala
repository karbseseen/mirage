package p2p

import byte_codec.Discriminator as Type


sealed trait Message extends Product:
  def senderId: Peer.Id


trait MessageHandler[M <: Message]:
  def onReceive(message: M, peer: Peer, p2p: P2p): Unit


object Message:

  @Type(100) case class MulticastAnnounce(senderId: Peer.Id, roomName: String, cookie: Long) extends Message
  @Type(101) case class Ping(senderId: Peer.Id, roomName: String, cookie: Long) extends Message
  @Type(102) case class Pong(senderId: Peer.Id, receiverId: Peer.Id) extends Message

  object Ping:
    val cookie = 0x4777b31c02b707b5L
