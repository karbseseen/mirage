package vlc.control

import fx.{AutoBg, AutoInsets, GrandParentXBinding, GrandParentYBinding}
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.event as jfxe
import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import org.kordamp.ikonli.fluentui.FluentUiFilledAL
import scalafx.Includes.{jfxBackground2sfx, jfxNode2sfx, jfxObservableValue2sfx, jfxParent2sfx, jfxProperty2sfx}
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.binding.{Bindings, BooleanExpression}
import scalafx.beans.property.ReadOnlyBooleanWrapper
import scalafx.collections.ObservableBuffer
import scalafx.delegate.AlignmentDelegate
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.input.{KeyEvent, MouseEvent}
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.util.Duration

import java.lang
import scala.collection.mutable
import scala.compiletime.uninitialized


private abstract class FloatingMenu(using parent: FloatingMenu.Parent) extends VBox:
  parent._menus += this

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
    disable <== FloatingMenu.this.disable
  def holder: StackPane = _holder
  def viewMode: SimpleObjectProperty[FloatingMenu.ViewMode] = _holder.viewMode
  def bindControl(control: FloatingMenu.Control, controls: Controls): Unit =
    _holder.bindControl(control, controls)


private object FloatingMenu:

  enum ViewMode:
    case Hover, WeakHover, Hide

  private class Holder extends StackPane:
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize
    alignmentInParent = Pos.TopLeft
    opacity = 0
    visible <== opacity.isNotEqualTo(0)
    managed <== visible

    val viewMode = SimpleObjectProperty(this, "viewMode", ViewMode.Hover: ViewMode)
    private var show: ObservableValue[lang.Boolean] = uninitialized
    hover.addListener: _ =>
      if (viewMode() == ViewMode.WeakHover)
        viewMode() = ViewMode.Hover

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

      control.holders() = this :: control.holders()
      show = viewMode.flatMap[lang.Boolean]:
        case ViewMode.Hover     => control.menuHover
        case ViewMode.WeakHover => ReadOnlyBooleanWrapper(true)
        case ViewMode.Hide      => ReadOnlyBooleanWrapper(false)
      show.addListener: (_,_,show) =>
        animation.setRate(if (show) 1 else -1)
        animation.play()

  trait Control extends Region:
    private[FloatingMenu] val holders = SimpleObjectProperty[List[Holder]](this, "holders", Nil)
    val menuHover: ObservableValue[lang.Boolean] = holders.flatMap: list =>
      (hover :: list.map(_.hover)).reduce[BooleanExpression](_ || _)

    def bindMenu(menu: FloatingMenu, controls: Controls): Unit =
      menu.bindControl(this, controls)

  trait Item extends Region with AlignmentDelegate[?]:
    padding = Insets(inset)
    maxWidth = Double.MaxValue
    alignment = Pos.CenterLeft
    background <== hover.map(if (_) AutoBg.fill(bgHoverColor) else null)

  class SelectableItem extends HBox(inset * 2.5) with Item:
    val label: Label = new Label:
      textFill = Color.White
      hgrow = Priority.Always
      maxWidth = Double.MaxValue
    val selectIcon: IconView = new IconView(FluentUiFilledAL.CHECKMARK_16, 17):
      managed <== visible
    children = Seq[Node](label, selectIcon)

  trait Parent extends ControlsBase:
    protected implicit val self: this.type = this

    private[FloatingMenu] val _menus = mutable.Buffer[FloatingMenu]()
    def menus: collection.Seq[FloatingMenu] = _menus

    private val eventHandler: EventHandler[jfxe.Event] = event =>
      val weakHoverMenus = menus.filter(_.viewMode() == ViewMode.WeakHover)
      weakHoverMenus.foreach(_.viewMode() = ViewMode.Hover)
      if (weakHoverMenus.nonEmpty)
        restartAutoHide(autoHideTimeout / 2)
        event.consume()

    stage.addEventFilter(MouseEvent.MouseClicked, eventHandler)
    stage.addEventFilter(KeyEvent.KeyPressed, event =>
      if (event.getCode == KeyCode.ESCAPE) eventHandler.handle(event)
    )
