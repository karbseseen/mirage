package fx

import config.SplitPositionConfig
import javafx.scene.layout.{Background, BackgroundFill, CornerRadii}
import scalafx.geometry.Insets
import scalafx.scene.control.SplitPane
import scalafx.scene.paint.Paint

import scala.jdk.CollectionConverters.*


object AutoInsets:
  def apply(
    top: Double = 0,
    right: Double = 0,
    bottom: Double = 0,
    left: Double = 0,
  ) = Insets(top, right, bottom, left)


object AutoBg:
  def fill(fill: Paint = null, radii: CornerRadii = null, insets: Insets = null): Background =
    Background(BackgroundFill(fill, radii, insets))

  extension (bg: Background)
    def +(other: Background): Background = Background(
      (bg.getFills.asScala ++ other.getFills.asScala).toArray,
      (bg.getImages.asScala ++ other.getImages.asScala).toArray,
    )


abstract class AutoSplitPane extends SplitPane:
  def splitName: String
  SplitPositionConfig(splitName, this)
