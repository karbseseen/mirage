package p2p.phone.codec

import p2p.phone.{codec, frameSize}

import java.nio.{ByteBuffer, FloatBuffer}


class DummyEncoder extends Encoder:
  def encode(data: Array[Float]): Array[Byte] =
    val buffer = ByteBuffer.allocate(data.length * 2)
    data.foreach: value =>
      buffer.putShort((value * Short.MaxValue).toShort)
    buffer.array

class DummyDecoder extends Decoder:
  def decodeMissing: Array[Float] = new Array[Float](frameSize)
  def decode(data: Array[Byte], fec: Boolean): Array[Float] =
    val input = ByteBuffer.wrap(data)
    val output = FloatBuffer.allocate(data.length / 2)
    while (output.remaining > 0)
      output.put(input.getShort / Short.MaxValue.toFloat)
    output.array
