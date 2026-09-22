package p2p.phone

import cn.enaium.webrtc.aec3.Aec3_jvmKt.*
import core.main.MainApp
import p2p.phone.WebRtcAec.Chunker
import util.also

import java.nio.FloatBuffer


private class WebRtcAec:

  private val (aec, aecBuffer) =
    val config = createAec3Config.also: config =>
      config.setDelayDefaultDelay(25)
      config.setFilterInitialStateSeconds(0.5f)
      config.setFilterConservativeInitialPhase(false)
    val env = createAec3Environment
    val factory = createAec3FactoryWithConfig(config)
    val echoControl = createAec3EchoControl(factory, env, sampleRate, 1, 1)
    val buffer = createAec3AudioBuffer(sampleRate, 1)

    MainApp.cleaner.register(this, () =>
      buffer.close()
      echoControl.close()
      factory.close()
      env.close()
      config.close()
    )

    (echoControl, buffer)

  private val buffer = FloatBuffer.allocate(sampleRate / 100)
  private val recordChunker, playChunker: Chunker = () => buffer.position(0)
  private val resultChunker: Chunker = () => FloatBuffer.allocate(frameSize)

  def putPlay(data: Array[Float]): Unit =
    playChunker.chunk(data): playChunk =>
      aecBuffer.writeChannel(0, playChunk)
      aec.analyzeRender(aecBuffer)

  def cancel(data: Array[Float]): List[Array[Float]] =
    var counter = 0
    recordChunker.chunk(data): recordChunk =>
      aecBuffer.writeChannel(0, recordChunk)
      aec.analyzeCapture(aecBuffer)
      counter += 1

    val builder = List.newBuilder[Array[Float]]
    for (_ <- 0 until counter)
      aec.processCapture(aecBuffer, false)
      resultChunker.chunk(aecBuffer.readChannel(0)):
        builder += _
    builder.result


private object WebRtcAec:

  private abstract class Chunker:
    private var buffer = createBuffer()
    protected def createBuffer(): FloatBuffer
    def chunk(data: Array[Float])(func: Array[Float] => Unit): Unit =
      val input = FloatBuffer.wrap(data)
      while (input.remaining >= buffer.remaining)
        input.limit(input.position + buffer.remaining)
        buffer.put(input)
        input.limit(input.capacity)
        func(buffer.array)
        buffer = createBuffer()
      buffer.put(input)
