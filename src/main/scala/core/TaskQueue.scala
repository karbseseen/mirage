package core

import core.main.MainApp
import util.also

import java.nio.channels.{SelectableChannel, Selector}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable


object TaskQueue:

  private val taskQueue = mutable.PriorityQueue.empty[ScheduledTask](using Ordering.by(-_.time))
  private val newTasks = AtomicReference(List.empty[ScheduledTask])
  private val closeTasks = AtomicReference(List.empty[Task])
  private val selector: Selector = Selector.open
  @volatile private var closing = false


  private def schedule(task: ScheduledTask): Unit =
    if (Thread.currentThread == loopThread)
      taskQueue += task
    else
      newTasks.updateAndGet(task :: _)
      selector.wakeup()

  def scheduleSingle(delay: Long)(task: => Unit): SingleTask =
    scheduleSingleAt(System.currentTimeMillis + delay)(task)
  def scheduleSingleAt(time: Long)(task: => Unit): SingleTask =
    SingleTask(task, time).also(schedule)

  def schedulePeriodic(delay: Long, period: Long)(task: => Unit): PeriodicTask =
    schedulePeriodicAt(System.currentTimeMillis + delay, period)(task)
  def schedulePeriodicAt(time: Long, period: Long)(task: => Unit): PeriodicTask =
    PeriodicTask(task, time, period).also(schedule)

  def register(channel: SelectableChannel, ops: Int)(task: => Unit): Task =
    new Task(task):
      private val key = channel.register(selector, ops, this)
      override def cancel(): Unit =
        super.cancel()
        key.cancel()

  def onClose(task: => Unit): Task =
    new Task(task):
      closeTasks.updateAndGet(this :: _)

  def threadSafe(task: => Unit): Unit =
    if (Thread.currentThread == loopThread) task
    else
      val singleTask = SingleTask(task, System.currentTimeMillis)
      newTasks.updateAndGet(singleTask :: _)
      selector.wakeup()


  private val loopThread = Thread: () =>
    while (!closing)
      taskQueue ++= newTasks.getAndSet(Nil)
      val timeUntilNext = runAvailable()
      selector.select(
        _.attachment match
          case task: Task => task.run()
          case _ => (),
        timeUntilNext.fold(5000L)(t => (t + 5) min 5000L),
      )
    for closeTask <- closeTasks.get.reverse do
      if (!closeTask.isCancelled)
        closeTask.run()
  loopThread.start()

  @tailrec private def runAvailable(): Option[Long] =
    val now = System.currentTimeMillis
    taskQueue.headOption match
      case Some(task) if task.isCancelled =>
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

  MainApp.shutdownHook:
    closing = true
    selector.wakeup()


  sealed abstract class Task(value: => Unit):
    @volatile private var cancelled = false
    def cancel(): Unit = cancelled = true
    def isCancelled: Boolean = cancelled
    private[TaskQueue] def run(): Unit =
      try value
      catch case error: Throwable => error.printStackTrace()

  sealed abstract class ScheduledTask(value: => Unit) extends Task(value):
    def time: Long
  class SingleTask private[TaskQueue] (value: => Unit, val time: Long) extends ScheduledTask(value)
  class PeriodicTask private[TaskQueue] (value: => Unit, @volatile private var _time: Long, val period: Long)
    extends ScheduledTask(value):
    def time: Long = _time
    private[TaskQueue] def time_=(value: Long): Unit = _time = value
