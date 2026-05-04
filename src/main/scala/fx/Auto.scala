package fx

import config.SplitPositionConfig
import scalafx.geometry.Insets
import scalafx.scene.control.SplitPane
import scalafx.scene.layout.{Background, BackgroundFill, CornerRadii}
import scalafx.scene.paint.Paint


object AutoInsets:
  def apply(
    top: Double = 0,
    right: Double = 0,
    bottom: Double = 0,
    left: Double = 0,
  ) = Insets(top, right, bottom, left)


object AutoBg:
  def fill(fill: Paint = null, radii: CornerRadii = null, insets: Insets = null): Background =
    Background(Array(BackgroundFill(fill, radii, insets)))


abstract class AutoSplitPane extends SplitPane:
  def splitName: String
  SplitPositionConfig(splitName, this)
