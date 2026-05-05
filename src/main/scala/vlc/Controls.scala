package vlc

import atlantafx.base.controls.ProgressSliderSkin
import constant.Constants
import fx.{AutoBg, AutoInsets}
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import javafx.beans.binding.StringBinding
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.layout as jfxsl
import javafx.util.Duration
import org.kordamp.ikonli.fluentui.FluentUiFilledMZ
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxNode2sfx, jfxProperty2sfx}
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.binding.BooleanBinding
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.SceneIncludes.jfxSkin2sfxSkin
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.text.Font
import uk.co.caprica.vlcj.player.base.MediaPlayer

import scala.math.Integral.Implicits.infixIntegralOps


private class Controls(player: MediaPlayer, pauses: Pauses) extends VBox(Constants.playerInset):

  padding = Insets(Constants.playerInset)
  alignmentInParent = Pos.BottomCenter
  maxHeight = Region.UsePrefSize

  private val roundedCorners = CornerRadii(99999)
  private val itemBg = AutoBg.fill(Color.gray(0.12, 0.55), roundedCorners)
  private val itemHoverBg = AutoBg.fill(Color.gray(0.1, 0.8), roundedCorners)

  val seek: Slider = new Slider:
    private val pause = pauses.createRequester
    hgrow = Priority.Always
    skin = ProgressSliderSkin(this)
    value.addListener: (_,_,value) =>
      if (pressed() && !valueChanging()) player.controls.setTime(value.longValue)
    valueChanging.addListener: (_,_,changing) =>
      if (!changing) player.controls.setTime(value().toLong)
      pause.requestPause(changing)

  val playPause: Button = new Button:
    private val pause = pauses.createRequestProp
    prefWidth = 50
    prefHeight = 50
    background <== hover.map[jfxsl.Background](if (_) itemHoverBg else itemBg)
    graphic = new FontIcon:
      setStyle("-fx-icon-size: 28px; -fx-icon-color: white;")
      iconCodeProperty <== pause.map(if (_) FluentUiFilledMZ.PLAY_48 else FluentUiFilledMZ.PAUSE_48)
    onAction = _ => pause() = !pause()

  val volume: HBox = new HBox:
    minWidth = 0
    background = itemBg
    alignment = Pos.CenterLeft

    private val slider = new Slider(0, 100, 100):
      margin = AutoInsets(right = Constants.playerInset)
      value.addListener { (_,_,value) => player.audio.setVolume(value.intValue) }
      hgrow = Priority.Always
      skin = ProgressSliderSkin(this)

    private val button = new Button:
      background = Background.Empty
      onAction = _ => slider.value() = if (slider.value() > 0) 0 else 100
      graphic = new FontIcon:
        setStyle("-fx-icon-size: 24px; -fx-icon-color: white;")
        iconCodeProperty <== slider.value.map: d =>
          val i = d.intValue
          if (i <= 0)       FluentUiFilledMZ.SPEAKER_NONE_24
          else if (i < 100) FluentUiFilledMZ.SPEAKER_1_24
          else              FluentUiFilledMZ.SPEAKER_24

    children ++= Seq(button, slider)

    private val extraWidth = SimpleIntegerProperty(this, "extraWidth")
    private val animation = Timeline(
      KeyFrame(Duration.millis(0),    KeyValue(extraWidth, 0),              KeyValue(slider.opacity, 0)),
      KeyFrame(Duration.millis(250),  KeyValue(extraWidth, 150, EaseBoth),  KeyValue(slider.opacity, 1, EaseBoth)),
    )

    private val sliderEnable = extraWidth.isNotEqualTo(0)
    slider.managed <== sliderEnable
    slider.visible <== sliderEnable

    prefWidth <== button.width + extraWidth
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize

    val expand: BooleanBinding = hover || slider.pressed
    expand.addListener: (_,_,expand) =>
      animation.pause()
      animation.setRate(if (expand) 1 else -1)
      animation.play()

  val time: Label = new Label:
    padding = Insets(Constants.playerInset)
    font = Font(15)
    textFill = Color.White
    background = itemBg
    text <== new StringBinding:
      bind(seek.value, seek.max)
      def computeValue: String = time2str(seek.value.toLong) + " / " + time2str(seek.max.toLong)
      private def time2str(msTotal: Long): String =
        val sTotal = msTotal / 1000
        val (mTotal, s) = sTotal /% 60
        val (h, m) = mTotal /% 60
        if (h > 0) f"$h%d:$m%02d:$s%02d" else f"$m%02d:$s%02d"

  private val bottomRow = new HBox(Constants.playerInset, playPause, volume, time):
    alignment = Pos.CenterLeft

  children = Seq(seek, bottomRow)
