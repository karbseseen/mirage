package core.main

import scalafx.application.JFXApp3

import scala.collection.mutable


private[main] trait OnShutDown extends JFXApp3:
  private val shutdownHooks = mutable.Buffer.empty[Runnable]
  private val shutdownLongHooks = mutable.Buffer.empty[Runnable]
  def shutdownHook(func: => Unit): Unit = shutdownHooks += (() => func)
  def shutdownLongHook(func: => Unit): Unit = shutdownLongHooks += (() => func)

  @volatile private var _isExiting = false
  def isExiting: Boolean = _isExiting

  override def main(args: Array[String]): Unit =
    try super.main(args)
    finally
      _isExiting = true
      shutdownLongHooks.foreach { Thread(_).start() }
      for hook <- shutdownHooks do
        try hook.run()
        catch case error: Throwable => error.printStackTrace()
