package p2p

import util.also

import scala.annotation.tailrec
import scala.collection.mutable


sealed trait Task:
  def time: Long
  def cancel(): Unit


trait Tasks private[p2p]:

  private val taskQueue = mutable.PriorityQueue.empty[Task](using Ordering.by(-_.time))
  private val alienTasks = mutable.Buffer.empty[Task]
  private val closeTasks = mutable.Buffer.empty[() => Unit]

  @volatile private var hasAlienTask = false
  @volatile private var closing = false
  private var closed = false


  def scheduleSingle(delay: Long)(task: => Unit): p2p.Task =
    scheduleSingleAt(System.currentTimeMillis + delay)(task)
  def scheduleSingleAt(time: Long)(task: => Unit): p2p.Task =
    SingleTask(task, time).also(addTask)

  def schedulePeriodic(delay: Long, period: Long)(task: => Unit): p2p.Task =
    schedulePeriodicAt(System.currentTimeMillis + delay, period)(task)
  def schedulePeriodicAt(time: Long, period: Long)(task: => Unit): p2p.Task =
    PeriodicTask(task, time, period).also(addTask)

  def close(): Unit = closing = true
  def scheduleAfterStop(task: => Unit): Unit =
    def tryTask(): Unit =
      try task
      catch case error: Throwable => error.printStackTrace()
    closeTasks.synchronized:
      if (closed) tryTask()
      else closeTasks += tryTask

  private def addTask(task: Task): Unit =
    if (Thread.currentThread == loopThread) taskQueue += task
    else alienTasks.synchronized:
      alienTasks += task
      hasAlienTask = true


  protected def start(): Unit = loopThread.start()
  protected def loop(timeUntilNext: Option[Long]): Unit


  private val loopThread = Thread: () =>
    while (!closing)
      if (hasAlienTask) alienTasks.synchronized:
        taskQueue ++= alienTasks
        alienTasks.clear()
        hasAlienTask = false
      loop(runAvailable())
    closeTasks.synchronized:
      closeTasks.foreach(_())
      closed = true

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
  private sealed abstract class Task(value: => Unit) extends p2p.Task:
    @volatile var cancelled = false
    def cancel(): Unit = cancelled = true
    def run(): Unit =
      try value
      catch case error: Throwable => error.printStackTrace()
