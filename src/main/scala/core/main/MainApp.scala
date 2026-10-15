package core.main

import atlantafx.base.controls.ModalPane
import constant.Constants
import core.MainMenu
import scalafx.Includes.jfxControl2sfx
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.view.TorrentView
import util.fx.{NotificationStage, getIconImages}

import java.lang.ref.Cleaner


object MainApp extends JFXApp3 with OnShutDown:
  setUnixLocale()
  System.setProperty("jna.encoding", "UTF8")

  val cleaner: Cleaner = Cleaner.create

  lazy val root = new VBox(new MainMenu, TorrentView)
  lazy val modal = new ModalPane

  def start(): Unit =
    stage = new PrimaryStage with NotificationStage:
      title = Constants.appName
      icons.addAll(getIconImages)
      scene = new Scene(new StackPane):
        content = Seq[Node](MainApp.root, modal)

    config.Theme
    p2p.RoomSync
    p2p.registerNotifications()
