package byte_codec

import byte_codec.ByteCodec.{CompactUInt, CompactULong}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.lang
import scala.annotation.tailrec
import scala.collection.Factory
import scala.reflect.ClassTag


trait ByteCodecGivenPrimitives private[byte_codec]:

  implicit val bool: ByteCodec[Boolean] = new ByteCodec:
    def encode(value: Boolean, output: ByteArrayOutputStream): Unit = output.write(if (value) 1 else 0)
    def decode(input: ByteArrayInputStream): Boolean = read(input) match
      case 0 => false
      case 1 => true
      case invalid => sys.error(s"Invalid value in ByteCodec[Boolean].decode: $invalid")

  implicit val byte: ByteCodec[Byte] = new ByteCodec:
    def encode(value: Byte, output: ByteArrayOutputStream): Unit = output.write(value)
    def decode(input: ByteArrayInputStream): Byte = read(input).toByte

  implicit val char: ByteCodec[Char] = new ByteCodec:
    def encode(value: Char, output: ByteArrayOutputStream): Unit =
      output.write(value >> 8)
      output.write(value.toInt)
    def decode(input: ByteArrayInputStream): Char =
      ((read(input) << 8) | read(input)).toChar

  implicit val short: ByteCodec[Short] = new ByteCodec:
    def encode(value: Short, output: ByteArrayOutputStream): Unit =
      output.write(value >> 8)
      output.write(value)
    def decode(input: ByteArrayInputStream): Short =
      ((read(input) << 8) | read(input)).toShort

  implicit val int: ByteCodec[Int] = new ByteCodec:
    def encode(value: Int, output: ByteArrayOutputStream): Unit =
      output.write(value >> 24)
      output.write(value >> 16)
      output.write(value >> 8)
      output.write(value)
    def decode(input: ByteArrayInputStream): Int =
      (read(input) << 24) |
      (read(input) << 16) |
      (read(input) << 8) |
      read(input)

  implicit val long: ByteCodec[Long] = new ByteCodec:
    def encode(value: Long, output: ByteArrayOutputStream): Unit =
      output.write((value >> 56).toInt)
      output.write((value >> 48).toInt)
      output.write((value >> 40).toInt)
      output.write((value >> 32).toInt)
      output.write((value >> 24).toInt)
      output.write((value >> 16).toInt)
      output.write((value >> 8).toInt)
      output.write(value.toInt)
    def decode(input: ByteArrayInputStream): Long =
      (read(input).toLong << 56) |
      (read(input).toLong << 48) |
      (read(input).toLong << 40) |
      (read(input).toLong << 32) |
      (read(input).toLong << 24) |
      (read(input).toLong << 16) |
      (read(input).toLong << 8) |
      read(input).toLong

  implicit val float: ByteCodec[Float] = new ByteCodec:
    def encode(value: Float, output: ByteArrayOutputStream): Unit =
      int.encode(lang.Float.floatToRawIntBits(value), output)
    def decode(input: ByteArrayInputStream): Float =
      lang.Float.intBitsToFloat(int.decode(input))

  implicit val double: ByteCodec[Double] = new ByteCodec:
    def encode(value: Double, output: ByteArrayOutputStream): Unit =
      long.encode(lang.Double.doubleToRawLongBits(value), output)
    def decode(input: ByteArrayInputStream): Double =
      lang.Double.longBitsToDouble(long.decode(input))

  implicit val string: ByteCodec[String] = new ByteCodec:
    def encode(value: String, output: ByteArrayOutputStream): Unit =
      summon[ByteCodec[Array[Byte]]].encode(value.getBytes, output)
    def decode(input: ByteArrayInputStream): String =
      String(summon[ByteCodec[Array[Byte]]].decode(input))


  implicit val compactUInt: ByteCodec[CompactUInt] = new ByteCodec:
    def encode(value: CompactUInt, output: ByteArrayOutputStream): Unit =
      compactULong.encode(value.toLong & 0xffff_ffff, output)
    def decode(input: ByteArrayInputStream): CompactUInt =
      val long = compactULong.decode(input)
      if ((long & 0xffff_ffff_0000_0000L) != 0) sys.error("Invalid CompactUInt bytes")
      long.toInt

  implicit val compactULong: ByteCodec[CompactULong] = new ByteCodec:
    def encode(value: CompactULong, output: ByteArrayOutputStream): Unit = encode(value, output, 8)
    @tailrec private def encode(value: CompactULong, output: ByteArrayOutputStream, count: Int): Unit =
      val next = value >> 7
      if (next == 0 || count == 0)
        output.write(value.toInt)
      else
        output.write(value.toInt | 0x80)
        encode(next, output, count - 1)

    def decode(input: ByteArrayInputStream): CompactULong = decode(input, 0, 0)
    @tailrec private def decode(input: ByteArrayInputStream, shift: Int, acc: Long): CompactULong =
      val part = read(input)
      if ((part & 0x80) == 0 || shift == 56) acc | (part << shift)
      else decode(input, shift + 7, acc | ((part & ~0x80) << shift))


  abstract class IterableCodec[T, It : ClassTag](using childCodec: ByteCodec[T], factory: Factory[T, It])
    extends ByteCodec[It]:
    def length(it: It): Int
    def foreach[U](it: It)(func: T => U): Unit

    def encode(value: It, output: ByteArrayOutputStream): Unit =
      compactUInt.encode(length(value), output)
      foreach(value)(childCodec.encode(_, output))
    def decode(input: ByteArrayInputStream): It =
      factory.fromSpecific:
        Iterable.tabulate(compactUInt.decode(input))(_ => childCodec.decode(input))

  given [T: {ClassTag, ByteCodec}]: ByteCodec[Array[T]] = new IterableCodec[T, Array[T]]:
    def length(array: Array[T]): Int = array.length
    def foreach[U](array: Array[T])(func: T => U): Unit = array.foreach(func)

  given [T : ByteCodec, It <: Iterable[T] : ClassTag](using Factory[T, It]): ByteCodec[It] = new IterableCodec[T, It]:
    def length(it: It): Int = it.size
    def foreach[U](it: It)(func: T => U): Unit = it.foreach(func)
