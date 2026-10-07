package vlc.control

import atlantafx.base.controls.ProgressSliderSkin
import constant.Tr
import fx.AutoInsets
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import org.kordamp.ikonli.fluentui.{FluentUiFilledAL, FluentUiFilledMZ}
import scalafx.Includes.{jfxProperty2sfx, jfxSkin2sfxSkin, jfxText2sfxText}
import scalafx.animation.Interpolator.EaseBoth
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.{HBox, Priority, Region, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.text.Font
import scalafx.util.Duration
import uk.co.caprica.vlcj.player.base.TrackDescription
import vlc.control.FloatingMenu.ViewMode


private trait Menus extends FloatingMenu.Parent:
  import stage.player

  val videoMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.video.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.video.setTrack(vlcId)

  val audioMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.audio.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.audio.setTrack(vlcId)

  val titleMenu: FloatingTracks = new FloatingTracks:
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

    children += new Label with FloatingMenu.Item:
      textFill = Color.White
      text <== Tr.speed
      graphic = IconView(FluentUiFilledMZ.TOP_SPEED_24, 24)
      onMouseClicked = event =>
        speedMenu.viewMode() = ViewMode.WeakHover
        settingsMenu.viewMode() = ViewMode.Hide
        event.consume()


  sealed trait SpeedMenu:
    def setSpeed(speed: Float): Unit

  val speedMenu: FloatingMenu & SpeedMenu = new FloatingMenu with SpeedMenu:
    floatingMenu =>

    viewMode() = ViewMode.Hide
    spacing = inset
    holder.visible.addListener: (_,_,visible) =>
      if (!visible && viewMode() != ViewMode.Hide)
        settingsMenu.viewMode() = ViewMode.Hover
        speedMenu.viewMode() = ViewMode.Hide

    children += new StackPane:
      children += new StackPane:
        alignment = Pos.CenterLeft
        padding = Insets(longInset)
        children += IconView(FluentUiFilledAL.CHEVRON_LEFT_16, 18)
        onMouseClicked = _ =>
          settingsMenu.viewMode() = ViewMode.WeakHover
          speedMenu.viewMode() = ViewMode.Hide
      children += new Label:
        alignment = Pos.Center
        textFill = Color.White
        text <== Tr.speed

    private val label = new Label:
      floatingMenu.children += this
      alignment = Pos.Center
      maxWidth = Double.MaxValue
      margin = Insets(inset)
      font = Font(26)
      textFill = Color.White

    private val slider = new Slider(0.3, 2, 1):
      floatingMenu.children += this
      margin = AutoInsets(left = inset * 1.5, right = inset * 1.5)
      minWidth = 200
      skin = ProgressSliderSkin(this)
      value.addListener: (_,_,number) =>
        if (!programChange)
          val rounded = (number.doubleValue * 10).round.toDouble / 10
          stage.updateSpeed(rounded.toFloat, send = true)

    children += new HBox(shortInset):
      margin = Insets(inset)
      children = List(0.5, 1.0, 2.0, 4.0).flatMap(space :: button(_) :: Nil).tail
      private def space = new Region:
        hgrow = Priority.Always
      private def button(value: Double) = new Button:
        padding = Insets(
          left    = 10,
          right   = 10,
          top     = 4,
          bottom  = 4,
        )
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
