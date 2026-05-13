package config

import javafx.beans.property.SimpleStringProperty


object Conf:
  val mediaFile   = Config.StringProp(Config, "mediaFile")
  val torrentFile = Config.StringProp(Config, "torrentFile")
  val torrentSave = Config.StringProp(Config, "torrentSave")
