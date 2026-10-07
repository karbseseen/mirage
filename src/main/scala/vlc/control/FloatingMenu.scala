package vlc.control

import fx.{AutoBg, AutoInsets, GrandParentXBinding, GrandParentYBinding}
import javafx.beans.property.SimpleBooleanProperty
import scalafx.Includes.{jfxBackground2sfx, jfxObservableValue2sfx, jfxParent2sfx, jfxProperty2sfx}
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.binding.Bindings
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Button
import scalafx.scene.layout.{Region, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.util.Duration


private abstract class FloatingMenu extends VBox:
  minWidth = 125
  fillWidth = true
  background = AutoBg.fill(bgColor)
  clip = new Rectangle:
    width <== FloatingMenu.this.width
    height <== FloatingMenu.this.height
    arcWidth = 15
    arcHeight = 15

  private val _holder = new FloatingMenu.Holder:
    children.add(FloatingMenu.this)
  def holder: StackPane = _holder
  def bindControl(control: FloatingMenu.Control, controls: Controls): Unit =
    _holder.bindControl(control, controls)


private object FloatingMenu:

  private class Holder extends StackPane:
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize
    alignmentInParent = Pos.TopLeft
    opacity = 0
    visible <== opacity.isNotEqualTo(0)
    managed <== visible

    def bindControl(control: Control, controls: Controls): Unit =
      val controlSceneX = parent.flatMap(GrandParentXBinding(control, _)).orElse(0)
      val controlSceneY = parent.flatMap(GrandParentYBinding(control, _)).orElse(0)

      padding <== Bindings.createObjectBinding(
        () => AutoInsets(bottom = controlSceneY().doubleValue - controls.layoutY()).delegate,
        controlSceneY, controls.layoutY
      )
      translateX <== Bindings.createDoubleBinding(
        () => (controlSceneX().doubleValue + (control.width() - width()) / 2) min (controls.width() - width()),
        controlSceneX, control.width, width, controls.width
      )
      translateY <== Bindings.createDoubleBinding(
        () => controlSceneY().doubleValue - height(),
        controlSceneY, height
      )

      val animation = new FadeTransition(Duration(230), this):
        fromValue = 0
        toValue = 1
        interpolator = EaseBoth

      val anyHover = hover || control.hover
      control.menuHover <== anyHover
      anyHover.addListener: (_, _, anyHover) =>
        animation.setRate(if (anyHover) 1 else -1)
        animation.play()

  trait Control extends Region:
    val menuHover = SimpleBooleanProperty(this, "menuHover")
    menuHover <== hover
    def bindMenu(menu: FloatingMenu, controls: Controls): Unit =
      menu.bindControl(this, controls)

  class Item extends Button:
    maxWidth = Double.MaxValue
    textFill = Color.White
    alignment = Pos.CenterLeft
    padding = Insets(
      left = inset * 2.5,
      right = inset * 2.5,
      top = inset,
      bottom = inset,
    )
    background <== hover.map(if (_) AutoBg.fill(bgHoverColor) else null)
