package config

import atlantafx.base.theme as afxbt
import atlantafx.base.theme.*
import constant.{Tr, Translate}
import javafx.application.ColorScheme
import javafx.beans.InvalidationListener
import org.virtuslab.yaml.Node.ScalarNode
import org.virtuslab.yaml.{ConstructError, LoadSettings, Node, NodeOps, YamlCodec}
import scalafx.Includes.jfxProperty2sfx
import scalafx.application.JFXApp3.userAgentStylesheet
import scalafx.application.Platform

import scala.language.implicitConversions


sealed trait Theme:
  val name: String
  override def toString: String = name

class SingleTheme(val name: String, val value: String, val isDark: Boolean) extends Theme
class LightDarkTheme(val name: String, val light: String, val dark: String) extends Theme


object Theme:

  private implicit def getStyleSheet(theme: afxbt.Theme): String = theme.getUserAgentStylesheet

  private val default = LightDarkTheme("Primer", new PrimerLight, new PrimerDark)
  val values: IArray[Theme] = IArray(
    default,
    LightDarkTheme("Cupertino", new CupertinoLight, new CupertinoDark),
    LightDarkTheme("Nord", new NordLight, new NordDark),
    SingleTheme("Dracula", new Dracula, isDark = true),
    SingleTheme("Old School", "", isDark = false),
  )


  implicit val codec: YamlCodec[Theme] = new YamlCodec:
    override def asNode(theme: Theme): Node = ScalarNode(theme.name)
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, Theme] =
      Some(node)
        .collect { case ScalarNode(name, _) => name }
        .flatMap(name => values.find(_.name == name))
        .toRight(ConstructError.from(s"Couldn't parse Theme: ${node.asYaml}"))


  val config = Config.ObjectProp(this, default: Theme)

  enum Darkness(val name: Translate) derives YamlEnumCodec:
    case System extends Darkness(Tr.system)
    case Light  extends Darkness(Tr.light)
    case Dark   extends Darkness(Tr.dark)
  object Darkness:
    val config = Config.ObjectProp(this, Darkness.System: Darkness)

  private val listener: InvalidationListener = _ =>
    val styleSheet = config() match
      case single: SingleTheme => single.value
      case multi: LightDarkTheme => Darkness.config() match
        case Darkness.Light => multi.light
        case Darkness.Dark => multi.dark
        case Darkness.System => Platform.preferences.colorScheme() match
          case ColorScheme.LIGHT => multi.light
          case ColorScheme.DARK => multi.dark
    userAgentStylesheet = Some(styleSheet).filter(_.nonEmpty)

  config.addListener(listener)
  Darkness.config.addListener(listener)
  Platform.preferences.colorScheme.addListener(listener)
  listener.invalidated(null)
