package player.default.control

import atlantafx.base.controls.ProgressSliderSkin
import constant.{Tr, Translate}
import core.DailyRandom
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import javafx.scene.control as jfxsc
import javafx.scene.input.KeyCode
import org.kordamp.ikonli.fluentui.{FluentUiFilledAL, FluentUiFilledMZ}
import Controls.IconView
import scalafx.Includes.{jfxObservableValue2sfx, jfxSkin2sfxSkin, jfxText2sfxText}
import scalafx.animation.Interpolator.EaseBoth
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.*
import scalafx.scene.input.KeyEvent
import scalafx.scene.layout.{Background, HBox, Priority, Region}
import scalafx.scene.paint.Color
import scalafx.scene.text.Font
import scalafx.util.Duration
import uk.co.caprica.vlcj.player.base.TrackDescription
import util.fx.{AutoInsets, addEventFilter}

import java.util.function.UnaryOperator


private trait Menus extends FloatingTrackMenus:

  def setSpeed(value: Float): Unit = speedMenu.setSpeed(value)
  def isSpeedShown: Boolean = speedMenu.showing()


  private val buttonPadding = Insets(
    left    = 12,
    right   = 12,
    top     = 6,
    bottom  = 6,
  )
  private def hboxSpacing = new Region:
    hgrow = Priority.Always


  val settingsMenu: FloatingMenu = new FloatingMenu:

    children += new FloatingMenu.SelectableItem:
      label.text <== Tr.crop
      label.graphic = IconView(FluentUiFilledAL.CROP_24, 24)
      selectIcon.visible = false
      private val animation = Timeline(
        KeyFrame(Duration(0), KeyValue(player.videoCropCoef, 0)),
        KeyFrame(Duration(250), KeyValue(player.videoCropCoef, 1, EaseBoth)),
      )
      onMouseClicked = event =>
        selectIcon.visible() = !selectIcon.visible()
        animation.setRate(if (selectIcon.visible()) 1 else -1)
        animation.play()
        event.consume()


  protected val speedMenu = new SpeedMenu
  protected class SpeedMenu extends FloatingMenu.Dependant(settingsMenu):
    thisMenu =>

    spacing = inset
    titleLabel.text <== Tr.speed

    settingsMenu.children += new FloatingMenu.DependantMenuItem(this):
      text <== Tr.speed
      graphic = IconView(FluentUiFilledMZ.TOP_SPEED_24, 24)

    private val label = new Label:
      thisMenu.children += this
      alignment = Pos.Center
      maxWidth = Double.MaxValue
      margin = Insets(inset)
      font = Font(26)
      textFill = Color.White

    private val slider = new Slider(0.3, 2, 1):
      thisMenu.children += this
      margin = AutoInsets(left = inset * 1.5, right = inset * 1.5)
      minWidth = 200
      skin = ProgressSliderSkin(this)
      value.addListener: (_,_,number) =>
        if (!programChange)
          player.setSpeedX10((number.doubleValue * 10).round.toByte)

    children += new HBox(shortInset):
      margin = Insets(inset)
      children = List(0.5, 1.0, 2.0, 4.0).flatMap(hboxSpacing :: button(_) :: Nil).tail
      private def button(value: Double) = new Button:
        padding = buttonPadding
        background <== hoverableBg(this)
        textFill = Color.White
        text = value.toString
        onMouseClicked = _ => player.setSpeedX10((value * 10).round.toByte)

    private var programChange = false
    def setSpeed(speed: Float): Unit =
      programChange = true
      slider.value = speed
      label.text = s"$speed x"
      programChange = false
    setSpeed(1)


  protected val customTitleMenu: FloatingMenu.Dependant = new FloatingMenu.Dependant(settingsMenu):
    thisMenu =>

    background = Background fill Color.Black.opacity(0.9)
    titleLabel.text <== Tr.title
    minWidth = 250

    settingsMenu.children += new FloatingMenu.DependantMenuItem(this):
      text <== Tr.title
      graphic = IconView(FluentUiFilledMZ.TEXTBOX_ALIGN_TOP_24, 24)

    private val input = new TextArea:
      thisMenu.children += this
      wrapText = true
      prefRowCount = 2
      font = Font(15)
      style =
        "-fx-background-color: transparent;" +
        "-fx-text-fill: white;" +
        "-fx-prompt-text-fill: gray;"

      holder.visible.addListener: (_,_,visible) =>
        if (visible)
          val index = DailyRandom().random.nextInt(funnyFilmNames.length)
          promptText <== funnyFilmNames(index)
          text() = player.title()

      this.addEventFilter(KeyEvent.KeyPressed): event =>
        if (event.getCode == KeyCode.ENTER)
          save()
          event.consume()

      private val formatter: UnaryOperator[jfxsc.TextFormatter.Change] = change =>
        if (change.isContentChange)
          val text = change.getText
          if (text.contains('\n') || text.contains('\r'))
            change.setText(text.replaceAll("(?:\\R|\\t)+", " "))
        change
      textFormatter = TextFormatter(formatter)

    children += new HBox:
      margin = Insets(inset)
      children += new Button:
        padding = buttonPadding
        background <== hoverableBg(this)
        textFill = Color.White
        text <== Tr.default
        onMouseClicked = _ => saveDefault()

      children += hboxSpacing

      children += new Button:
        padding = buttonPadding
        background <== hoverableBg(this)
        textFill = Color.White
        text <== Tr.save
        onMouseClicked = _ => save()

    private def save(): Unit =
      if (input.text().isBlank) saveDefault()
      else player.setCustomTitle(input.text())

    private def saveDefault(): Unit =
      player.setCustomTitle("")
      input.text() = player.title()


private val funnyFilmNames: IArray[Translate] = IArray(
  Translate("The Killer Bra", "Лифчик-убийца"),
  Translate("Killer Condom", "Презерватив-убийца"),
  Translate("Hedgehog in the Fog", "Ёжик в тумане"),
  Translate("Sausage Party", "Сосисочная вечеринка"),
  Translate("Our Dad Mayonnaise", "Наш папа — майонез"),
  Translate("Attack of the Killer Donuts", "Нападение пончиков-убийц"),
  Translate("Killer Barbys vs. Dracula", "Убийцы Барби против Дракулы"),
  Translate("Attack of the Killer Tomatoes", "Нападение помидоров-убийц"),
  Translate("Gayniggers from Outer Space", "Геи-ниггеры из далёкого космоса"),
  Translate("Hollywood Chainsaw Hookers", "Голливудские шлюхи с бензопилами"),
  Translate("Death Bed: The Bed That Eats", "На смертном одре: Постель-людоед"),
  Translate("Cannibal Women in the Avocado Jungle of Death", "Женщины-каннибалы в смертельных джунглях авокадо"),
  Translate(
    "The Man with the Smallest Penis in Existence and the Electron Microscope Technician Who Loved Him",
    "Человек с самым маленьким пенисом в мире и техник электронного микроскопа, которая любила его",
  ),
  Translate(
    "I Killed My Lesbian Wife, Hung Her on a Meat Hook, and Now I Have a Three-Picture Deal at Disney",
    "Я убил жену-лесбиянку, повесил ее на мясной крюк, и теперь у меня контракт с Диснеем на три фильма",
  ),
)
