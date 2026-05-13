package config

import core.main.MainApp
import javafx.beans.property.{Property, SimpleObjectProperty, SimpleStringProperty}
import org.virtuslab.yaml.Node.{MappingNode, ScalarNode}
import org.virtuslab.yaml.{Node, NodeOps, StringOps, YamlCodec, YamlDecoder, YamlEncoder}
import util.{JavaUtil, printError}

import java.io.File
import java.nio.file.Files
import scala.collection.mutable
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try


object Config:
  private val fileName = "data.yaml"
  private val file = new File(JavaUtil.jarFile.getParentFile, fileName)

  private type Getter = () => Option[Node]

  private def parseMap(root: MappingNode) = root.mappings.view
    .flatMap {
      case (ScalarNode(key, _), value) => Some(key, value: Node | Getter)
      case invalid => System.err.println(s"Couldn't parse ${MappingNode(invalid).asYaml}"); None
    }
    .to(mutable.Map)

  private def parseFile = for
    yaml <- Try(Files.readString(file.toPath)).printError(_ => s"Couldn't read $fileName")
    root <- yaml.asNode.toTry.printError(_ => s"Couldn't parse $fileName")
    mapRoot <- Try(root).collect { case root: MappingNode => root }.printError(_ => s"Couldn't parse $fileName as map")
  yield parseMap(mapRoot)

  private val map = parseFile getOrElse mutable.Map.empty

  MainApp.shutdownHook:
    val values = map.view.flatMap:
      case (key, node: Node) => Some(ScalarNode(key) -> node)
      case (key, getter: Getter) => getter().map(ScalarNode(key) -> _)
    Files.writeString(file.toPath, MappingNode(values.toSeq*).asYaml)

  def register[T](name: String)(get: => Option[T])(using codec: YamlCodec[T]): Option[T] =
    val initValue = map.get(name)
      .collect { case node: Node => node }
      .flatMap { codec.construct(_).toTry.printError(_ => s"Couldn't parse config $name").toOption }
    map.updateWith(name):
      case taken@Some(_: Getter) =>
        System.err.println(s"Config with name $name is already registered")
        taken
      case _ => Some(() => get.map(codec.asNode))
    initValue

  def register[T : YamlCodec](property: Property[T]): Unit =
    val initValue = register(property.getName)(Option(property.getValue))
    initValue.foreach(property.setValue)


  class StringProp(bean: AnyRef, name: String, default: String = "") extends SimpleStringProperty(bean, name, default):
    Config.register(this)

  class ObjectProp[T : YamlCodec](bean: AnyRef, name: String, default: T)
    extends SimpleObjectProperty[T](bean, name, default)
  :
    def this(bean: AnyRef, default: T)(using tag: ClassTag[T]) =
      this(bean, tag.runtimeClass.getSimpleName.toLowerCase, default)
    Config.register(this)


given [T](using YamlDecoder[T], YamlEncoder[T]): YamlCodec[T] = YamlCodec.make
given [K, V](using codec: YamlCodec[Map[K, V]]): YamlCodec[mutable.Map[K, V]] = codec.mapInvariant(_.to(mutable.Map))(_.toMap)
