package config


object Language:
  val config: Config.ObjectProp[Language] = Config.ObjectProp(this, en)

enum Language(val name: String, val default: Option[Language] = None) derives YamlEnumCodec:
  case en extends Language("English")
  case ru extends Language("Русский", Some(en))
