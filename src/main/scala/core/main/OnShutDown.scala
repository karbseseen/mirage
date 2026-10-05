package core.main

import scalafx.application.JFXApp3
import util.also

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec


private trait OnShutDown extends JFXApp3:
  private val shutdownHooks, shutdownLongHooks = AtomicReference[List[Runnable]](Nil)
  def shutdownHook(func: => Unit): Unit = shutdownHooks.updateAndGet { (() => func) :: _ }
  def shutdownLongHook(func: => Unit): Unit = shutdownLongHooks.updateAndGet { (() => func) :: _ }

  @volatile private var _isExiting = false
  def isExiting: Boolean = _isExiting

  override def main(args: Array[String]): Unit =
    try super.main(args)
    finally
      _isExiting = true
      for hook <- shutdownLongHooks.get.reverse do
        Thread(hook).also(_.start())
      for hook <- shutdownHooks.get.reverse do
        try hook.run()
        catch case error: Throwable => error.printStackTrace()
      joinThreads(10000)

  private def joinThreads(timeout: Long): Boolean =
    val endTime = System.currentTimeMillis + timeout

    val loader = Thread.currentThread.getContextClassLoader
    Thread.currentThread.setContextClassLoader(null)

    @tailrec def getAllThreads(maxSize: Int = 16): List[Thread] =
      val array = new Array[Thread](maxSize)
      val actualSize = Thread.currentThread.getThreadGroup.enumerate(array)
      if (actualSize < maxSize)
        array.view.take(actualSize).filter(_.getContextClassLoader eq loader).toList
      else
        getAllThreads(maxSize * 2)
    val threads = getAllThreads()

    threads.filter(_.isDaemon).foreach(_.interrupt())

    !threads.exists: thread =>
      val timeout = endTime - System.currentTimeMillis
      if (timeout > 0) thread.join(timeout)
      thread.isAlive
