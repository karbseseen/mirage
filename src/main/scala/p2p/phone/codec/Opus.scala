package p2p.phone.codec

import core.main.MainApp
import org.freedesktop.dbus.errors.InvalidMethodArgument
import org.lwjgl.util.opus.Opus.*
import p2p.phone.{codec, frameSize, sampleRate}
import util.also

import java.nio.{ByteBuffer, ByteOrder}
import scala.language.implicitConversions


class OpusEncoder extends Encoder:
  private val input = ByteBuffer.allocateDirect(frameSize * 4).order(ByteOrder.nativeOrder).asFloatBuffer
  private val output = ByteBuffer.allocateDirect(4000).order(ByteOrder.nativeOrder)
  private val native = opus_encoder_create(sampleRate, 1, OPUS_APPLICATION_VOIP, output.asIntBuffer)

  output.getInt(0).checkOpusError
  native.also(native => MainApp.cleaner.register(this, () => opus_encoder_destroy(native)))
  opus_encoder_ctl(native, OPUS_SET_INBAND_FEC(1)).checkOpusError
  opus_encoder_ctl(native, OPUS_SET_VBR_CONSTRAINT(0)).checkOpusError

  def encode(data: Array[Float]): Array[Byte] =
    if (data.length != input.limit)
      throw InvalidMethodArgument(s"data length is ${data.length} instead of frameSize = $frameSize")
    input.put(0, data)
    val size = opus_encode_float(native, input, frameSize, output).checkOpusError
    new Array[Byte](size).also(output.get(0, _))

  def setLoss(percentage: Int): Unit =
    opus_encoder_ctl(native, OPUS_SET_PACKET_LOSS_PERC(percentage)).checkOpusError


class OpusDecoder extends Decoder:
  private val input = ByteBuffer.allocateDirect(4000).order(ByteOrder.nativeOrder)
  private val output = ByteBuffer.allocateDirect(frameSize * 4).order(ByteOrder.nativeOrder).asFloatBuffer
  private val native = opus_decoder_create(sampleRate, 1, input.asIntBuffer)

  input.getInt(0).checkOpusError
  native.also(native => MainApp.cleaner.register(this, () => opus_decoder_destroy(native)))

  def decode(data: Array[Byte], fec: Boolean): Array[Float] =
    decodeInner(input.limit(data.length).put(0, data), fec)

  def decodeMissing: Array[Float] =
    decodeInner(null, false)

  private def decodeInner(input: ByteBuffer, fec: Boolean) =
    val size = opus_decode_float(native, input, output, frameSize, if (fec) 1 else 0).checkOpusError
    if (size != frameSize)
      throw Exception(s"Received opus packet with a wrong length of $size samples")
    new Array[Float](size).also(output.get(0, _))


extension (code: Int)
  private def checkOpusError: Int = code match
    case code if code >= 0      => code
    case OPUS_BAD_ARG           => throw Exception("OPUS_BAD_ARG")
    case OPUS_BUFFER_TOO_SMALL  => throw Exception("OPUS_BUFFER_TOO_SMALL")
    case OPUS_INTERNAL_ERROR    => throw Exception("OPUS_INTERNAL_ERROR")
    case OPUS_INVALID_PACKET    => throw Exception("OPUS_INVALID_PACKET")
    case OPUS_UNIMPLEMENTED     => throw Exception("OPUS_UNIMPLEMENTED")
    case OPUS_INVALID_STATE     => throw Exception("OPUS_INVALID_STATE")
    case OPUS_ALLOC_FAIL        => throw Exception("OPUS_ALLOC_FAIL")
    case unknown                => throw Exception(s"Unknown Opus error code $unknown")
