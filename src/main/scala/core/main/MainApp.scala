package core.main

import constant.Constants
import core.MainMenu
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.view.TorrentView
import util.fx.{ModalPane, NotificationStage, setIcon}

import java.lang.ref.Cleaner


object MainApp extends JFXApp3 with OnShutDown:
  setUnixLocale()
  System.setProperty("jna.encoding", "UTF8")

  val cleaner: Cleaner = Cleaner.create

  lazy val root = new VBox(new MainMenu, TorrentView)
  lazy val modal = new ModalPane

  def start(): Unit =
    config.Theme

    stage = new PrimaryStage with NotificationStage:
      this.setIcon()
      title = Constants.appName
      scene = new Scene(new StackPane):
        content = Seq[Node](MainApp.root, modal, notificationBox)

    p2p.registerNotifications()
    player.PlayerSync
