package config

import javafx.beans.property as jfxbp
import org.virtuslab.yaml.Node.{MappingNode, ScalarNode}
import org.virtuslab.yaml.{Node, NodeOps, StringOps, YamlCodec}
import scalafx.Includes.jfxObservableValue2sfx
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

  private def parseMap(root: MappingNode) = root.mappings.view
    .flatMap {
      case (ScalarNode(key, _), value) => Some(key, value: Node | Config[?])
      case invalid => System.err.println(s"Couldn't parse ${MappingNode(invalid).asYaml}"); None
    }
    .to(mutable.Map)

  private def parseFile = for
    yaml <- Try(Files.readString(file.toPath)).printError(_ => s"Couldn't read $fileName")
    root <- yaml.asNode.toTry.printError(_ => s"Couldn't parse $fileName")
    mapRoot <- Try(root).collect { case root: MappingNode => root }.printError(_ => s"Couldn't parse $fileName as map")
  yield parseMap(mapRoot)

  private val map = parseFile getOrElse mutable.Map.empty

  private lazy val updateHook: Unit = sys.addShutdownHook {
    val values = map.view.flatMap {
      case (key, node: Node) => Some(ScalarNode(key) -> node)
      case (key, config: Config[?]) => config.node.map(ScalarNode(key) -> _)
    }.toSeq
    Files.writeString(file.toPath, MappingNode(values*).asYaml)
  }


  class Default[T](val value: T)
  object Default:
    implicit def apply[T](value: T): Default[T] = new Default(value)

  def derived[T : YamlCodec](using ct: ClassTag[T], default: Default[T]) =
    new jfxbp.SimpleObjectProperty[T](this, ct.runtimeClass.getSimpleName.toLowerCase, default.value) with Config[T]
  
  def apply[T](using config: Config[T]): Config[T] = config


trait Config[T : YamlCodec] extends jfxbp.Property[T]:
  import Config.*

  private def node = Option(getValue).map(summon[YamlCodec[T]].asNode)

  Config.map.updateWith(getName) {
    case taken@Some(_: Config[?]) =>
      System.err.println(s"Config with name $getName is already registered")
      taken
    case available =>
      Try(available).collect { case Some(node: Node) => node }
        .flatMap { implicitly[YamlCodec[T]].construct(_).toTry }.printError(_ => s"Couldn't parse config $getName")
        .foreach(setValue)
      this.onChange(updateHook)
      Some(this)
  }
