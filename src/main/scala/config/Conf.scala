package config

import javafx.beans.property.SimpleStringProperty


object Conf:
  val torrentFile = new SimpleStringProperty(Config, "torrentFile", "") with Config[String]
  val torrentSave = new SimpleStringProperty(Config, "torrentSave", "") with Config[String]
