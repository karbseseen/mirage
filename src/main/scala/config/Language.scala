package config

import config.Config.Default


object Language:
  given Default[Language] = Language.en

enum Language(val name: String, val default: Option[Language] = None) derives YamlEnumCodec, Config:
  case en extends Language("English")
  case ru extends Language("Русский", Some(en))
