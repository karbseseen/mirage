package vlc.control

import core.TaskQueue.{ScheduledTask, scheduleSingleAt}
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx, jfxScene2sfx}
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.Linear
import scalafx.application.Platform.runLater
import scalafx.scene.Cursor
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.Region
import scalafx.stage.WindowEvent
import scalafx.util.Duration
import vlc.control.FloatingMenu.ViewMode

import java.lang


private trait AutoHide extends Region with ControlsBase:
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
  stage.root.addEventHandler(MouseEvent.MouseMoved, _ => restartAutoHide())

  protected def restartAutoHide(timeout: Long): Unit =
    if (animation.rate() < 0)
      animation.rate = 1
      animation.play()
      stage.scene().cursor = Cursor.Default
    actualTime = System.currentTimeMillis + autoHideTimeout
    if (task.isEmpty) createTask()

  private def createTask(): Unit =
    task = Some:
      scheduleSingleAt(actualTime):
        runLater:
          if (task.exists(_.time == actualTime))
            task = None
            if (animation.rate() > 0 && !hover())
              if (menus.exists(_.showing()))
                val weakHoverMenus = menus.filter(_.viewMode() == ViewMode.WeakHover)
                if (weakHoverMenus.nonEmpty)
                  weakHoverMenus.foreach(_.viewMode() = ViewMode.Hover)
                  actualTime = System.currentTimeMillis + autoHideTimeout / 2
                  createTask()
              else
                animation.rate = -1
                animation.play()
          else
            createTask()
