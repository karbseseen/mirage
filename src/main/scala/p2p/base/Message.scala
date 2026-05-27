package p2p.base

import byte_codec.ByteCodec.{CompactBytes, CompactUInt, CompactULong}
import byte_codec.{ByteCodec, Discriminator as Msg}
import p2p.RoomSync
import torrent.Hash

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.reflect.ClassTag


sealed trait Message

trait MessageHandler[M <: Message]:
  def onReceive(message: M, peer: Peer): Unit

object Message:

  @Msg(-1) case class MulticastAnnounce(roomName: String, cookie: Long = MulticastAnnounce.cookie) extends Message
  @Msg(-2) case class Ping(roomName: String, latency: Int, cookie: Long = Ping.cookie) extends Message
  @Msg(-3) case class Pong(receiverId: Peer.Id) extends Message
  @Msg(-4) case object Bye extends Message

  @Msg(100) case class PlayerState(
    hash: Hash,
    fileIndex: CompactUInt,
    fileCounter: Byte,
    time: CompactULong  = 0,
    speedX10: Byte      = 10,
    seekCounter: Byte   = 0,
    speedCounter: Byte  = 0,
    pauseCounter: Byte  = 0,
  ) extends Message:
    def pause: Boolean = (pauseCounter & 1) == 1
  object PlayerState:
    def count(counter: Byte): Byte =
      if (counter == -1) 2
      else (counter + 1).toByte

  @Msg(101) case class TorrentMagnetRequest(hash: Hash) extends Message
  @Msg(102) case class TorrentMagnetResponse(hash: Hash, magnet: CompactBytes) extends Message

  object MulticastAnnounce:
    val cookie = 0xa0763626f5735cd2L
  object Ping:
    val cookie = 0x4777b31c02b707b5L

trait Messages private[p2p] extends Tasks:
  def myId: Peer.Id

  protected case class MessageHolder(senderId: Peer.Id, message: Message)

  extension(message: Message) protected def encode: ByteBuffer =
    ByteBuffer.wrap(ByteCodec.encode(MessageHolder(myId, message)))

  extension(data: ByteBuffer) protected def decode: MessageHolder =
    ByteCodec.decode[MessageHolder](data.array, 0, data.position)

  private[p2p] def send(message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =
    channel.send(message.encode, address)


  private val messageHandlers = mutable.Map.empty[Class[? <: Message], List[MessageHandler[? <: Message]]]
  protected def getMessageHandlers(cls: Class[? <: Message]): List[MessageHandler[? <: Message]] =
    messageHandlers.getOrElse(cls, Nil)

  def addMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    threadSafe:
      messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
        listOpt => Some(handler :: listOpt.getOrElse(Nil))

  def removeMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    threadSafe:
      messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
        _.map(_.filter(_ != handler)).filter(_.nonEmpty)
