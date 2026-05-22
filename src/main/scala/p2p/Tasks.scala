package p2p

import util.also

import java.util.concurrent.CountDownLatch
import scala.annotation.tailrec
import scala.collection.mutable


sealed trait Task:
  def time: Long
  def cancel(): Unit


/**All of its methods are thread unsafe and intended to be called in scheduled tasks*/
trait Tasks private[p2p]:

  private val taskQueue = mutable.PriorityQueue.empty[Task](using Ordering.by(-_.time))
  private val closeLatch = CountDownLatch(1)
  @volatile private var closed = false


  def scheduleSingle(delay: Long)(task: => Unit): p2p.Task =
    scheduleSingleAt(System.currentTimeMillis + delay)(task)
  def scheduleSingleAt(time: Long)(task: => Unit): p2p.Task =
    Task(task, time).also(taskQueue += _)

  def schedulePeriodic(delay: Long, period: Long)(task: => Unit): p2p.Task =
    schedulePeriodicAt(System.currentTimeMillis + delay, period)(task)
  def schedulePeriodicAt(time: Long, period: Long)(task: => Unit): p2p.Task =
    PeriodicTask(task, time, period).also(taskQueue += _)

  def close(): CountDownLatch =
    closed = true
    closeLatch


  protected def loop(timeUntilNext: Option[Long]): Unit
  protected def onClose(): Unit

  protected val loopThread = Thread: () =>
    while (!closed) loop(runAvailable())
    onClose()
    closeLatch.countDown()

  @tailrec private def runAvailable(): Option[Long] =
    val now = System.currentTimeMillis
    taskQueue.headOption match
      case Some(task) if task.cancelled =>
        taskQueue.dequeue()
        runAvailable()
      case Some(task) if task.time <= now =>
        taskQueue.dequeue()
        try task.run()
        catch case error: Throwable => error.printStackTrace()
        Some(task).collect:
          case periodic: PeriodicTask => taskQueue += periodic.next
        runAvailable()
      case Some(task) => Some(task.time - now)
      case None => None


  private class Task(value: => Unit, var time: Long) extends p2p.Task:
    @volatile private[Tasks] var cancelled = false
    def cancel(): Unit = cancelled = true
    def run(): Unit = value

  private class PeriodicTask(value: => Unit, time: Long, period: Long) extends Task(value, time):
    def next = PeriodicTask(value, System.currentTimeMillis + period, period)
