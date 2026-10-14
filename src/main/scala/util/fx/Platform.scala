package util.fx

import scalafx.application.Platform


def runLaterIfNeeded(task: => Unit): Unit =
  if (Platform.isFxApplicationThread) task
  else Platform.runLater(task)
