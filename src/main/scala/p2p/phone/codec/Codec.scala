package p2p.phone.codec


private[phone] trait Encoder:
  def encode(data: Array[Float]): Array[Byte]

private[phone] trait Decoder:
  def decode(data: Array[Byte], fec: Boolean): Array[Float]
  def decodeMissing: Array[Float]


def ensureOpus(encoder: Encoder, enable: Boolean): Encoder =
  if (enable && !encoder.isInstanceOf[OpusEncoder])         new OpusEncoder
  else if (!enable && !encoder.isInstanceOf[DummyEncoder])  new DummyEncoder
    else encoder

def ensureOpus(decoder: Decoder, enable: Boolean): Decoder =
  if (enable && !decoder.isInstanceOf[OpusDecoder])         new OpusDecoder
  else if (!enable && !decoder.isInstanceOf[DummyEncoder])  new DummyDecoder
  else decoder
