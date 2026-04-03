package fx

import scalafx.application.Platform


trait Worker extends Runnable:
  protected def ui(func: => Unit): Unit = Platform.runLater(func)
  def toThread: Thread =
    val thread = new Thread(this)
    thread.setDaemon(true)
    thread
  def start(): Unit = toThread.start()
