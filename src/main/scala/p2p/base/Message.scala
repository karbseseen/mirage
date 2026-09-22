package p2p.base

import byte_codec.ByteCodec.{CompactBytes, CompactUInt, CompactULong}
import byte_codec.{ByteCodec, Discriminator as Msg}
import core.TaskQueue
import p2p.base.Message.Counter.{inc, toBoolean}
import torrent.Hash

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.language.implicitConversions
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
    fileCounter: Counter,
    time: CompactULong    = 0,
    speedX10: Byte        = 10,
    seekCounter: Counter  = 0,
    speedCounter: Counter = 0,
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

  opaque type AtomicCounter = AtomicInteger
  object AtomicCounter:
    def apply(value: Counter = Counter.zero): AtomicCounter = AtomicInteger(value)
    extension (atomic: AtomicCounter)
      inline def get: Counter = atomic.get
      def inc: Counter = atomic.updateAndGet(Counter.inc)
      def merge(other: Counter): Counter = atomic.updateAndGet(Counter.merge(_)(other))
      def set(value: Boolean): Boolean =
        val old = atomic.getAndUpdate: old =>
          if (old.toBoolean == value) old else Counter.inc(old)
        old.toBoolean != value


trait Messages private[p2p]:
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

  def addMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): MessageHandler[M] =
    TaskQueue.threadSafe:
      messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
        listOpt => Some(handler :: listOpt.getOrElse(Nil))
    handler

  def removeMessageHandler[M <: Message](handler: MessageHandler[M])(using tag: ClassTag[M]): Unit =
    TaskQueue.threadSafe:
      messageHandlers.updateWith(tag.runtimeClass.asInstanceOf[Class[? <: Message]]):
        _.map(_.filter(_ != handler)).filter(_.nonEmpty)
