package p2p

import atlantafx.base.theme.Styles
import config.Language
import constant.Tr
import core.TaskQueue
import javafx.scene.text as jfxst
import p2p.base.{P2p, Peer}
import scalafx.Includes.jfxProperty2sfx
import scalafx.application.Platform.runLater
import scalafx.scene.text.{Text, TextFlow}
import util.also
import util.fx.TextFlowInterpolation.*
import util.fx.{NotificationStage, PopupNotification}


def registerNotifications(): Unit = TaskQueue.scheduleSingle(0):

  val listener = P2p.addPeerListener: (peer, change) =>
    val text = Language.config.map[jfxst.TextFlow]: lang =>
      change match
        case Peer.Add => flow"${peer.nick.bold} ${Tr.joined(lang)} ${P2p.roomName().bold}"
        case Peer.Die => flow"${peer.nick.bold} ${Tr.left(lang)} ${P2p.roomName().bold}"
        case Peer.Rename(oldNick) => flow"${oldNick.bold} ${Tr.isNow(lang)} ${peer.nick.bold}"

    val styleCl = change match
      case Peer.Add => Styles.SUCCESS
      case Peer.Die => Styles.DANGER
      case Peer.Rename(_) => Styles.WARNING

    runLater:
      NotificationStage.notifications += new PopupNotification:
        graphic <== text
        styleClass += styleCl

  P2p.peers.values.foreach(listener.onPeer(_, Peer.Add))
