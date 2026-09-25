package p2p.phone

import byte_codec.ByteCodec.CompactUInt
import p2p.base.Message.{PhoneData, PhoneMetadata}
import p2p.base.Peer
import p2p.phone.Jitter.SingleJitter

import scala.annotation.tailrec
import scala.collection.mutable
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

  private inline val QueueMaxSize = 18
  private inline val AvgCoef = 0.07f
  private inline val KillAfter = 10
  private inline val NormalSpeed = 1.03f

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
    private var packetAvgWait = frameDuration.toFloat
    private var packetAvgWaitNegDiffSqr = 0f

    private var decoder: codec.Decoder = new codec.DummyDecoder
    private var wsola: Option[Wsola] = None

    def alive: Boolean = readIndex > -KillAfter

    def put(data: PhoneData): Unit =
      queue.enqueue(Packet(data))
      if (queue.size > QueueMaxSize) queue.dequeue()

    @tailrec final def get: Array[Float] =
      wsola match
        case None =>
          val (data, speed) = decodeNext
          if (speed == NormalSpeed) data
          else
            val wsola = Wsola(1200, 800)
            wsola.speed = speed
            wsola.put(data)
            this.wsola = Some(wsola)
            get
        case Some(wsola) if wsola.speed == NormalSpeed && wsola.available + 400 < frameSize =>
          this.wsola = None
          wsola.flush(frameSize)
        case Some(wsola) if wsola.available < frameSize =>
          val (data, speed) = decodeNext
          wsola.speed = speed
          wsola.put(data)
          get
        case Some(wsola) =>
          wsola.get(frameSize)

    private def decodeNext =
      val data = decodeNext2
      readIndex += (if (readIndex >= 0) 1 else -1)

      statTotal += 1
      if (statTotal == metaSendPeriod / frameDuration)
        peer.send(PhoneMetadata(statLoss * 100 / statTotal, Phone.enableEncoderCounter))
        statLoss = 0
        statTotal = 0

      val packetMinAvgWait = packetAvgWait - math.sqrt(packetAvgWaitNegDiffSqr).toFloat
      val speed = packetMinAvgWait.toInt / 10 match
        case p if p < 7 => NormalSpeed
        case 7          => wsola.fold(NormalSpeed)(_.speed min 1.2f)
        case 8 | 9      => 1.2f
        case 10         => wsola.fold(1.2f)(_.speed max 1.2f)
        case _          => 1.4f

      (data, speed)

    @tailrec private def decodeNext2: Array[Float] =
      queue.headOption match
        case Some(packet) if (packet.index - readIndex + queue.size).abs > QueueMaxSize =>
          readIndex = packet.index
          dequeue()
          queue = queue.filter(data => (data.index - readIndex).abs <= QueueMaxSize)
          decode(packet, fec = false)
        case Some(packet) if packet.index < readIndex =>
          dequeue(System.currentTimeMillis + (packet.index - readIndex) * frameDuration)
          decodeNext2
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
