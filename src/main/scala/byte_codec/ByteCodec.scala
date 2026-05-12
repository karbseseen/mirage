package byte_codec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.language.implicitConversions
import scala.reflect.ClassTag


abstract class ByteCodec[T : ClassTag]:
  val cls: Class[T] = summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
  def encode(value: T, output: ByteArrayOutputStream): Unit
  def decode(input: ByteArrayInputStream): T

  protected def read(input: ByteArrayInputStream): Int =
    val value = input.read
    if (value < 0) sys.error("Eof reached in ByteCodec.decode")
    value


object ByteCodec extends ByteCodecGivenPrimitives with ByteCodecGivenProduct:

  def encode[T](value: T)(using codec: ByteCodec[T]): Array[Byte] =
    val bytes = new ByteArrayOutputStream
    codec.encode(value, bytes)
    bytes.toByteArray

  def decode[T](bytes: Array[Byte])(using codec: ByteCodec[T]): T =
    codec.decode(ByteArrayInputStream(bytes))

  opaque type CompactUInt = Int
  object CompactUInt:
    implicit def from(value: Int): CompactUInt = value
    implicit def to(compact: CompactUInt): Int = compact

  opaque type CompactULong = Long
  object CompactULong:
    implicit def from(value: Long): CompactULong = value
    implicit def to(compact: CompactULong): Long = compact
