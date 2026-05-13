package config

import atlantafx.base.theme as afxbt
import org.virtuslab.yaml.Node.ScalarNode
import org.virtuslab.yaml.{ConstructError, LoadSettings, Node, NodeOps, YamlCodec}
import scalafx.application.JFXApp3.userAgentStylesheet


object Theme:

  val map: Map[String, afxbt.Theme] = List(
    afxbt.CupertinoDark(),
    afxbt.CupertinoLight(),
    afxbt.Dracula(),
    afxbt.NordDark(),
    afxbt.NordLight(),
    afxbt.PrimerDark(),
    afxbt.PrimerLight(),
    afxbt.Theme.of("Old School", "", false),
  ).map(theme => theme.getName -> theme).toMap

  given YamlCodec[afxbt.Theme] with
    override def asNode(theme: afxbt.Theme): Node = ScalarNode(theme.getName)
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, afxbt.Theme] =
      Some(node)
        .collect { case ScalarNode(name, _) => name }
        .flatMap(Theme.map.get)
        .toRight(ConstructError.from(s"Couldn't parse Theme: ${node.asYaml}"))

  val config: Config.ObjectProp[afxbt.Theme] = Config.ObjectProp(this, afxbt.PrimerLight())
  config.subscribe { theme => userAgentStylesheet = Some(theme.getUserAgentStylesheet).filter(_.nonEmpty) }
