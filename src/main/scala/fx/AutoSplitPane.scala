package fx

import config.SplitPositionConfig
import scalafx.scene.control.SplitPane


abstract class AutoSplitPane extends SplitPane:
  def splitName: String
  SplitPositionConfig(splitName, this)
