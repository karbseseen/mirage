package p2p.phone.portaudio

import core.main.MainApp
import p2p.phone.portAudioLatency
import p2p.phone.portaudio.portaudio_h.*
import util.also

import java.lang.foreign.{Arena, MemorySegment, ValueLayout}


object PortAudioStream:

  trait Callback:
    def apply(input: Array[Float]): Array[Float]

  Pa_Initialize().check
  MainApp.shutdownHook(Pa_Terminate())


class PortAudioStream(
  input: Boolean,
  output: Boolean,
  sampleRate: Int,
  frameSize: Int,
)(
  callback: PortAudioStream.Callback,
):
  PortAudioStream

  private val arena = Arena.ofConfined

  private val inputArray = new Array[Float](frameSize)
  private val paCallback: PaStreamCallback = (
    input: MemorySegment,
    output: MemorySegment,
    frameCount: Long,
    timeInfo: MemorySegment,
    statusFlags: Long,
    userData: MemorySegment,
  ) =>
    val hasInput  = input.address != 0
    val hasOutput = output.address != 0

    if (hasInput) MemorySegment.copy(
      input.reinterpret(frameSize * 4),
      ValueLayout.JAVA_FLOAT,
      0,
      inputArray,
      0,
      frameSize,
    )

    val outputArray = callback(if (hasInput) inputArray else null)

    if (hasOutput && outputArray != null) MemorySegment.copy(
      outputArray,
      0,
      output.reinterpret(frameSize * 4),
      ValueLayout.JAVA_FLOAT,
      0,
      frameSize,
    )

    paContinue

  private val streamPtr = arena.allocate(ValueLayout.ADDRESS)
  private val inputParameters = if (!input) MemorySegment.NULL else PaStreamParameters.allocate(arena).also: parameters =>
    PaStreamParameters.device(parameters, Pa_GetDefaultInputDevice)
    PaStreamParameters.channelCount(parameters, 1)
    PaStreamParameters.sampleFormat(parameters, paFloat32)
    PaStreamParameters.suggestedLatency(parameters, portAudioLatency / 1000.0)
  private val outputParameters = if (!output) MemorySegment.NULL else PaStreamParameters.allocate(arena).also: parameters =>
    PaStreamParameters.device(parameters, Pa_GetDefaultOutputDevice)
    PaStreamParameters.channelCount(parameters, 1)
    PaStreamParameters.sampleFormat(parameters, paFloat32)
    PaStreamParameters.suggestedLatency(parameters, portAudioLatency / 1000.0)

  Pa_OpenStream(
    streamPtr,
    inputParameters,
    outputParameters,
    sampleRate,
    frameSize,
    paNoFlag,
    PaStreamCallback.allocate(paCallback, arena),
    MemorySegment.NULL,
  ).check
  private val stream = streamPtr.get(ValueLayout.ADDRESS, 0)
  Pa_StartStream(stream).check

  def close(): Unit =
    Pa_CloseStream(stream)
    arena.close()

  def abort(): Unit =
    Pa_AbortStream(stream)
    arena.close()


extension (error: Int) private def check: Int =
  if (error == paNoError) error
  else throw new IllegalStateException(s"PortAudio error $error: ${Pa_GetErrorText(error).getString(0)}")
