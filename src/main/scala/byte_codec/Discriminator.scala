package byte_codec

import scala.annotation.StaticAnnotation
import scala.quoted.*


case class Discriminator[T](value: Byte) extends StaticAnnotation

object Discriminator:

  given Discriminator[None.type](0)
  given [T]: Discriminator[Some[T]](1)

  given [L, R]: Discriminator[Right[L, R]](1)
  given [L, R]: Discriminator[Left[L, R]](2)

  inline given[T]: Discriminator[T] = ${ fromAnnotationImpl[T] }
  private def fromAnnotationImpl[T : Type](using Quotes): Expr[Discriminator[T]] =
    import quotes.reflect.*
    TypeRepr.of[T].typeSymbol.annotations
      .collectFirst:
        case Apply(TypeApply(Select(New(tpt), _), _), Literal(ByteConstant(value)) :: Nil)
          if tpt.tpe =:= TypeRepr.of[Discriminator] =>
          '{ Discriminator[T]( ${Expr(value)} ) }
      .getOrElse:
        report.errorAndAbort(s"${TypeRepr.of[T].show} doesn't have ByteCodecDiscriminator")
