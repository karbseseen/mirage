package p2p

import config.Config


object Room:

  private var _instance = Option.empty[P2p]
  def instance: Option[P2p] = _instance
  val config: Config.StringProp = Config.StringProp(this, "roomName")


