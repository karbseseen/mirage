package vlc

import atlantafx.base.controls.ProgressSliderSkin
import constant.{Constants, Tr}
import fx.AutoBg.+
import fx.{AutoBg, AutoInsets, GrandParentXBinding, GrandParentYBinding}
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import javafx.beans.binding.StringBinding
import javafx.beans.property.{SimpleBooleanProperty, SimpleIntegerProperty}
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.fluentui.{FluentUiFilledAL, FluentUiFilledMZ}
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxBackground2sfx, jfxInsets2sfx, jfxNode2sfx, jfxParent2sfx, jfxProperty2sfx}
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.EaseBoth
import scalafx.beans.BeanIncludes.jfxObservableValue2sfx
import scalafx.beans.binding.{Bindings, BooleanBinding, BooleanExpression}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.SceneIncludes.jfxSkin2sfxSkin
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.text.Font
import scalafx.util.Duration
import uk.co.caprica.vlcj.player.base.MediaPlayer

import scala.jdk.CollectionConverters.*
import scala.math.Integral.Implicits.infixIntegralOps


private class Controls(stage: VlcStage) extends VBox(Constants.playerInset):

  import stage.{pauses, player}

  padding = Insets(Constants.playerInset)
  alignmentInParent = Pos.BottomCenter
  maxHeight = Region.UsePrefSize

  private val bgColor       = Color.gray(0.12, 0.75)
  private val bgHoverColor  = Color.gray(1, 0.1)
  private val staticBg      = AutoBg.fill(bgColor, CornerRadii(99999))
  private val staticHoverBg = staticBg + AutoBg.fill(bgHoverColor, CornerRadii(99999))
  private def hoverableBg(node: Node) = node.hover.map(if (_) staticHoverBg else staticBg)

  private def fontIconStyle(size: Int) = s"-fx-icon-size: ${size}px; -fx-icon-color: white;"


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
    padding = Insets(Constants.playerInset * 1.5)
    background <== hoverableBg(this)
    graphic = new FontIcon:
      setStyle(fontIconStyle(28))
      iconCodeProperty <== pause.map(if (_) FluentUiFilledMZ.PLAY_48 else FluentUiFilledMZ.PAUSE_48)
    onAction = _ => pause() = !pause()

  val volume: HBox = new HBox:
    background <== hoverableBg(this)
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
        setStyle(fontIconStyle(24))
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

  val time: Label = new Label:
    padding = Insets(Constants.playerInset)
    font = Font(15)
    textFill = Color.White
    background = staticBg
    text <== new StringBinding:
      bind(seek.value, seek.max)
      def computeValue: String = time2str(seek.value.toLong) + " / " + time2str(seek.max.toLong)
      private def time2str(msTotal: Long): String =
        val sTotal = msTotal / 1000
        val (mTotal, s) = sTotal /% 60
        val (h, m) = mTotal /% 60
        if (h > 0) f"$h%d:$m%02d:$s%02d" else f"$m%02d:$s%02d"

  private val space = new Region:
    hgrow = Priority.Always

  val video: TrackControl = new TrackControl:
    def getName(vlcId: Int): String = player.video.trackDescriptions.asScala.find(_.id == vlcId).fold("???")(_.description)
    def onSelect(vlcId: Int): Unit = player.video.setTrack(vlcId)

  val audio: TrackControl = new TrackControl:
    def getName(vlcId: Int): String = player.audio.trackDescriptions.asScala.find(_.id == vlcId).fold("???")(_.description)
    def onSelect(vlcId: Int): Unit = player.audio.setTrack(vlcId)

  val title: TrackControl = new TrackControl:
    def getName(vlcId: Int): String = player.subpictures.trackDescriptions.asScala.find(_.id == vlcId).fold("???")(_.description)
    def onSelect(vlcId: Int): Unit = player.subpictures.setTrack(vlcId)
    override def removeItem(vlcId: Int): Unit = if (vlcId != -1) super.removeItem(vlcId)
    children += new TrackItem(-1) { text <== Tr.noSubtitles }

  val tracks: HBox = new HBox(
    trackIcon(video, FluentUiFilledMZ.VIDEO_24),
    trackIcon(audio, FluentUiFilledMZ.MUSIC_NOTE_24),
    trackIcon(title, FluentUiFilledAL.CLOSED_CAPTION_24),
  ):
    padding = AutoInsets(left = Constants.playerInset, right = Constants.playerInset)
    maxHeight = Region.UsePrefSize
    background = staticBg

    visible <== children.view.map[BooleanExpression](_.visible).reduce(_ || _)
    managed <== visible

  val expand: Button = new Button:
    padding = Insets(Constants.playerInset)
    background <== hoverableBg(this)
    graphic = new FontIcon:
      setStyle(fontIconStyle(24))
      iconCodeProperty <== stage.fullScreen.map(if (_) FluentUiFilledAL.ARROW_MINIMIZE_24 else FluentUiFilledAL.ARROW_MAXIMIZE_24)
    onAction = _ => stage.fullScreen = !stage.fullScreen()


  private val bottomRow = new HBox(Constants.playerInset, playPause, volume, time, space, tracks, expand):
    alignment = Pos.CenterLeft

  children = Seq(seek, bottomRow)

  def extraParts: List[Node] = List(video, audio, title)


  abstract class TrackControl private[Controls] extends VBox:

    private def cornerRadius = 12.0

    def getName(vlcId: Int): String
    def onSelect(vlcId: Int): Unit

    minWidth = 125
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize
    alignmentInParent = Pos.TopLeft
    fillWidth = true
    opacity = 0
    visible <== opacity.isNotEqualTo(0)
    managed <== visible
    background <== padding.map(AutoBg.fill(bgColor, CornerRadii(cornerRadius), _))

    val selectedId = SimpleIntegerProperty(this, "selectedId", -1)

    def addItem(vlcId: Int): Unit =
      val index = children.indexWhere(_.id().toIntOption.exists(_ > vlcId))
      children.add(if (index >= 0) index else children.size, TrackItem(vlcId))
    def removeItem(vlcId: Int): Unit =
      val index = children.indexWhere(_.id().toIntOption.exists(_ == vlcId))
      if (index >= 0) children.remove(index)

    protected class TrackItem(vlcId: Int) extends Button:

      private val first = SimpleBooleanProperty(this, "first")
      private val last = SimpleBooleanProperty(this, "last")
      TrackControl.this.children.addListener: _ =>
        val index = TrackControl.this.children.indexOf(this)
        first() = index == 0
        last() = index == TrackControl.this.children.size - 1

      maxWidth = Double.MaxValue
      textFill = Color.White
      alignment = Pos.CenterLeft
      underline <== selectedId.isEqualTo(vlcId)
      text = getName(vlcId)
      id = vlcId.toString
      padding = Insets(
        left    = Constants.playerInset * 2.5,
        right   = Constants.playerInset * 2.5,
        top     = Constants.playerInset,
        bottom  = Constants.playerInset,
      )
      background <== Bindings.createObjectBinding(
        () => if (!hover()) null else AutoBg.fill(
          bgHoverColor,
          CornerRadii(
            topLeft     = if (first()) cornerRadius else 0,
            topRight    = if (first()) cornerRadius else 0,
            bottomRight = if (last()) cornerRadius else 0,
            bottomLeft  = if (last()) cornerRadius else 0,
            asPercent   = false,
          ),
        ),
        hover, first, last
      )
      onAction = _ => onSelect(vlcId)

  private def trackIcon(control: TrackControl, icon: Ikon) =
    object iconPane extends StackPane:
      val enable: BooleanBinding = hover || control.hover
      padding = Insets(Constants.playerInset)
      background <== enable.map(if (_) staticHoverBg else null)
      children += new FontIcon:
        setStyle(fontIconStyle(24))
        setIconCode(icon)

    val iconSceneX = control.parent.flatMap(GrandParentXBinding(iconPane, _)).orElse(0)
    val iconSceneY = control.parent.flatMap(GrandParentYBinding(iconPane, _)).orElse(0)

    control.padding <== Bindings.createObjectBinding(
      () => AutoInsets(bottom = iconSceneY().doubleValue - Controls.this.layoutY()).delegate,
      iconSceneY, Controls.this.layoutY
    )

    control.translateX <== Bindings.createDoubleBinding(
      () => (iconSceneX().doubleValue + (iconPane.width() - control.width()) / 2) min (Controls.this.width() - control.width()),
      iconSceneX, iconPane.width, control.width, Controls.this.width
    )

    control.translateY <== Bindings.createDoubleBinding(
      () => iconSceneY().doubleValue - control.height(),
      iconSceneY, control.height
    )

    val animation = new FadeTransition(Duration(250), control):
      fromValue = 0
      toValue = 1
      interpolator = EaseBoth

    iconPane.enable.addListener: (_, _, enable) =>
      animation.setRate(if (enable) 1 else -1)
      animation.play()

    iconPane.visible = children.size > 1
    control.children.onChange((children, _) => iconPane.visible = children.size > 1)
    iconPane.managed <== iconPane.visible

    iconPane
