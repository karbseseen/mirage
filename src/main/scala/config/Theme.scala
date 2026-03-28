package config

import atlantafx.base.theme as afxbt
import org.virtuslab.yaml.Node.ScalarNode
import org.virtuslab.yaml.{ConstructError, LoadSettings, Node, NodeOps, YamlCodec}
import scalafx.application.JFXApp3.userAgentStylesheet


object Theme:
  given Config.Default[afxbt.Theme] = afxbt.PrimerLight()

  val map: Map[String, afxbt.Theme] = List(
    new afxbt.CupertinoDark(),
    new afxbt.CupertinoLight(),
    new afxbt.Dracula(),
    new afxbt.NordDark(),
    new afxbt.NordLight(),
    new afxbt.PrimerDark(),
    implicitly[Config.Default[afxbt.Theme]].value,
    afxbt.Theme.of("Old School", "", false),
  ).map(theme => theme.getName -> theme).toMap

  given YamlCodec[afxbt.Theme] with
    override def asNode(theme: afxbt.Theme): Node = ScalarNode(theme.getName)
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, afxbt.Theme] =
      Some(node)
        .collect { case ScalarNode(name, _) => name }
        .flatMap(Theme.map.get)
        .toRight(ConstructError.from(s"Couldn't parse Theme: ${node.asYaml}"))

  given Config[afxbt.Theme] = Config.derived[afxbt.Theme]

  Config[afxbt.Theme].subscribe { theme => userAgentStylesheet = Some(theme.getUserAgentStylesheet).filter(_.nonEmpty) }
