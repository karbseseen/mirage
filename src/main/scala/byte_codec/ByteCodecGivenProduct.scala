package byte_codec

import byte_codec.ByteCodecGivenProduct.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.compiletime.{erasedValue, summonFrom, summonInline}
import scala.deriving.Mirror
import scala.reflect.ClassTag


trait ByteCodecGivenProduct private[byte_codec]:

  inline given [T : ClassTag](using mirror: Mirror.Of[T]): ByteCodec[T] = inline mirror match
    case mirror: Mirror.Singleton     => ObjectCodec(mirror.fromProduct(null))
    case mirror: Mirror.ProductOf[T]  => ProductCodec(productChildren[mirror.MirroredElemTypes], mirror)
    case mirror: Mirror.SumOf[T]      => SumCodec(sumChildren[mirror.MirroredElemTypes])

  private inline def productChildren[T <: Tuple]: List[ByteCodec[?]] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (t *: ts) => summonInline[ByteCodec[t]] :: productChildren[ts]

  private inline def sumChildren[T <: Tuple]: List[ByteCodecWithDiscriminator[?]] = inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (t *: ts) => summonFrom:
      case mirror: Mirror.SumOf[`t`] => sumChildren[mirror.MirroredElemTypes] ::: sumChildren[ts]
      case codec: ByteCodecWithDiscriminator[`t`] => codec :: sumChildren[ts]


object ByteCodecGivenProduct:

  class ObjectCodec[O : ClassTag](obj: O) extends ByteCodec[O]:
    def encode(obj: O, output: ByteArrayOutputStream): Unit = ()
    def decode(input: ByteArrayInputStream): O = obj


  class ProductCodec[P : ClassTag](childCodecs: List[ByteCodec[?]], mirror: Mirror.ProductOf[P]) extends ByteCodec[P]:
    def encode(product: P, output: ByteArrayOutputStream): Unit =
      for (childCodec, child) <- childCodecs zip product.asInstanceOf[Product].productIterator do
        childCodec.encodeAny(child, output)
    def decode(input: ByteArrayInputStream): P =
      val values = childCodecs.map(_.decode(input)).toArray
      mirror.fromProduct(Tuple.fromArray(values))


  class SumCodec[T: ClassTag](childCodecs: Iterable[ByteCodecWithDiscriminator[?]]) extends ByteCodec[T]:
    private val childCodecByClass = childCodecs.groupMapReduce(_.codec.cls)(identity):
      (a, b) => sys.error(s"${a.codec} and ${b.codec} have the same class = ${a.codec.cls}")
    private val childCodecByDiscriminator = childCodecs.groupMapReduce(_.discriminatorValue)(identity):
      (a, b) => sys.error(s"${a.codec} and ${b.codec} have the same discriminator ${a.discriminatorValue}")

    def encode(product: T, output: ByteArrayOutputStream): Unit =
      val codec = childCodecByClass(product.getClass)
      output.write(codec.discriminatorValue)
      codec.codec.encodeAny(product, output)
    def decode(input: ByteArrayInputStream): T =
      val codec = childCodecByDiscriminator(input.read.toByte)
      codec.codec.decode(input).asInstanceOf[T]


  class ByteCodecWithDiscriminator[T](val codec: ByteCodec[T], val discriminatorValue: Byte)
  given [T](using codec: ByteCodec[T], discriminator: Discriminator[T]): ByteCodecWithDiscriminator[T] =
    ByteCodecWithDiscriminator(codec, discriminator.value)

  extension [T](codec: ByteCodec[T])
    private def encodeAny(value: Any, output: ByteArrayOutputStream): Unit = codec.encode(value.asInstanceOf[T], output)
