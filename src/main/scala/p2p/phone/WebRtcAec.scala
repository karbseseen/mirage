package p2p.phone

import cn.enaium.webrtc.aec3.Aec3_jvmKt.*
import core.main.MainApp
import p2p.phone.WebRtcAec.Chunker
import util.also

import java.nio.FloatBuffer


private class WebRtcAec:

  private val (aec, playBuffer, cancelBuffer) =
    val config = createAec3Config.also: config =>
      config.setDelayDefaultDelay(25)
      config.setFilterInitialStateSeconds(0.5f)
      config.setFilterConservativeInitialPhase(false)
    val env = createAec3Environment
    val factory = createAec3FactoryWithConfig(config)
    val echoControl = createAec3EchoControl(factory, env, sampleRate, 1, 1)
    val playBuffer, cancelBuffer = createAec3AudioBuffer(sampleRate, 1)

    MainApp.cleaner.register(this, () =>
      playBuffer.close()
      cancelBuffer.close()
      echoControl.close()
      factory.close()
      env.close()
      config.close()
    )

    (echoControl, playBuffer, cancelBuffer)

  private val recordChunker, playChunker = new Chunker:
    private val buffer = FloatBuffer.allocate(sampleRate / 100)
    protected def createBuffer: FloatBuffer = buffer.position(0)
  private val resultChunker = new Chunker:
    protected def createBuffer: FloatBuffer = FloatBuffer.allocate(frameSize)

  def putPlay(data: Array[Float]): Unit =
    playChunker.chunk(data).foreach: playChunk =>
      playBuffer.writeChannel(0, playChunk)
      aec.analyzeRender(playBuffer)

  def cancel(data: Array[Float]): List[Array[Float]] =
    recordChunker.chunk(data).flatMap: recordChunk =>
      cancelBuffer.writeChannel(0, recordChunk)
      aec.analyzeCapture(cancelBuffer)
      aec.processCapture(cancelBuffer, false)
      resultChunker.chunk(cancelBuffer.readChannel(0))
    .toList


private object WebRtcAec:

  private abstract class Chunker:
    protected def createBuffer: FloatBuffer
    private var buffer = createBuffer
    def chunk(data: Array[Float]): Iterator[Array[Float]] =
      val input = FloatBuffer.wrap(data)
      val infinite = Iterator.continually:
        if (input.remaining >= buffer.remaining)
          input.limit(input.position + buffer.remaining)
          buffer.put(input)
          input.limit(input.capacity)
          val array = buffer.array
          buffer = createBuffer
          Some(array)
        else
          buffer.put(input)
          None
      infinite.takeWhile(_.nonEmpty).map(_.get)
