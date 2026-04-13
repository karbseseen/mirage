package core.main

import atlantafx.base.controls.ModalPane
import constant.Tr
import core.MainMenu
import fx.NotificationBox
import scalafx.Includes.jfxControl2sfx
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.TorrentView


object MainApp extends JFXApp3
  with UnixLocale
:
  lazy val root = new VBox(new MainMenu, new TorrentView)
  lazy val modal = new ModalPane
  lazy val notifications = new NotificationBox

  def start(): Unit =
    stage = new PrimaryStage:
      title <== Tr.appName
      scene = new Scene(new StackPane, 800, 500):
        content = Seq[Node](MainApp.root, modal, notifications)
