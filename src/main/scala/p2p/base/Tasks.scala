package p2p.base

import core.main.MainApp
import p2p.base
import util.also

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable


sealed trait Task:
  def time: Long
  def cancel(): Unit


trait Tasks private[p2p]:

  private val taskQueue = mutable.PriorityQueue.empty[Task](using Ordering.by(-_.time))
  private val newTasks = AtomicReference(List.empty[Task])
  @volatile private var closing = false


  def scheduleSingle(delay: Long, wakeup: Boolean = false)(task: => Unit): base.Task =
    scheduleSingleAt(System.currentTimeMillis + delay, wakeup)(task)
  def scheduleSingleAt(time: Long, wakeup: Boolean = false)(task: => Unit): base.Task =
    SingleTask(task, time).also(addTask(_, wakeup))

  def schedulePeriodic(delay: Long, period: Long, wakeup: Boolean = false)(task: => Unit): base.Task =
    schedulePeriodicAt(System.currentTimeMillis + delay, period, wakeup)(task)
  def schedulePeriodicAt(time: Long, period: Long, wakeup: Boolean = false)(task: => Unit): base.Task =
    PeriodicTask(task, time, period).also(addTask(_, wakeup))

  private def addTask(task: Task, wakeup: Boolean): Unit =
    if (Thread.currentThread == loopThread) taskQueue += task
    else
      newTasks.updateAndGet(task :: _)
      if (wakeup) this.wakeup()

  protected def threadSafe(task: => Unit): Unit =
    if (Thread.currentThread == loopThread) task
    else
      val singleTask = SingleTask(task, System.currentTimeMillis)
      newTasks.updateAndGet(singleTask :: _)
      wakeup()


  protected def loop(timeUntilNext: Option[Long]): Unit
  protected def wakeup(): Unit
  protected def onClose(): Unit


  protected def start(): Unit = loopThread.start()

  MainApp.shutdownHook:
    closing = true
    wakeup()

  private val loopThread = Thread: () =>
    while (!closing)
      taskQueue ++= newTasks.getAndSet(Nil)
      loop(runAvailable())
    onClose()

  @tailrec private def runAvailable(): Option[Long] =
    val now = System.currentTimeMillis
    taskQueue.headOption match
      case Some(task) if task.cancelled =>
        taskQueue.dequeue()
        runAvailable()
      case Some(task: SingleTask) if task.time <= now =>
        taskQueue.dequeue()
        task.run()
        runAvailable()
      case Some(task: PeriodicTask) if task.time <= now =>
        taskQueue.dequeue()
        task.run()
        task.time = System.currentTimeMillis + task.period
        taskQueue += task
        runAvailable()
      case Some(task) => Some(task.time - now)
      case None => None


  private class SingleTask(value: => Unit, val time: Long) extends Task(value)
  private class PeriodicTask(value: => Unit, var time: Long, val period: Long) extends Task(value, time)
  private sealed abstract class Task(value: => Unit) extends base.Task:
    @volatile var cancelled = false
    def cancel(): Unit = cancelled = true
    def run(): Unit =
      try value
      catch case error: Throwable => error.printStackTrace()
