package config

import org.virtuslab.yaml.Node.ScalarNode
import org.virtuslab.yaml.{ConstructError, LoadSettings, Node, NodeOps, YamlCodec}

import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.Try


object YamlEnumCodec:
  inline def derived[T](using ct: ClassTag[T]): YamlEnumCodec[T] = ${ derivedImpl[T]('{ct.runtimeClass.getName}) }
  private def derivedImpl[T : Type](name: Expr[String])(using quotes: Quotes): Expr[YamlEnumCodec[T]] =
    import quotes.reflect.*
    val companion = Ref(TypeRepr.of[T].typeSymbol.companionModule)
      .asExprOf[{ def valueOf(string: String): T }]
    '{ new YamlEnumCodec[T]($name, $companion.valueOf) }


class YamlEnumCodec[T](name: String, get: String => T) extends YamlCodec[T]:
  def asNode(item: T): Node = ScalarNode(item.toString)
  def construct(node: Node)(using LoadSettings): Either[ConstructError, T] =
    Try(node)
      .collect { case ScalarNode(value, _) => get(value) }
      .toEither
      .left.map(_ => ConstructError.from(s"Couldn't parse enum $name from: ${node.asYaml}"))
