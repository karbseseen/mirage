package vlc.control

import fx.{AutoBg, AutoInsets, GrandParentXBinding, GrandParentYBinding}
import javafx.beans.property.SimpleBooleanProperty
import org.kordamp.ikonli.fluentui.FluentUiFilledAL
import scalafx.Includes.{jfxBackground2sfx, jfxNode2sfx, jfxObservableValue2sfx, jfxParent2sfx, jfxProperty2sfx}
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.binding.Bindings
import scalafx.collections.ObservableBuffer
import scalafx.delegate.AlignmentDelegate
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.*
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

  trait Item extends Region with AlignmentDelegate[?]:
    maxWidth = Double.MaxValue
    alignment = Pos.CenterLeft
    background <== hover.map(if (_) AutoBg.fill(bgHoverColor) else null)

  class SelectableItem extends HBox(inset * 2.5) with Item:
    padding = Insets(inset)
    val label: Label = new Label:
      textFill = Color.White
      hgrow = Priority.Always
      maxWidth = Double.MaxValue
    val selectIcon: IconView = new IconView(FluentUiFilledAL.CHECKMARK_16, 17):
      managed <== visible
    children = Seq[Node](label, selectIcon)
