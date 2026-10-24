package player.default.control

import atlantafx.base.controls.ProgressSliderSkin
import core.TaskQueue.{PeriodicTask, schedule}
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import javafx.beans.binding.StringBinding
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.paint.Stop
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.fluentui.{FluentUiFilledAL, FluentUiFilledMZ, FluentUiRegularMZ}
import org.kordamp.ikonli.javafx.FontIcon
import player.PlayerSync.Force
import player.PlayerSync.Pause.Toggle
import player.default.DefaultPlayer
import player.default.control.Controls.IconView
import player.time2str
import scalafx.Includes.{jfxBackground2sfx, jfxNode2sfx, jfxObservableValue2sfx, jfxProperty2sfx, jfxSkin2sfxSkin, jfxStage2sfx}
import scalafx.animation.Interpolator.EaseBoth
import scalafx.application.Platform.runLater
import scalafx.beans.binding.{BooleanBinding, BooleanExpression}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Label, Slider}
import scalafx.scene.layout.*
import scalafx.scene.paint.{Color, LinearGradient}
import scalafx.scene.text.Font
import scalafx.scene.{Cursor, Node}
import scalafx.util.Duration
import util.fx.AutoInsets


private trait ControlsBase:
  protected val player: DefaultPlayer
  protected def restartAutoHide(timeout: Long = autoHideTimeout): Unit
  protected def menus: collection.Seq[FloatingMenu]
  def extraParts: List[Node] = menus.map(_.holder).toList


