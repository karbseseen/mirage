package util.fx

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.util.Subscription
import scalafx.beans.property.DoubleProperty
import scalafx.scene.Node
import util.also

import java.lang


extension (node: Node)
  def animateLayoutX(xProperty: DoubleProperty = node.translateX, exceptFirst: Boolean = true): Subscription =
    val xAnimator = LayoutMoveAnimator(xProperty, exceptFirst).also(node.layoutX.addListener)
    () => node.layoutX.removeListener(xAnimator)

  def animateLayoutY(yProperty: DoubleProperty = node.translateY, exceptFirst: Boolean = true): Subscription =
    val yAnimator = LayoutMoveAnimator(yProperty, exceptFirst).also(node.layoutY.addListener)
    () => node.layoutY.removeListener(yAnimator)


private class LayoutMoveAnimator(targetProperty: DoubleProperty, exceptFirst: Boolean) extends ChangeListener[lang.Number]:

  private var skipFirst = exceptFirst
  private var animation: Option[Animation] = None

  def changed(o: ObservableValue[? <: lang.Number], oldValue: lang.Number, newValue: lang.Number): Unit =
    val diff = oldValue.doubleValue + targetProperty() - newValue.doubleValue
    if (diff.abs > 1e-9)
      if (skipFirst)
        skipFirst = false
      else
        animation.foreach(_.stop())
        animation = Some:
          Timeline(
            KeyFrame(0,   easeValue(targetProperty, diff)),
            KeyFrame(210, easeValue(targetProperty, 0)),
          ).also(_.play())
