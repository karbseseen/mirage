package core.main

import atlantafx.base.controls.ModalPane
import atlantafx.base.theme.Styles
import constant.Constants
import core.MainMenu
import javafx.beans.value as jfxbv
import scalafx.Includes.{jfxControl2sfx, jfxStringProperty2sfx}
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.application.{JFXApp3, Platform}
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.view.TorrentView
import util.fx.{PopupNotification, getIconImages, NotificationStage}

import java.lang.ref.Cleaner


object MainApp extends JFXApp3 with OnShutDown:
  setUnixLocale()
  System.setProperty("jna.encoding", "UTF8")

  val cleaner: Cleaner = Cleaner.create

  lazy val root = new VBox(new MainMenu, TorrentView)
  lazy val modal = new ModalPane

  def start(): Unit =
    config.Theme
    config.Language
    p2p.base.P2p
    p2p.RoomSync

    stage = new PrimaryStage with NotificationStage:
      title = Constants.appName
      icons.addAll(getIconImages)
      scene = new Scene(new StackPane):
        content = Seq[Node](MainApp.root, modal)
