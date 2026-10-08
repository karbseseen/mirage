package vlc.control

import atlantafx.base.controls.ProgressSliderSkin
import constant.Tr
import core.DailyRandom
import fx.AutoInsets
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import javafx.scene.input.{KeyCode, KeyEvent}
import org.kordamp.ikonli.fluentui.{FluentUiFilledAL, FluentUiFilledMZ}
import scalafx.Includes.{jfxSkin2sfxSkin, jfxStringProperty2sfx, jfxText2sfxText}
import scalafx.animation.Interpolator.EaseBoth
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Button, Label, Slider, TextArea}
import scalafx.scene.layout.{Background, HBox, Priority, Region}
import scalafx.scene.paint.Color
import scalafx.scene.text.Font
import scalafx.util.Duration
import uk.co.caprica.vlcj.player.base.TrackDescription


private trait Menus extends FloatingMenu.Parent:
  import stage.player


  private val buttonPadding = Insets(
    left    = 12,
    right   = 12,
    top     = 6,
    bottom  = 6,
  )
  private def hboxSpacing = new Region:
    hgrow = Priority.Always


  val videoMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.video.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.video.setTrack(vlcId)

  val audioMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.audio.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.audio.setTrack(vlcId)

  val subtitleMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.subpictures.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.subpictures.setTrack(vlcId)
    override def removeItem(vlcId: Int): Unit = if (vlcId != -1) super.removeItem(vlcId)
    children += new TrackItem(-1) { label.text <== Tr.noSubtitles }


  val settingsMenu: FloatingMenu = new FloatingMenu:

    children += new FloatingMenu.SelectableItem:
      label.text <== Tr.crop
      label.graphic = IconView(FluentUiFilledAL.CROP_24, 24)
      selectIcon.visible = false
      private val animation = Timeline(
        KeyFrame(Duration(0), KeyValue(stage.videoCropCoef, 0)),
        KeyFrame(Duration(250), KeyValue(stage.videoCropCoef, 1, EaseBoth)),
      )
      onMouseClicked = event =>
        selectIcon.visible() = !selectIcon.visible()
        animation.setRate(if (selectIcon.visible()) 1 else -1)
        animation.play()
        event.consume()


  sealed trait SpeedMenu:
    def setSpeed(speed: Float): Unit

  val speedMenu: FloatingMenu.Dependant & SpeedMenu = new FloatingMenu.Dependant(settingsMenu) with SpeedMenu:
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
          val rounded = (number.doubleValue * 10).round.toDouble / 10
          stage.updateSpeed(rounded.toFloat, send = true)

    children += new HBox(shortInset):
      margin = Insets(inset)
      children = List(0.5, 1.0, 2.0, 4.0).flatMap(hboxSpacing :: button(_) :: Nil).tail
      private def button(value: Double) = new Button:
        padding = buttonPadding
        background <== hoverableBg(this)
        textFill = Color.White
        text = value.toString
        onMouseClicked = _ => stage.updateSpeed(value.toFloat, send = true)

    private var programChange = false
    def setSpeed(speed: Float): Unit =
      programChange = true
      slider.value = speed
      label.text = s"$speed x"
      programChange = false


  val customTitleMenu: FloatingMenu.Dependant = new FloatingMenu.Dependant(settingsMenu):
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
          val index = DailyRandom().random.nextInt(Tr.funnyFilmNames.length)
          promptText <== Tr.funnyFilmNames(index)
          text() = stage.title()

      this.addEventFilter(KeyEvent.KEY_PRESSED, event =>
        if (event.getCode == KeyCode.ENTER)
          stage.customTitle() = text()
          event.consume()
      )

    children += new HBox:
      margin = Insets(inset)
      children += new Button:
        padding = buttonPadding
        background <== hoverableBg(this)
        textFill = Color.White
        text <== Tr.default
        onMouseClicked = _ =>
          stage.customTitle() = ""
          input.text() = stage.title()

      children += hboxSpacing

      children += new Button:
        padding = buttonPadding
        background <== hoverableBg(this)
        textFill = Color.White
        text <== Tr.save
        onMouseClicked = _ =>
          stage.customTitle() = input.text()
