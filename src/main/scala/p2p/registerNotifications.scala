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
import util.fx.{NotificationStage, PopupNotification}


def registerNotifications(): Unit = TaskQueue.scheduleSingle(0):
  inline def boldText(value: String) = Text(value).also(_.style = "-fx-font-weight: bold")

  val listener = P2p.addPeerListener: (peer, change) =>
    val text = Language.config.map[jfxst.TextFlow]: lang =>
      new TextFlow:
        children = change match
          case Peer.Add => boldText(peer.nick) :: Text(" " + Tr.joined(lang) + " ") :: boldText(P2p.roomName()) :: Nil
          case Peer.Die => boldText(peer.nick) :: Text(" " + Tr.left(lang) + " ") :: boldText(P2p.roomName()) :: Nil
          case Peer.Rename(oldNick) => boldText(oldNick) :: Text(" " + Tr.isNow(lang) + " ") :: boldText(peer.nick) :: Nil

    val styleCl = change match
      case Peer.Add => Styles.SUCCESS
      case Peer.Die => Styles.DANGER
      case Peer.Rename(_) => Styles.WARNING

    runLater:
      NotificationStage.view.children += new PopupNotification:
        graphic <== text
        styleClass += styleCl

  P2p.peers.values.foreach(listener.onPeer(_, Peer.Add))
