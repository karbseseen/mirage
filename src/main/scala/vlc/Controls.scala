package vlc

import atlantafx.base.controls.ProgressSliderSkin
import constant.Constants
import fx.AutoBg
import javafx.beans.binding.StringBinding
import javafx.scene.layout as jfxsl
import org.kordamp.ikonli.fluentui.FluentUiFilledMZ
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxNode2sfx, jfxProperty2sfx}
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

  private val bottomRow = new HBox(Constants.playerInset, playPause, time):
    alignment = Pos.CenterLeft

  children = Seq(seek, bottomRow)
