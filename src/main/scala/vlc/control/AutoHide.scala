package vlc.control

import constant.Constants
import core.TaskQueue.{ScheduledTask, scheduleSingleAt}
import scalafx.Includes.jfxScene2sfx
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.Linear
import scalafx.application.Platform.runLater
import scalafx.scene.layout.Region
import scalafx.scene.{Cursor, Node}
import scalafx.stage.WindowEvent
import scalafx.util.Duration
import vlc.VlcStage

import java.lang


private trait AutoHide extends Region:
  protected val stage: VlcStage
  def extraParts: List[Node]

  private var task: Option[ScheduledTask] = None
  private var actualTime = Long.MaxValue
  private val animation = new FadeTransition(Duration(200), this):
    fromValue = 0
    toValue = 1
    interpolator = Linear
    onFinished = _ => if (rate() < 0) stage.scene().cursor = Cursor.None

  translateY <== opacity.map[lang.Number]: opacity =>
    (1 - opacity.doubleValue) * height()

  stage.addEventHandler(WindowEvent.WindowHidden, _ => task.foreach(_.cancel()))
  stage.root.onMouseMoved = _ =>
    if (animation.rate() < 0)
      animation.rate = 1
      animation.play()
      stage.scene().cursor = Cursor.Default
    actualTime = System.currentTimeMillis + Constants.playerControlHideTimeout
    if (task.isEmpty) createTask()

  private def createTask(): Unit =
    task = Some:
      scheduleSingleAt(actualTime):
        runLater:
          if (task.exists(_.time == actualTime))
            task = None
            if (animation.rate() > 0 && !(this :: extraParts).exists(_.hover()))
              animation.rate = -1
              animation.play()
          else
            createTask()
