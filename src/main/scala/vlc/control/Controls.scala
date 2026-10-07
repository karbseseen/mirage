package vlc.control

import atlantafx.base.controls.ProgressSliderSkin
import fx.AutoInsets
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import javafx.beans.binding.StringBinding
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.paint.Stop
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.fluentui.{FluentUiFilledAL, FluentUiFilledMZ, FluentUiRegularMZ}
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxBackground2sfx, jfxNode2sfx, jfxProperty2sfx}
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.binding.{BooleanBinding, BooleanExpression}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.SceneIncludes.jfxSkin2sfxSkin
import scalafx.scene.control.{Button, Slider}
import scalafx.scene.layout.*
import scalafx.scene.paint.{Color, LinearGradient}
import scalafx.scene.text.Font
import scalafx.util.Duration
import vlc.{VlcStage, isFinished}

import scala.math.Integral.Implicits.infixIntegralOps


private[vlc] class Controls(val stage: VlcStage) extends VBox with Menus with AutoHide:
  import stage.player

  alignmentInParent = Pos.BottomCenter
  maxHeight = Region.UsePrefSize

  val seek: Slider = new Slider:
    private var wasPlaying = false
    hgrow = Priority.Always
    padding = AutoInsets(left = inset, right = inset)
    margin = AutoInsets(top = shortInset)
    background = Background fill new LinearGradient(
      0.5, 0, 0.5, 1,
      stops = Seq(Stop(0.2, Color.Transparent), Stop(0.8, Color.Black.opacity(backgroundOpacity))),
    )
    skin = ProgressSliderSkin(this)
    value.addListener: (_,_,value) =>
      if (pressed() && !valueChanging())
        if (player.isFinished) player.media.play(stage.media)
        player.controls.setTime(value.longValue)
        stage.updateState(time = value.longValue, seek = true, send = true)
    valueChanging.addListener: (_,_,changing) =>
      if (changing)
        wasPlaying = player.status.isPlaying
        if (wasPlaying) player.controls.setPause(true)
      else
        if (player.isFinished) player.media.play(stage.media)
        else if (wasPlaying) player.controls.setPause(false)
        player.controls.setTime(value.longValue)
        stage.updateState(pause = Some(!wasPlaying), time = value.longValue, seek = true, send = true)

  val playPauseIcon: FontIcon = IconView(FluentUiFilledMZ.PLAY_48, 28)
  val playPause: Button = new Button:
    padding = Insets(longInset)
    background <== hoverableBg(this)
    graphic = playPauseIcon
    onAction = _ => stage.togglePause()

  val volume: HBox = new HBox:
    background <== hoverableBg(this)
    alignment = Pos.CenterLeft
    padding = AutoInsets(right = longInset)

    private val slider = new Slider(0, 100, 100):
      value.addListener { (_,_,value) => player.audio.setVolume(value.intValue) }
      hgrow = Priority.Always
      skin = ProgressSliderSkin(this)

    private val button = new Button:
      background = Background.Empty
      onAction = _ => slider.value() = if (slider.value() > 0) 0 else 100
      graphic = new IconView(24):
        iconCodeProperty <== slider.value.map: d =>
          val i = d.intValue
          if (i <= 0)       FluentUiFilledMZ.SPEAKER_NONE_24
          else if (i < 100) FluentUiFilledMZ.SPEAKER_1_24
          else              FluentUiFilledMZ.SPEAKER_24

    children ++= Seq(button, slider)

    private val extraWidth = SimpleIntegerProperty(this, "extraWidth")
    private val animation = Timeline(
      KeyFrame(Duration(0),   KeyValue(extraWidth, 0),              KeyValue(slider.opacity, 0)),
      KeyFrame(Duration(250), KeyValue(extraWidth, 150, EaseBoth),  KeyValue(slider.opacity, 1, EaseBoth)),
    )

    slider.visible <== extraWidth.isNotEqualTo(0)
    slider.managed <== slider.visible

    minWidth = 0
    prefWidth <== button.width + extraWidth
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize

    val expand: BooleanBinding = hover || slider.pressed
    expand.addListener: (_,_,expand) =>
      animation.setRate(if (expand) 1 else -1)
      animation.play()

  val microphone: Button = new Button:
    padding = Insets(inset)
    background <== hoverableBg(this)
    graphic = new IconView(24):
      iconCodeProperty <==
        stage.phone.microphoneEnabled.map(if (_) FluentUiRegularMZ.MIC_ON_24 else FluentUiRegularMZ.MIC_OFF_24)
    onAction = _ =>
      stage.phone.microphoneEnabled() = !stage.phone.microphoneEnabled()

  val time: Button = new Button:
    padding = Insets(inset)
    font = Font(15)
    textFill = Color.White
    background <== hoverableBg(this)

    private var backward = false
    private val textBind = new StringBinding:
      bind(seek.value, seek.max)
      protected def computeValue: String = time1 + " / " + time2str(seek.max.toLong)
      private def time1 =
        if (backward) "-" + time2str(seek.max.toLong - seek.value.toLong)
        else time2str(seek.value.toLong)
      private def time2str(msTotal: Long): String =
        val sTotal = msTotal / 1000
        val (mTotal, s) = sTotal /% 60
        val (h, m) = mTotal /% 60
        if (h > 0) f"$h%d:$m%02d:$s%02d" else f"$m%02d:$s%02d"
    text <== textBind
    onAction = _ => { backward = !backward; textBind.invalidate() }

  private val space = new Region:
    hgrow = Priority.Always

  val tracks: HBox = new HBox:
    maxHeight = Region.UsePrefSize
    background = staticBg

    children = Seq(
      (videoMenu, FluentUiFilledMZ.VIDEO_24),
      (audioMenu, FluentUiFilledMZ.MUSIC_NOTE_24),
      (titleMenu, FluentUiFilledAL.CLOSED_CAPTION_24),
    ).map: (floating: FloatingTracks, icon: Ikon) =>
      new StackPane with FloatingMenu.Control:
        bindMenu(floating, Controls.this)
        padding = Insets(inset)
        background <== menuHover.map(if (_) staticBgHover else null)
        children += IconView(icon, 24)

    visible <== children.view.map[BooleanExpression](_.visible).reduce(_ || _)
    managed <== visible

  val settings: Button = new Button with FloatingMenu.Control:
    bindMenu(settingsMenu, Controls.this)
    padding = Insets(inset)
    background <== menuHover.map(if (_) staticHoveredBg else staticBg)
    graphic = IconView(FluentUiFilledMZ.SETTINGS_24, 24)

  val expand: Button = new Button:
    padding = Insets(inset)
    background <== hoverableBg(this)
    graphic = new IconView(24):
      iconCodeProperty <== stage.fullScreen.map:
        if (_) FluentUiFilledAL.ARROW_MINIMIZE_24 else FluentUiFilledAL.ARROW_MAXIMIZE_24
    onAction = _ => stage.fullScreen = !stage.fullScreen()


  private val bottomRow = new HBox(inset, playPause, volume, microphone, time, space, tracks, settings, expand):
    alignment = Pos.Center
    padding = Insets(left = inset, right = inset, top = shortInset, bottom = shortInset)
    background = Background fill Color.Black.opacity(backgroundOpacity)

  children = Seq(seek, bottomRow)