private[player] class Controls(using protected val player: DefaultPlayer) extends VBox with Menus with AutoHide:

  alignmentInParent = Pos.BottomCenter
  maxHeight = Region.UsePrefSize

  private var isSeekProgramChange = false
  def setTime(value: Long): Unit =
    if (!seek.valueChanging())
      isSeekProgramChange = true
      seek.value = value
      isSeekProgramChange = false
  def setMaxTime(value: Long): Unit =
    seek.max = value

  private val seek = new Slider with Controls.Slider:
    hgrow = Priority.Always
    padding = AutoInsets(left = inset, right = inset)
    margin = AutoInsets(top = shortInset)
    background = Background fill new LinearGradient(
      0.5, 0, 0.5, 1,
      stops = Seq(Stop(0.2, Color.Transparent), Stop(0.8, Color.Black.opacity(bgOpacity))),
    )
    cursor = funnyCursor

    private var dragCounter = 0
    private var task: Option[Task] = None

    value.addListener: (_,_,value) =>
      if (!isSeekProgramChange)
        dragCounter += 1
        dragCounter match
          case 1 => player.setTime(value.longValue)
          case 2 => task = Some(new Task)
          case _ => ()

    onMouseReleased = _ =>
      dragCounter = 0
      task.foreach(_.cancel())
      task = None

    private class Task extends PeriodicTask(System.currentTimeMillis + 500, 500):
      schedule(this)
      private val wasPlaying = player.isPlaying
      if (wasPlaying) player.setPause(true)
      def run(): Unit = runLater:
        if (!isCancelled)
          player.setTime(value.longValue, Force)
      override def cancel(): Unit =
        super.cancel()
        if (!player.restartIfFinished() && wasPlaying) player.setPause(false, Force)
        player.setTime(value.longValue, Force)


  private val playPause = new Controls.Button(longInset):
    graphic = new IconView(28):
      iconCodeProperty <== player.playing.map(if (_) FluentUiFilledMZ.PAUSE_48 else FluentUiFilledMZ.PLAY_48)
    override def onClick(): Unit = player.setPause(Toggle)

  private val volume: HBox = new HBox:
    background <== hoverableBg(this)
    alignment = Pos.CenterLeft
    padding = AutoInsets(right = longInset)
    cursor = funnyCursor

    private val slider = new Slider(0, 100, 100) with Controls.Slider:
      value.addListener { (_,_,value) => player.volume = value.intValue }
      hgrow = Priority.Always

    private val button = new Label:
      padding = Insets(inset)
      onMouseClicked = _ => slider.value() = if (slider.value() > 0) 0 else 100
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

  private val microphone = new Controls.Button:
    private var wasEnabled = false
    graphic = new IconView(24):
      iconCodeProperty <==
        player.phone.microphoneEnabled.map(if (_) FluentUiRegularMZ.MIC_ON_24 else FluentUiRegularMZ.MIC_OFF_24)
    disable <== player.playing
    override def onClick(): Unit = player.phone.microphoneEnabled() = !player.phone.microphoneEnabled()
    player.playing.addListener: (_,_,playing) =>
      if (playing)
        wasEnabled = player.phone.microphoneEnabled()
        player.phone.microphoneEnabled() = false
      else
        player.phone.microphoneEnabled() = wasEnabled

  private val time = new Controls.Button:
    font = Font(15)
    textFill = Color.White

    private var backward = false
    private val textBind = new StringBinding:
      bind(seek.value, seek.max)
      protected def computeValue: String = time1 + " / " + time2str(seek.max.toLong)
      private def time1 =
        if (backward) "-" + time2str(seek.max.toLong - seek.value.toLong)
        else time2str(seek.value.toLong)
    text <== textBind
    override def onClick(): Unit =
      backward = !backward
      textBind.invalidate()

  private val space = new Region:
    hgrow = Priority.Always

  private val tracks = new HBox:
    maxHeight = Region.UsePrefSize
    background = staticBg

    children = Seq(
      (videoMenu, FluentUiFilledMZ.VIDEO_24),
      (audioMenu, FluentUiFilledMZ.MUSIC_NOTE_24),
      (subtitleMenu, FluentUiFilledAL.CLOSED_CAPTION_24),
    ).map: (floating: FloatingTracks, icon: Ikon) =>
      new Controls.Button with FloatingMenu.Control:
        bindMenu(floating, Controls.this)
        background <== menuHover.map(if (_) staticBgHover else null)
        graphic = IconView(icon, 24)

    visible <== children.view.map[BooleanExpression](_.visible).reduce(_ || _)
    managed <== visible

  private val settings = new Controls.Button with FloatingMenu.Control:
    bindMenu(settingsMenu, Controls.this)
    bindMenu(speedMenu, Controls.this)
    bindMenu(customTitleMenu, Controls.this)
    background <== menuHover.map(if (_) staticHoveredBg else staticBg)
    graphic = IconView(FluentUiFilledMZ.SETTINGS_24, 24)

  private val expand = new Controls.Button:
    graphic = new IconView(24):
      iconCodeProperty <== player.stage.flatMap(_.fullScreen).map:
        if (_) FluentUiFilledAL.ARROW_MINIMIZE_24 else FluentUiFilledAL.ARROW_MAXIMIZE_24
    override def onClick(): Unit = player.stage().fullScreen = !player.stage().fullScreen()


  private val bottomRow = new HBox(inset, playPause, volume, microphone, time, space, tracks, settings, expand):
    alignment = Pos.Center
    padding = Insets(left = inset, right = inset, top = shortInset, bottom = shortInset)
    background = Background fill Color.Black.opacity(bgOpacity)

  children = Seq(seek, bottomRow)


private object Controls:

  private abstract class Button(inset: Double = inset)(using cursor: Cursor) extends Label:
    padding = Insets(inset)
    background <== hoverableBg(this)
    this.cursor = cursor
    onMouseClicked = { event => onClick(); event.consume() }
    def onClick(): Unit = ()

  private trait Slider extends scalafx.scene.control.Slider:
    skin = ProgressSliderSkin(this)
    focused.addListener((_,_,focused) => if (focused) scene().getRoot.requestFocus())

  class IconView(size: Int) extends FontIcon:
    setStyle(s"-fx-icon-size: ${size}px; -fx-icon-color: white;")
    def this(icon: Ikon, size: Int) =
      this(size)
      setIconCode(icon)
