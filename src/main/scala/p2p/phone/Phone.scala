package p2p.phone

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import config.Config
import core.TaskQueue
import javafx.beans.property.{ReadOnlyBooleanProperty, SimpleBooleanProperty}
import javafx.beans.value.ChangeListener
import p2p.base.Message.{Counter, PhoneData, PhoneMetadata}
import p2p.base.{P2p, Peer, PeerListener}
import p2p.phone.codec.{Encoder, OpusEncoder}
import p2p.phone.portaudio.PortAudioStream
import scalafx.Includes.jfxBooleanProperty2sfx
import scalafx.application.Platform
import util.SystemInfo.OS
import util.{SystemInfo, let}

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.compiletime.uninitialized


private inline val sampleRate = 48000
private inline val frameDuration = 20
private inline val frameSize = sampleRate * frameDuration / 1000
private inline val metaSendPeriod = 1000
private inline val portAudioLatency = 35


object Phone:


  private trait WinMM extends StdCallLibrary:
    def timeBeginPeriod(uPeriod: Int): Int
    def timeEndPeriod(uPeriod: Int): Int
  private object WinMM:
    val instance: Option[WinMM] = Option.when(SystemInfo.os == OS.Windows)(Native.load("winmm", classOf[WinMM]))

  private val _enableEncoder = Config.BoolProp(this, "enableEncoder", true)
  def enableEncoder: ReadOnlyBooleanProperty = _enableEncoder

  @volatile private var _enableEncoderCounter = Counter(_enableEncoder())
  def enableEncoderCounter: Counter = _enableEncoderCounter
  def enableEncoderCounter_=(value: Counter): Unit =
    _enableEncoderCounter = value
    _enableEncoder() = value.toBoolean


class Phone:

  private val portAudio = new PortAudio
  private val metaHandler = new MetaHandler
  private val inputQueue = AtomicReference(List[(PhoneData, Peer)]())
  private var enableEncoderCounter = Phone.enableEncoderCounter

  val microphoneEnabled = SimpleBooleanProperty(this, "enabled", false)
  microphoneEnabled.addListener((_,_,enabled) => portAudio.setInput(enabled))

  Phone.WinMM.instance.foreach(_.timeBeginPeriod(1))

  private val dataMessageHandler = P2p.addMessageHandler[PhoneData]: (data, peer) =>
    inputQueue.getAndUpdate((data, peer) :: _)
    portAudio.setOutput(true)

  private val metadataMessageHandler = P2p.addMessageHandler[PhoneMetadata]: (meta, peer) =>
    metaHandler.put(meta, peer)

  def close(): Unit =
    Phone.WinMM.instance.foreach(_.timeEndPeriod(1))
    TaskQueue.threadSafe:
      P2p.removeMessageHandler(dataMessageHandler)
      P2p.removeMessageHandler(metadataMessageHandler)
      portAudio.close()

  /**Call only inside portaudio thread*/
  private def mergeEnableEncoder(): Unit =
    val globalCounter = Phone.enableEncoderCounter
    val diff = enableEncoderCounter compare globalCounter
    if (diff < 0)
      enableEncoderCounter = globalCounter
    else if (diff > 0)
      val localCounter = enableEncoderCounter
      Platform.runLater(Phone.enableEncoderCounter = Phone.enableEncoderCounter merge localCounter)

  private class PortAudio:
    private var _stream: Option[PortAudioStream] = None
    def stream: Option[PortAudioStream] = _stream

    private var input, output, closed = false
    def setInput(value: Boolean): Unit = update(input != value) { input = value }
    def setOutput(value: Boolean): Unit = update(output != value) { output = value }
    def close(): Unit = update(!closed) { closed = true }

    private inline def update(inline condition: => Boolean)(inline action: => Unit): Unit =
      TaskQueue.threadSafe(if (condition) { action; update() })

    private def update(): Unit =
      _stream.foreach(_.abort())
      _stream = Option.when(!closed)(input, output)
        .collect:
          case (true, false) => new Input
          case (false, true) => new Output
          case (true, true) => new InOut
        .map:
          PortAudioStream(input, output, sampleRate, frameSize)(_)

  private class Input(mergeEnableEncoder: Boolean = true) extends PortAudioStream.Callback:
    private var encoder: Encoder = uninitialized
    private var counter = 0
    private var loss = 0
    def apply(input: Array[Float]): Array[Float] =
      if (mergeEnableEncoder) Phone.this.mergeEnableEncoder()
      encoder = codec.ensureOpus(encoder, enable = enableEncoderCounter.toBoolean)
      metaHandler.maxLoss.let: handlerLoss =>
        if (loss != handlerLoss)
          loss = handlerLoss
          Some(encoder).collect:
            case opus: OpusEncoder => opus.setLoss(loss)
      P2p.sendToAll(PhoneData(counter, encoder.encode(input), enableEncoderCounter))
      counter += 1
      null

  private class Output extends PortAudioStream.Callback:
    private val jitter = new Jitter
    def apply(input: Array[Float]): Array[Float] =
      inputQueue.getAndSet(Nil).reverse.foreach: (packet, peer) =>
        jitter.put(packet, peer)
        enableEncoderCounter = enableEncoderCounter merge packet.enableEncoderCounter
      mergeEnableEncoder()
      val buffer = jitter.get
      if (buffer.isEmpty) portAudio.setOutput(false)
      buffer getOrElse new Array[Float](frameSize)

  private class InOut extends PortAudioStream.Callback:
    private val input = new Input(mergeEnableEncoder = false)
    private val output = new Output
    private val echo = new WebRtcAec
    def apply(in: Array[Float]): Array[Float] =
      val out = output(in)
      echo.putPlay(out)
      echo.cancel(in).foreach(input(_))
      out


private class MetaHandler:
  private val map = mutable.Map[Peer, PhoneMetadata]()
  @volatile private var _maxLoss = 0
  def maxLoss: Int = _maxLoss

  def put(meta: PhoneMetadata, peer: Peer): Unit =
    map(peer) = meta
    updateMaxLoss()

  private val listener = P2p.addPeerListener: (peer, added) =>
    if (!added)
      map -= peer
      updateMaxLoss()

  private def updateMaxLoss(): Unit =
    _maxLoss = map.values.map(_.packetLoss: Int).maxOption.getOrElse(0)

  def close(): Unit = P2p.removePeerListener(listener)
