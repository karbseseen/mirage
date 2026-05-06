package vlc

import constant.Constants
import scalafx.animation.FadeTransition
import scalafx.animation.Interpolator.EaseBoth
import scalafx.application.Platform.runLater
import scalafx.util.Duration

import java.lang
import java.util.concurrent.{CountDownLatch, TimeUnit}


private def applyControlHide(stage: VlcStage): Unit =

  import stage.controls

  val animation = new FadeTransition(Duration(250), controls):
    fromValue = 0
    toValue = 1
    interpolator = EaseBoth
  controls.translateY <== controls.opacity.map[lang.Number]: opacity =>
    (1 - opacity.doubleValue) * controls.height()

  val dieLatch = CountDownLatch(1)

  val thread = Thread: () =>
    def await(func: => Unit) =
      try { func; true }
      catch case _: InterruptedException => false
    while
      while !await(dieLatch.await(Constants.playerControlHideTimeout, TimeUnit.MILLISECONDS)) do ()
      if (!(controls :: controls.extraParts).exists(_.hover()))
        runLater:
          if (animation.rate() > 0)
            animation.rate = -1
            animation.play()
      !await(dieLatch.await())
    do ()
  thread.setDaemon(true)
  thread.start()

  stage.onHidden = _ => dieLatch.countDown()
  stage.root.onMouseMoved = _ =>
    if (animation.rate() < 0)
      animation.rate = 1
      animation.play()
    thread.interrupt()
