package config


object Conf:
  val mediaFile             = Config.StringProp(this, "mediaFile")
  val torrentFile           = Config.StringProp(this, "torrentFile")
  val torrentSave           = Config.StringProp(this, "torrentSave")
  val torrentLocalDiscovery = Config.BoolProp(this, "torrentLocalDiscovery", false)
  val torrentDhtDiscovery   = Config.BoolProp(this, "torrentDhtDiscovery", false)
