package core.main

import atlantafx.base.controls.ModalPane
import atlantafx.base.theme.Styles
import constant.Constants
import core.MainMenu
import fx.{NotificationBox, PopupNotification}
import javafx.beans.value as jfxbv
import scalafx.Includes.{jfxControl2sfx, jfxStringProperty2sfx}
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.application.{JFXApp3, Platform}
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.view.TorrentView

import java.lang.ref.Cleaner


object MainApp extends JFXApp3
  with UnixLocale
  with OnShutDown
:
  System.setProperty("jna.encoding", "UTF8")

  val cleaner: Cleaner = Cleaner.create

  lazy val root = new VBox(new MainMenu, TorrentView)
  lazy val modal = new ModalPane
  lazy val notifications = new NotificationBox

  def start(): Unit =
    config.Theme
    config.Language
    p2p.base.P2p
    p2p.RoomSync

    stage = new PrimaryStage:
      title = Constants.appName
      icons.addAll(fx.getIconImages)
      scene = new Scene(new StackPane):
        content = Seq[Node](MainApp.root, modal, notifications)

  /**Thread-safe*/
  def showError(text: String | jfxbv.ObservableValue[String]): Unit =
    Platform.runLater:
      notifications.children += new PopupNotification:
        text match
          case str: String => message = str
          case observable: jfxbv.ObservableValue[String] => message <== observable
        styleClass += Styles.DANGER
  /**Thread-safe*/
  def showError(error: Throwable): Unit = showError(error.getMessage)
