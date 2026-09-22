package p2p.phone

import byte_codec.ByteCodec.CompactUInt
import p2p.base.Message.{PhoneData, PhoneMetadata}
import p2p.base.Peer
import p2p.phone.Jitter.SingleJitter
import scalafx.Includes.jfxObjectProperty2sfx
import util.also

import java.nio.{ByteBuffer, FloatBuffer}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.language.implicitConversions


private class Jitter:
  private val jitters = mutable.Map[Peer, SingleJitter]()

  def put(data: PhoneData, peer: Peer): Unit =
    jitters.getOrElseUpdate(peer, SingleJitter(peer)).put(data)

  def get: Option[Array[Float]] =
    val buffers = jitters.values.map(_.get).toList
    jitters.filterInPlace((_, jitter) => jitter.alive)
    buffers.reduceOption: (a, b) =>
      for (i <- 0 until frameSize)
        a(i) += b(i)
      a

private object Jitter:

  private inline val QueueMaxSize = 14
  private inline val AvgCoef = 0.08f
  private inline val KillAfter = 10

  private class Packet(val message: PhoneData) extends Comparable[Packet]:
    val time: Long = System.currentTimeMillis
    inline def index: Int = message.index
    def compareTo(other: Packet): Int = other.index - this.index
  private object Packet:
    implicit inline def toMessage(packet: Packet): PhoneData = packet.message

  private class SingleJitter(peer: Peer):
    private var readIndex = -QueueMaxSize
    private var statLoss, statTotal = 0

    private var queue = mutable.PriorityQueue.empty[Packet]
    private var queueAvgSize = QueueMaxSize * 0.5f
    private var queueAvgSizeDiffNegSqr = 0f
    private var packetAvgWait = frameDuration.toFloat
    private var packetAvgWaitNegDiffSqr = 0f

    private var decoder: codec.Decoder = new codec.DummyDecoder
    private var frame = FloatBuffer.allocate(0)

    def alive: Boolean = readIndex > -KillAfter

    def put(data: PhoneData): Unit =
      queue.enqueue(Packet(data))
      if (queue.size > QueueMaxSize) queue.dequeue()

    def get: Array[Float] =
      get(FloatBuffer.allocate(frameSize))

    @tailrec private def get(data: FloatBuffer): Array[Float] =
      if (data.remaining < frame.remaining) frame.limit(data.remaining)
      data.put(frame)
      frame.limit(frame.capacity)
      if (data.remaining == 0)
        data.array
      else
        frame = FloatBuffer.wrap(nextFrame)
        get(data)

    private def nextFrame: Array[Float] =
      val data = decodeNext
      readIndex += (if (readIndex >= 0) 1 else -1)

      statTotal += 1
      if (statTotal == metaSendPeriod / frameDuration)
        peer.send(PhoneMetadata(statLoss * 100 / statTotal, Phone.enableEncoderCounter))
        statLoss = 0
        statTotal = 0

      val queueSizeDiff = queue.size - queueAvgSize
      if (queueSizeDiff < 0)
        queueAvgSizeDiffNegSqr += (queueSizeDiff * queueSizeDiff - queueAvgSizeDiffNegSqr) * AvgCoef
      queueAvgSize += (queue.size - queueAvgSize) * AvgCoef

      val queueMinAvgSize = queueAvgSize - math.sqrt(queueAvgSizeDiffNegSqr).toFloat
      val packetMinAvgWait = packetAvgWait - math.sqrt(packetAvgWaitNegDiffSqr).toFloat

      if (queueMinAvgSize < 1.5f) changeSpeed(data, if (queueMinAvgSize < 0.5f) 0.8f else 0.9f)
      else if (packetMinAvgWait > 65) changeSpeed(data, if (packetMinAvgWait > 90) 1.25f else 1.111111f)
      else data

    @tailrec private def decodeNext: Array[Float] =
      queue.headOption match
        case Some(packet) if (packet.index - readIndex + queue.size).abs > QueueMaxSize =>
          readIndex = packet.index
          dequeue()
          queue = queue.filter(data => (data.index - readIndex).abs <= QueueMaxSize)
          decode(packet, fec = false)
        case Some(packet) if packet.index < readIndex =>
          dequeue(System.currentTimeMillis + (packet.index - readIndex) * frameDuration)
          decodeNext
        case Some(packet) if packet.index == readIndex =>
          dequeue()
          decode(packet, fec = false)
        case Some(packet) if packet.index == readIndex + 1 =>
          decode(packet, fec = true)
        case packetOpt =>
          if (packetOpt.isEmpty && readIndex >= 0) readIndex = -QueueMaxSize
          statLoss += 1
          decoder.decodeMissing

    private def decode(packet: Packet, fec: Boolean) =
      decoder = codec.ensureOpus(decoder, enable = packet.enableEncoderCounter.toBoolean)
      decoder.decode(packet.data, fec)

    private def dequeue(now: Long = System.currentTimeMillis): Unit =
      val wait = now - queue.dequeue().time
      val waitDiff = wait - packetAvgWait
      if (waitDiff < 0)
        packetAvgWaitNegDiffSqr += (waitDiff * waitDiff - packetAvgWaitNegDiffSqr) * AvgCoef
      packetAvgWait += (wait - packetAvgWait) * AvgCoef

    private def changeSpeed(input: Array[Float], speed: Float) =
      val sonic = Sonic(sampleRate, 1)
      sonic.setSpeed(speed)
      sonic.writeFloatToStream(input, input.length)
      sonic.flushStream()

      val newLength = sonic.samplesAvailable
      new Array[Float](newLength).also:
        sonic.readFloatFromStream(_, newLength)
