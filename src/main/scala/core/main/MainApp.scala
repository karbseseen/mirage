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


object MainApp extends JFXApp3
  with UnixLocale
:
  class Root private[MainApp] extends VBox(new MainMenu)

  private given root: Root = new Root
  private given modal: ModalPane = new ModalPane
  private given notifications: NotificationBox = new NotificationBox

  def start(): Unit =
    stage = new PrimaryStage:
      title <== Tr.appName
      scene = new Scene(new StackPane, 800, 500):
        content = Seq[Node](MainApp.root, modal, notifications)
    LibTorrentLoad()
