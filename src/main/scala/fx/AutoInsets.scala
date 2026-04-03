package fx

import scalafx.geometry.Insets


object AutoInsets:
  def apply(
    top: Double = 0,
    right: Double = 0,
    bottom: Double = 0,
    left: Double = 0,
  ) = Insets(top, right, bottom, left)
