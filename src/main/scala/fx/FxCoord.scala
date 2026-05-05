package fx

import javafx.beans.binding.DoubleBinding
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene as jfxs
import scalafx.Includes.{jfxNode2sfx, jfxObservableValue2sfx, jfxParent2sfx}
import scalafx.beans.binding.Bindings
import scalafx.beans.property.ReadOnlyDoubleProperty
import scalafx.scene.{Node, Parent}

import scala.annotation.tailrec
import scala.collection.mutable


class GrandParentXBinding(node: Node, targetParent: Parent) extends GrandParentCoordBinding(node, targetParent):
  def getCoord(node: Node): ReadOnlyDoubleProperty = node.layoutX

class GrandParentYBinding(node: Node, targetParent: Parent) extends GrandParentCoordBinding(node, targetParent):
  def getCoord(node: Node): ReadOnlyDoubleProperty = node.layoutY

sealed abstract class GrandParentCoordBinding(node: Node, targetParent: Parent) extends DoubleBinding:
  def getCoord(node: Node): ReadOnlyDoubleProperty

  protected def computeValue: Double =
    var value = 0.0
    forAllParent(node)(value += getCoord(_).get)
    value

  val listener: ChangeListener[jfxs.Node] = (_, oldParent, newParent) =>
    forAllParent(oldParent): parent =>
      unbind(getCoord(parent))
      parent.parent.removeListener(listener)
    forAllParent(newParent): parent =>
      bind(getCoord(parent))
      parent.parent.addListener(listener)
    invalidate()
  listener.changed(null, null, node)

  @tailrec private def forAllParent(node: Node)(func: Node => Unit): Unit =
    if (node == null || node == targetParent) ()
    else
      func(node)
      forAllParent(node.parent())(func)
