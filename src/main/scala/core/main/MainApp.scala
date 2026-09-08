package core.main

import atlantafx.base.controls.ModalPane
import atlantafx.base.theme.Styles
import constant.{Tr, Translate}
import core.MainMenu
import fx.{NotificationBox, PopupNotification}
import javafx.beans.property.StringPropertyBase
import javafx.beans.property.adapter.JavaBeanStringProperty
import javafx.beans.value as jfxbv
import scalafx.Includes.{jfxControl2sfx, jfxStringProperty2sfx}
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.application.{JFXApp3, Platform}
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.view.TorrentView


object MainApp extends JFXApp3
  with UnixLocale
  with OnShutDown
:
  lazy val root = new VBox(new MainMenu, TorrentView)
  lazy val modal = new ModalPane
  lazy val notifications = new NotificationBox

  def start(): Unit =
    config.Theme
    config.Language
    p2p.base.P2p
    p2p.RoomSync

    stage = new PrimaryStage:
      title <== Tr.appName
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
