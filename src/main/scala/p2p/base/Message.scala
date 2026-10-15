package p2p.base

import byte_codec.ByteCodec.{CompactBytes, CompactUInt, CompactULong}
import byte_codec.{ByteCodec, Discriminator as Msg}
import core.TaskQueue
import p2p.base.Message.Counter.toBoolean
import torrent.Hash

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.collection.mutable
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.{Random, Try}


sealed trait Message

object Message:

  sealed trait P2pInner extends Message

  @Msg(-1) case class MulticastAnnounce(roomName: String, cookie: Long = MulticastAnnounce.cookie) extends P2pInner
  @Msg(-2) case class Ping(
    id: Ping.Id,
    latency: Int,
    roomName: String = P2p._roomName,
    cookie: Long = Ping.cookie,
  ) extends P2pInner
  @Msg(-3) case class Pong(pingId: Ping.Id) extends P2pInner
  @Msg(-4) case object Bye extends P2pInner

  @Msg(100) case class PlayerState(
    hash: Hash,
    fileIndex: CompactUInt,
    fileCounter: Counter,
    time: CompactULong    = 0,
    speedX10: Byte        = 10,
    title: String         = "",
    seekCounter: Counter  = 0,
    speedCounter: Counter = 0,
    titleCounter: Counter = 0,
    pauseCounter: Counter = 0,
  ) extends Message:
    inline def pause: Boolean = pauseCounter.toBoolean

  @Msg(101) case class TorrentMagnetRequest(hash: Hash) extends Message
  @Msg(102) case class TorrentMagnetResponse(hash: Hash, magnet: CompactBytes) extends Message

  @Msg(103) case class PhoneData(index: CompactUInt, data: Array[Byte], enableEncoderCounter: Counter) extends Message
  @Msg(104) case class PhoneMetadata(packetLoss: CompactUInt, encoderEnableCounter: Counter) extends Message

  object MulticastAnnounce:
    val cookie = 0xa0763626f5735cd2L
  object Ping:
    val cookie = 0x4777b31c02b707b5L
    opaque type Id = Long
    object Id:
      def random: Id = Random.nextLong
      implicit def byteCodec: ByteCodec[Id] = ByteCodec.long

  opaque type Counter = Int
  object Counter:
    inline def apply(bool: Boolean): Counter = if (bool) 1 else 0
    inline def zero: Counter = 0

    extension (counter: Counter)
      def inc: Counter = if (counter == 255) 2 else counter + 1
      def set(bool: Boolean): Counter = if (counter.toBoolean == bool) counter else counter.inc
      inline def toBoolean: Boolean = (counter & 1) == 1
      infix def compare(other: Counter): Int = (counter >= 2, other >= 2) match
        case (false, false) => 0
        case (false, true) => -1
        case (true, false) => 1
        case (true, true) => (counter - other).toByte.toInt
      inline infix def merge(other: Counter): Counter =
        if (counter.compare(other) >= 0) counter else other

    implicit val byteCodec: ByteCodec[Counter] = new ByteCodec:
      def encode(value: Counter, output: ByteArrayOutputStream): Unit = output.write(value)
      def decode(input: ByteArrayInputStream): Counter = input.read


  trait Handler[M <: Message]:
    def onReceive(message: M, peer: Peer): Unit
  private[p2p] case class Holder(senderId: Peer.Id, message: Message)

  private val messageHandlers = mutable.Map[Class[? <: Message], List[Handler[? <: Message]]]()
  private[p2p] def getHandlers(cls: Class[? <: Message]): List[Handler[? <: Message]] =
    messageHandlers.getOrElse(cls, Nil)

  extension (message: Message) private[p2p] def encode: ByteBuffer =
    ByteBuffer.wrap(ByteCodec.encode(Holder(P2p.myId, message)))
  private[p2p] def decode(data: ByteBuffer) = Try:
    ByteCodec.decode[Holder](data.array, data.position, data.remaining)
  private[p2p] def send(message: Message, address: InetSocketAddress, channel: DatagramChannel): Unit =
    channel.send(message.encode, address)


  private[p2p] trait Trait:

    def addMessageHandler[M <: Message](handler: Handler[M])(using tag: ClassTag[M]): Handler[M] =
      TaskQueue.threadSafe:
        messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
          listOpt => Some(handler :: listOpt.getOrElse(Nil))
      handler

    def removeMessageHandler[M <: Message](handler: Handler[M])(using tag: ClassTag[M]): Unit =
      TaskQueue.threadSafe:
        messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
          _.map(_.filter(_ != handler)).filter(_.nonEmpty)
