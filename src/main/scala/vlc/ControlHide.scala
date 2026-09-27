package vlc

import constant.Constants
import core.TaskQueue.{ScheduledTask, scheduleSingleAt}
import scalafx.Includes.jfxScene2sfx
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.Linear
import scalafx.application.Platform.runLater
import scalafx.scene.Cursor
import scalafx.stage.WindowEvent
import scalafx.util.Duration

import java.lang


private def applyControlHide(stage: VlcStage): Unit =

  import stage.controls

  val animation = new FadeTransition(Duration(200), controls):
    fromValue = 0
    toValue = 1
    interpolator = Linear
    onFinished = _ => if (rate() < 0) stage.scene().cursor = Cursor.None
  controls.translateY <== controls.opacity.map[lang.Number]: opacity =>
    (1 - opacity.doubleValue) * controls.height()

  var task: Option[ScheduledTask] = None
  var actualTime = Long.MaxValue

  def createTask(): Unit =
    task = Some:
      scheduleSingleAt(actualTime):
        runLater:
          if (task.exists(_.time == actualTime))
            task = None
            if (animation.rate() > 0 && !(controls :: controls.extraParts).exists(_.hover()))
              animation.rate = -1
              animation.play()
          else
            createTask()

  stage.addEventHandler(WindowEvent.WindowHidden, _ => task.foreach(_.cancel()))
  stage.root.onMouseMoved = _ =>
    if (animation.rate() < 0)
      animation.rate = 1
      animation.play()
      stage.scene().cursor = Cursor.Default

    actualTime = System.currentTimeMillis + Constants.playerControlHideTimeout
    if (task.isEmpty) createTask()
