package p2p.phone

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import core.TaskQueue
import javafx.beans.property.SimpleBooleanProperty
import p2p.base.Message.{PhoneData, PhoneMetadata}
import p2p.base.{P2p, Peer, PeerListener}
import p2p.phone.portaudio.PortAudioStream
import util.SystemInfo.OS
import util.{SystemInfo, let}

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.Queue
import scala.collection.mutable


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


class Phone:

  private val portAudio = new PortAudio
  private val metaHandler = new MetaHandler
  private val inputQueue = AtomicReference(List[(PhoneData, Peer)]())

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

  private class Input extends PortAudioStream.Callback:
    private val encoder = new Encoder
    private var counter = 0
    private var loss = 0
    def apply(input: Array[Float]): Array[Float] =
      metaHandler.maxLoss.let: handlerLoss =>
        if (loss != handlerLoss)
          loss = handlerLoss
          encoder.setLoss(loss)
      val encoded = encoder.encode(input)
      val data = PhoneData(counter, encoded)
      counter += 1
      P2p.sendToAll(data)
      null

  private class Output extends PortAudioStream.Callback:
    private val jitter = new Jitter
    def apply(input: Array[Float]): Array[Float] =
      inputQueue.getAndSet(Nil).reverse.foreach(jitter.put)
      val buffer = jitter.get
      if (buffer.isEmpty) portAudio.setOutput(false)
      buffer getOrElse new Array[Float](frameSize)

  private class InOut extends PortAudioStream.Callback:
    private val input = new Input
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
