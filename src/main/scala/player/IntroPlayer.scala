package player

import config.{Config, YamlEnumCodec}
import constant.{Tr, Translate}
import core.TaskQueue
import javafx.scene.input.KeyCode
import javafx.scene.layout as jfxsl
import player.IntroPlayer.*
import player.IntroPlayer.Transition.Bubbles
import player.default.DefaultPlayer
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx}
import scalafx.application.Platform.runLater
import scalafx.scene.Node
import scalafx.scene.input.KeyEvent
import uk.co.caprica.vlcj.media.callback.seekable.RandomAccessFileMedia
import uk.co.caprica.vlcj.player.base.{MediaPlayer, MediaPlayerEventAdapter}
import util.fx.JfxEvents.registerEventHandler
import util.fx.SfxSuperBind.<~=
import util.loop

import java.io.File
import java.nio.ByteBuffer


class IntroPlayer(nextMedia: Media) extends Player:
  protected val media = RandomAccessFileMedia(File(IntroPlayer.fileConfig()))
  videoCropCoef() = 1
  override def start(): Unit = vlc.media.play(media)

  private val actualPlayer = DefaultPlayer.Preloaded(nextMedia)

  private var switched = false
  private def switchToActual(): Unit =
    close()
    Option(stage()).foreach(_.player() = actualPlayer)
    actualPlayer.start()
    switched = true

  private val transitionPlayer = Some(transitionConfig()).collect:
    case Transition.Bubbles => BubblePlayer(this)

  override def views: List[Node] =
    if (stage() == null) super.views
    else actualPlayer.views ::: super.views ::: transitionPlayer.toList.flatMap(_.views)

  stage.map(_.title) <~= Tr.almostShowtime

  this addHandler new AnyFinishHandler with TaskHandler:
    def anyFinish(player: MediaPlayer): Unit = runLater { if (transitionPlayer.forall(!_.started)) switchToActual() }
    def taskTime: Option[Long] = transitionPlayer.map(length - _.switchOffset)
    def task(): Unit = runLater { if (!switched) transitionPlayer.foreach(_.start()) }

  scene.registerEventHandler(KeyEvent.KeyPressed): event =>
    transitionPlayer.filter(_ => event.getCode == KeyCode.ENTER).foreach: player =>
      player.start()
      event.consume()


object IntroPlayer:

  enum Transition(val name: Translate) derives YamlEnumCodec:
    case No       extends Transition(Tr.no)
    case Bubbles  extends Transition(Tr.bubbles)

  val fileConfig = Config.StringProp(this, "introFile")
  val transitionConfig = Config.ObjectProp(this, "transition", Bubbles: Transition)


  private trait AnyFinishHandler extends MediaPlayerEventAdapter:
    private var anyFinished = false
    private def doAnyFinish(player: MediaPlayer): Unit =
      if (!anyFinished)
        anyFinished = true
        anyFinish(player)
    def anyFinish(player: MediaPlayer): Unit

    override def finished(player: MediaPlayer): Unit = doAnyFinish(player)
    override def stopped(player: MediaPlayer):  Unit = doAnyFinish(player)
    override def error(player: MediaPlayer):    Unit = doAnyFinish(player)

  private trait TaskHandler extends MediaPlayerEventAdapter:
    def taskTime: Option[Long]
    def task(): Unit
    private var hasTask = false
    override def timeChanged(player: MediaPlayer, time: Long): Unit =
      taskTime.filter(_ => !hasTask).foreach: taskTime =>
        TaskQueue.scheduleSingle(taskTime - time)(task())
        hasTask = true


  private class BubblePlayer(parentPlayer: IntroPlayer) extends Player:
    protected val media = ResourceMedia("/bubbles_transition.webm")

    def videoStartOffset = 2450
    def switchOffset = 666

    videoCropCoef() = 1
    vlc.media.play(media, "start-paused", s"start-time=${videoStartOffset/1000f}")

    def started: Boolean = _started
    private var _started = false
    override def start(): Unit =
      vlc.controls.setPause(false)
      _started = true

    override def close(): Unit =
      super.close()
      views.foreach: view =>
        view.parent() match
          case pane: jfxsl.Pane => pane.getChildren.remove(view)

    this addHandler new AnyFinishHandler with TaskHandler:
      def anyFinish(player: MediaPlayer): Unit = runLater { close() }
      def taskTime: Option[Long] = Some(videoStartOffset + switchOffset)
      def task(): Unit = runLater { parentPlayer.switchToActual() }

    override protected def bufferNum: Int = 2
    override protected def onRender(buffer: ByteBuffer, width: Int, height: Int): Unit =
      loop(0, width * height * 4, 4): index =>
        val srcB = 255 & buffer.get(index)
        val srcG = 255 & buffer.get(index + 1)
        val srcR = 255 & buffer.get(index + 2)

        val dstA0 = (srcR * rCoef + srcG * gCoef + srcB * bCoef + offset) / denominator
        val dstA = (dstA0 max 0 min 255).toByte

        buffer.put(index, dstA)
        buffer.put(index + 1, dstA)
        buffer.put(index + 2, dstA)
        buffer.put(index + 3, dstA)

    private val color0 = Vector(170, 181, 11)
    private val color1 = Vector(254, 254, 254)
    private val dColor = color1.zip(color0).map(_ - _)
    private val Vector(rCoef, gCoef, bCoef) = dColor.map(_ * 255)
    private val denominator: Int = dColor.map(c => c * c).sum
    private val offset: Int = color0.zip(dColor).map(_ * _).sum * -255 + denominator / 2
