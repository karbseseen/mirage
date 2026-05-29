package p2p

import byte_codec.ByteCodec.{CompactUInt, CompactULong}
import com.frostwire.jlibtorrent.{AddTorrentParams, TorrentFlags}
import config.Conf
import p2p.base.Message.{PlayerState, TorrentMagnetRequest, TorrentMagnetResponse}
import p2p.base.{MessageHandler, P2p, Task}
import scalafx.Includes.jfxObservableValue2sfx
import scalafx.application.Platform.runLater
import torrent.Hash.hash
import torrent.{Hash, Torrent, TorrentMedia}
import util.JavaUtil
import vlc.VlcStage

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator


object RoomSync:

  val mergedState = AtomicReference(PlayerState(hash = Hash.empty, fileIndex = -1, fileCounter = 0))
  private var waitTask = Option.empty[Task]

  P2p.addMessageHandler(playerSyncHandler)
  P2p.addMessageHandler(magnetRequestHandler)
  P2p.addMessageHandler(magnetResponseHandler)


  private def playerSyncHandler: MessageHandler[PlayerState] = (peerState, peer) =>

    enum Change:
      case No, File, Player
    var change: Change = Change.No
    var fileCounterDiff, seekDiff, speedDiff, pauseDiff = 0

    val oldState = mergedState.getAndUpdate: myState =>

      inline def counterDiff(getCounter: PlayerState => Byte) =
        (getCounter(myState), getCounter(peerState)) match
          case (0, 0) => 0
          case (0, _) => -1
          case (_, 0) => 1
          case (myCounter, peerCounter) => (myCounter - peerCounter).toByte.toInt

      inline def getMax[T](diff: Int, getter: PlayerState => T, ifNull: => PlayerState = myState) =
        getter:
          if (diff > 0) myState
          else if (diff < 0) peerState
          else ifNull

      fileCounterDiff = counterDiff(_.fileCounter)
      seekDiff        = counterDiff(_.seekCounter)
      speedDiff       = counterDiff(_.speedCounter)
      pauseDiff       = counterDiff(_.pauseCounter)

      val fileCmp =
        if (myState.hash eq Hash.empty) -1
        else
          val byHash = Hash.ordering.compare(myState.hash, peerState.hash)
          if (byHash != 0) byHash else myState.fileIndex - peerState.fileIndex

      if (fileCmp != 0 && fileCounterDiff > 0 || fileCmp > 0 && fileCounterDiff == 0)
        change = Change.No
        myState
      else if (fileCmp != 0)
        change = Change.File
        peerState
      else
        change = Change.Player
        peerState.copy(
          fileCounter   = getMax(fileCounterDiff, _.fileCounter),
          time          = getMax(seekDiff,        _.time),
          seekCounter   = getMax(seekDiff,        _.seekCounter),
          speedX10      = getMax(speedDiff,       _.speedX10, if (myState.speedX10 < peerState.speedX10) myState else peerState),
          speedCounter  = getMax(speedDiff,       _.speedCounter),
          pauseCounter  = getMax(pauseDiff,       _.pauseCounter),
        )

    change match
      case Change.No => ()

      case Change.File => runLater:
        val torrent = Torrent.all.find(_.hash == peerState.hash)
        if (torrent.isEmpty) peer.send(TorrentMagnetRequest(peerState.hash))
        val media = torrent.filter(_.handle.torrentFile != null).map(TorrentMedia(_, peerState.fileIndex))
        VlcStage.play(media, peerState.playerOptions)

      case Change.Player => VlcStage.instance.foreach: stage =>
        val player = stage.player

        def updateWaitTask(delay: Long): Unit =
          waitTask.foreach(_.cancel())
          waitTask = Some:
            P2p.scheduleSingle(delay):
              if (VlcStage.instance.contains(stage))
                player.controls.play()
          player.controls.setPause(true)

        if (!stage.isBuffering)
          if (!oldState.pause && peerState.pause && pauseDiff <= 0) player.controls.setPause(true)
          else if (oldState.pause && !peerState.pause && pauseDiff < 0) player.controls.setPause(false)

        if (seekDiff <= 0)
          val peerTime = peerState.time + (if (peerState.pause) 0 else peer.latency)
          val timeDiff = player.status.time - peerTime
          if (seekDiff < 0 || timeDiff > 0) math.abs(timeDiff) match
            case diff if diff < AcceptTimeDiff => ()
            case diff if diff < WaitTimeDiff => if (!peerState.pause && !stage.isBuffering) updateWaitTask(diff - 100)
            case _ => player.controls.setTime(peerTime)

        if (speedDiff <= 0)
          if (speedDiff < 0 || oldState.speedX10 > peerState.speedX10)
            player.controls.setRate(peerState.speedX10 / 10f)
            stage.showNewSpeedText(peerState.speedX10)


  private def magnetRequestHandler: MessageHandler[TorrentMagnetRequest] = (message, peer) =>
    for torrent <- Option(Torrent.session.find(message.hash)) do
      peer.send(TorrentMagnetResponse(message.hash, torrent.makeMagnetUri.getBytes))


  private def magnetResponseHandler: MessageHandler[TorrentMagnetResponse] = (message, peer) =>
    if (mergedState.get.hash == message.hash)
      val hash = AddTorrentParams.parseMagnetUri(String(message.magnet)).hash
      val known: Torrent.Known = (torrent, info) =>
        Torrent.Known.default.onMetadata(torrent, info)
        torrent.handle.resume()
        torrent.handle.setFlags(TorrentFlags.AUTO_MANAGED)
        for state <- Some(mergedState.get).filter(_.hash == message.hash) do
          runLater:
            VlcStage.play(Some(TorrentMedia(torrent, state.fileIndex)), state.playerOptions)

      val confSaveDir = File(Conf.torrentSave())
      val saveDir = if (confSaveDir.isDirectory) confSaveDir else File(".")
  
      Torrent.knownTorrents.updateAndGet(_ + (hash -> known))
      Torrent.add(String(message.magnet), saveDir)


  extension (state: PlayerState) private def playerOptions =
    s"start-time=${state.time / 1000}" ::
      s"rate=${state.speedX10 / 10.0}" ::
      Option.when(state.pause)("start-paused").toList


  private inline val AcceptTimeDiff = 1000
  private inline val WaitTimeDiff   = 8000
