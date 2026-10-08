package vlc.control

import fx.{AutoBg, AutoInsets, GrandParentXBinding, GrandParentYBinding}
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.event as jfxe
import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import org.kordamp.ikonli.fluentui.FluentUiFilledAL
import scalafx.Includes.{jfxBackground2sfx, jfxColor2sfx, jfxNode2sfx, jfxObservableValue2sfx, jfxParent2sfx, jfxProperty2sfx}
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.binding.{Bindings, BooleanExpression}
import scalafx.beans.property.ReadOnlyBooleanWrapper
import scalafx.collections.ObservableBuffer
import scalafx.delegate.AlignmentDelegate
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.{Cursor, Node}
import scalafx.scene.control.Label
import scalafx.scene.input.{KeyEvent, MouseEvent}
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.util.Duration
import util.let

import java.lang
import scala.collection.mutable
import scala.compiletime.uninitialized


private abstract class FloatingMenu(using parent: FloatingMenu.Parent) extends VBox:
  parent._menus += this
  cursor = parent.funnyCursor

  minWidth = 125
  fillWidth = true
  background = AutoBg fill Color.Black.opacity(menuBgOpacity)
  clip = new Rectangle:
    width <== FloatingMenu.this.width
    height <== FloatingMenu.this.height
    arcWidth = menuCornerRadii * 2
    arcHeight = menuCornerRadii * 2
  border = BorderStroke(
    Color.White.opacity(0.5),
    BorderStrokeStyle.Solid,
    CornerRadii(menuCornerRadii),
    BorderWidths(1),
  ).let(Border(_))
  onMouseClicked = _.consume()

  private val _holder = new FloatingMenu.Holder:
    children.add(FloatingMenu.this)
    disable <== FloatingMenu.this.disable
  def holder: StackPane = _holder
  def viewMode: SimpleObjectProperty[FloatingMenu.ViewMode] = _holder.viewMode
  def showing: ObservableValue[lang.Boolean] = _holder.showing
  def bindControl(control: FloatingMenu.Control, controls: Controls): Unit =
    _holder.bindControl(control, controls)


private object FloatingMenu:

  private inline val corderRadii = 15.0
  enum ViewMode:
    case Hide, Hover, WeakHover, Always

  private class Holder extends StackPane:
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize
    alignmentInParent = Pos.TopLeft
    opacity = 0
    visible <== opacity.isNotEqualTo(0)
    managed <== visible

    val viewMode = SimpleObjectProperty(this, "viewMode", ViewMode.Hover: ViewMode)
    var showing: ObservableValue[lang.Boolean] = uninitialized
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
      showing = viewMode.flatMap[lang.Boolean]:
        case ViewMode.Hide      => ReadOnlyBooleanWrapper(false)
        case ViewMode.Hover     => control.menuHover
        case ViewMode.WeakHover |
             ViewMode.Always    => ReadOnlyBooleanWrapper(true)
      showing.addListener: (_,_,show) =>
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

  class DependantMenuItem(dependant: Dependant) extends Label with Item:
    textFill = Color.White
    onMouseClicked = event =>
      dependant.viewMode() = ViewMode.Always
      dependant.master.viewMode() = ViewMode.Hide
      event.consume()


  class Dependant(val master: FloatingMenu)(using Parent) extends FloatingMenu:
    viewMode() = ViewMode.Hide
    holder.visible.addListener: (_,_,visible) =>
      if (!visible && viewMode() != ViewMode.Hide)
        viewMode() = ViewMode.Hide
        if (master.viewMode() == ViewMode.Hide)
          master.viewMode() = ViewMode.Hover

    protected val titleLabel: Label = new Label:
      alignment = Pos.Center
      textFill = Color.White
    children += new StackPane:
      children += new StackPane:
        alignment = Pos.CenterLeft
        padding = Insets(longInset)
        children += IconView(FluentUiFilledAL.CHEVRON_LEFT_16, 18)
        onMouseClicked = event =>
          exitMenu()
          event.consume()
      children += titleLabel

    protected def exitMenu(): Unit =
      master.viewMode() = ViewMode.WeakHover
      viewMode() = ViewMode.Hide


  trait Parent extends ControlsBase:
    protected implicit val self: this.type = this
    implicit val funnyCursor: Cursor = getFunnyCursor

    private[FloatingMenu] val _menus = mutable.Buffer[FloatingMenu]()
    def menus: collection.Seq[FloatingMenu] = _menus

    private val eventHandler: EventHandler[jfxe.Event] = event =>
      val weakHoverMenus = menus.filter: menu =>
        (menu.viewMode() == ViewMode.WeakHover || menu.viewMode() == ViewMode.Always) && !menu.hover()
      weakHoverMenus.foreach(_.viewMode() = ViewMode.Hover)
      if (weakHoverMenus.nonEmpty)
        restartAutoHide(autoHideTimeout / 2)
        event.consume()

    stage.addEventFilter(MouseEvent.MousePressed, eventHandler)
    stage.addEventFilter(KeyEvent.KeyPressed, event =>
      if (event.getCode == KeyCode.ESCAPE) eventHandler.handle(event)
    )
