package util

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.annotation.tailrec
import scala.collection.mutable


/**All of its methods are thread unsafe and intended to be called in scheduled tasks*/
class SingleThreadExecutor extends AutoCloseable:

  val impl: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor

  private val taskQueue = mutable.PriorityQueue.empty[(ScheduledFuture[?], Long)](using Ordering.by(_._2))
  private val updatedTasks = mutable.Set.empty[ScheduledFuture[?]]

  def close(): Unit = impl.close()

  def scheduleSingle(delay: Long, units: TimeUnit = MILLISECONDS)(task: => Unit): ScheduledFuture[?] =
    val runnable: Runnable = () => task
    impl.schedule(runnable, delay, units).also(updatedTasks += _)

  def schedulePeriodic(delay: Long, period: Long, units: TimeUnit = MILLISECONDS)(task: => Unit): ScheduledFuture[?] =
    class Task extends Runnable:
      def run(): Unit =
        task
        updatedTasks += future
      val future: ScheduledFuture[?] = impl.scheduleWithFixedDelay(this, delay, period, units)
    Task().future

  def timeUntilNext: Option[Long] =
    val now = System.currentTimeMillis
    taskQueue ++= updatedTasks.iterator
      .filter { task => !task.isDone && !task.isCancelled }
      .map { task => (task, -(now + task.getDelay(MILLISECONDS))) }
    updatedTasks.clear()
    timeUntilNextInner

  @tailrec private def timeUntilNextInner: Option[Long] =
    taskQueue.headOption match
      case None => None
      case Some(task, time) if !task.isDone && !task.isCancelled => Some(time - System.currentTimeMillis)
      case _ =>
        taskQueue.dequeue()
        timeUntilNextInner
