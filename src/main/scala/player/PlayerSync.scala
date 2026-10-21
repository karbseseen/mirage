package player

import core.TaskQueue
import core.TaskQueue.Task
import p2p.base.Message.Counter.{compare, inc, set}
import p2p.base.Message.{Counter, PlayerState}
import p2p.base.{P2p, Peer}
import player.PlayerSync.*
import player.PlayerSync.SetFlag.{counter, force, send}
import player.Media.FileId
import scalafx.application.Platform.runLater
import uk.co.caprica.vlcj.player.base.MediaPlayer
import util.{also, listIf}

import scala.collection.mutable
import scala.language.implicitConversions


object PlayerSync:

  private inline val AcceptTimeDiff = 1100
  private inline val WaitTimeDiff = 4000
  private inline val TargetDiscardAfter = 1000
  private inline val MinSyncPeriod = 1500

  private var fileCounter = Counter.zero
  private val targetFile = new Target[FileId]
  private var instance: Option[PlayerSync] = None

  def getTargetFileId: Option[FileId] = targetFile.peek.map(_.value)


  P2p.addMessageHandler[PlayerState]: (peerState, peer) =>
    val tasks = sync:
      val fileDiff = instance.fold(1)(_.myState.fileId.compareTo(peerState.fileId))
      if (fileDiff == 0)
        instance.fold[UnsyncTasks](Nil)(_.onPeerState(peerState, peer))
      else
        val fileCounterDiff = fileCounter compare peerState.fileCounter
        val start = (fileCounterDiff < 0 || fileCounterDiff == 0 && fileDiff < 0) &&
          targetFile.trySet(peerState.fileId, peerState.fileCounter)
        if (start) runLater { Media.play(peerState.fileId) }
        Nil
    tasks.foreach(_())

  private def onFileId(fileId: FileId): Unit =
    fileCounter = targetFile.getIf(_ == fileId) counterOrElse fileCounter.inc


  enum Pause:
    case True, False, Toggle

  object Pause:
    implicit inline def apply(value: Boolean): Pause =
      if (value) Pause.True else Pause.False


  opaque type SetFlag = Int
  val EmptyFlag: SetFlag = 0
  val NoSend: SetFlag = 1
  val Force: SetFlag = 2
  private val CounterFlag: SetFlag = 4

  object SetFlag:
    extension (flag: SetFlag)
      inline def +(inline other: SetFlag): Int = flag | other
      inline def send:  Boolean = (flag & NoSend) == 0
      inline def force: Boolean = (flag & Force) != 0
      inline def counter: Option[Counter] =
        Option.when((flag & CounterFlag) != 0)((flag >> 24).asInstanceOf[Counter])

  private implicit inline def fromRemote(counter: Counter): SetFlag =
    (counter.asInstanceOf[Int] << 24) | CounterFlag | NoSend


  private class Target[T]:
    private var value: Option[Target.Value[T]] = None

    def trySet(value: T, flag: SetFlag): Boolean =
      if (flag.force) this.value = None else discardExpired()
      val success = this.value.isEmpty
      if (success) this.value = Some(Target.Value(value, flag))
      success

    def getIf(condition: T => Boolean): Option[Target.Value[T]] =
      discardExpired()
      val result = value.filter(value => condition(value.value))
      if (result.nonEmpty) value = None
      result

    def peek: Option[Target.Value[T]] =
      discardExpired()
      value

    private def discardExpired(): Unit =
      value = value.filter(_.discardAt > System.currentTimeMillis)

  private object Target:
    class Value[T](val value: T, val flag: SetFlag):
      val discardAt: Long = System.currentTimeMillis + TargetDiscardAfter

  implicit def toFlag(value: Target.Value[?]): SetFlag = value.flag

  extension (target: Option[Target.Value[?]])
    private infix def counterOrElse(ifEmpty: => Counter): Counter =
      target.flatMap(_.counter).getOrElse(ifEmpty)


  private type UnsyncTasks = List[() => Unit]
  extension (tasks: UnsyncTasks)
    private def +(task: () => Unit) = task :: tasks

  private sealed trait PeerToWait { val peer: Peer }
  private case class BufferingPeer(peer: Peer) extends PeerToWait
  private case class LaggingPeer(peer: Peer) extends PeerToWait

  private inline def sync[T](inline func: => T): T = synchronized(func)


trait PlayerSync:

  protected val vlc: MediaPlayer
  protected val media: Media

  private val initFileCounter = sync:
    onFileId(media.fileId)
    PlayerSync.instance = Some(this)
    PlayerSync.fileCounter

  private var myState = PlayerState(media.fileId, initFileCounter)
  private val targetTime = new Target[Long]
  private val targetPause = new Target[Boolean]

  private val peerWaiter = new PeerWaiter
  private var idleTask: Option[Task] = None

  private var lastSendTime = 0L


  def close(): Unit = sync:
    PlayerSync.instance = None
    PlayerSync.fileCounter = myState.fileCounter
    peerWaiter.close()
    idleTask.foreach(_.cancel())


  private def onPeerState(peerState: PlayerState, peer: Peer): UnsyncTasks =
    if (myState.fileCounter.compare(peerState.fileCounter) < 0)
      myState = myState.copy(fileCounter = peerState.fileCounter)

    val tasks =
      if (peerState.buffering) peerWaiter.waitFor(BufferingPeer(peer), WaitTimeDiff)
      else peerWaiter.releaseWait(BufferingPeer(peer))

    if (peerWaiter.isWaiting || myState.buffering) tasks
    else tasks ::: onPeerStateInner(peerState, peer)

  private def onPeerStateInner(peerState: PlayerState, peer: Peer): UnsyncTasks =
    var tasks: UnsyncTasks = Nil

    val pauseDiff = myState.pauseCounter compare peerState.pauseCounter
    if (pauseDiff < 0)
      if (myState.pause != peerState.pause) tasks += { () => setPause(peerState.pause, peerState.pauseCounter) }
      else myState = myState.copy(pauseCounter = peerState.pauseCounter)

    val seekDiff = myState.seekCounter compare peerState.seekCounter
    if (seekDiff <= 0)
      val peerTime = peerState.time + (if (peerState.pause) 0 else peer.latency)
      val timeDiff = vlc.status.time - peerTime
      if (seekDiff < 0 || timeDiff > 0) math.abs(timeDiff) match
        case diff if diff < AcceptTimeDiff                    => ()
        case diff if diff < WaitTimeDiff && !peerState.pause  => tasks ++= peerWaiter.waitFor(LaggingPeer(peer), diff - 100)
        case diff                                             => tasks += (() => setTime(peerTime, peerState.seekCounter))

    val speedDiff = myState.speedCounter compare peerState.speedCounter
    if (speedDiff < 0 || speedDiff == 0 && myState.speedX10 > peerState.speedX10)
      tasks += { () => setSpeedX10(peerState.speedX10, peerState.speedCounter) }

    val titleDiff = myState.titleCounter compare peerState.titleCounter
    if (titleDiff < 0 || titleDiff == 0 && myState.title > peerState.title)
      tasks += { () => setCustomTitle(peerState.title, peerState.titleCounter) }

    tasks


  def setTime(time: Long, flag: SetFlag = EmptyFlag): Boolean =
    sync(targetTime.trySet(time, flag)).also:
      if (_) vlc.controls.setTime(time)

  protected def onTime(time: Long): Unit = sync:
    val target = targetTime.getIf(target => (target - time).abs < AcceptTimeDiff)
    val counter = target counterOrElse target.fold(myState.seekCounter)(_ => myState.seekCounter.inc)
    myState = myState.copy(time = time, seekCounter = counter)

    onBuffering(false)

    val send = target match
      case Some(target) => target.send
      case None => time - lastSendTime >= MinSyncPeriod || time < lastSendTime
    if (send) sendToAll()


  def setPause(pause: PlayerSync.Pause, flag: SetFlag = EmptyFlag): Boolean =
    val value = pause match
      case Pause.True => true
      case Pause.False => false
      case Pause.Toggle => !myState.pause
    sync:
      peerWaiter.reset()
      myState.pause != value && targetPause.trySet(value, flag)
    .also:
      if (_) vlc.controls.setPause(value)

  protected def onPause(pause: Boolean): Unit = sync:
    val target = targetPause.getIf(_ == pause)
    val counter = target counterOrElse myState.pauseCounter.set(pause)
    myState = myState.copy(pauseCounter = counter, time = vlc.status.time)

    if (pause || !onBuffering(false)) updateIdleTask()
    if (target.forall(_.send)) sendToAll()


  def setSpeedX10(speedX10: Int, flag: SetFlag = EmptyFlag): Boolean =
    vlc.controls.setRate(speedX10 / 10f).also:
      if (_) sync:
        val counter = flag.counter getOrElse myState.speedCounter.inc
        myState = myState.copy(speedX10 = speedX10.toByte, speedCounter = counter, time = vlc.status.time)
        if (flag.send) sendToAll()


  def setCustomTitle(title: String, flag: SetFlag = EmptyFlag): Unit = sync:
    val counter = flag.counter getOrElse myState.titleCounter.inc
    myState = myState.copy(title = title, titleCounter = counter, time = vlc.status.time)
    if (flag.send) sendToAll()


  protected def onBuffering(buffering: Boolean): Boolean = sync:
    (myState.buffering != buffering).also:
      if (_)
        myState = myState.copy(buffering = buffering, time = vlc.status.time)
        updateIdleTask()


  private def sendToAll(): Unit =
    if (!peerWaiter.isWaiting)
      lastSendTime = myState.time
      P2p.sendToAll(myState)

  private def updateIdleTask(): Unit =
    val idle = myState.pause || myState.buffering
    if (idle && idleTask.isEmpty)
      idleTask = Some:
        TaskQueue.schedulePeriodic(MinSyncPeriod, MinSyncPeriod):
          sync(sendToAll())
    else if (!idle)
      idleTask.foreach: task =>
        task.cancel()
        idleTask = None


  private class PeerWaiter:
    private val peerMap = mutable.Map[PeerToWait, Task]()
    private var wasPlaying = false

    private val listener = P2p.addPeerListener:
      case (peer, Peer.Die) => peerMap.remove(BufferingPeer(peer))
      case _ => ()

    def isWaiting: Boolean = peerMap.nonEmpty

    def close(): Unit = P2p.removePeerListener(listener)

    def waitFor(peer: PeerToWait, time: Long): UnsyncTasks =
      val wasEmpty = peerMap.isEmpty
      val at = System.currentTimeMillis + time
      peerMap.updateWith(peer):
        case None => Some(Task(at, peer))
        case Some(task) if task.time < at => task.cancel(); Some(Task(at, peer))
        case Some(task) => Some(task)
      listIf(wasEmpty) { () => wasPlaying = setPause(true) }

    def releaseWait(peer: PeerToWait): UnsyncTasks =
      val removed = peerMap.remove(peer)
      removed.foreach(_.cancel())
      listIf(removed.nonEmpty && peerMap.isEmpty && wasPlaying) { () => setPause(false) }

    def reset(): Unit =
      peerMap.values.foreach(_.cancel())
      peerMap.clear()

    private class Task(at: Long, peer: PeerToWait) extends TaskQueue.SingleTask(at):
      TaskQueue.schedule(this)
      def run(): Unit = sync(if (!isCancelled) releaseWait(peer))
