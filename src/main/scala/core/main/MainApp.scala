package core.main

import atlantafx.base.controls.ModalPane
import atlantafx.base.theme.Styles
import constant.{Tr, Translate}
import core.MainMenu
import fx.{NotificationBox, PopupNotification}
import scalafx.Includes.{jfxControl2sfx, jfxStringProperty2sfx}
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.application.{JFXApp3, Platform}
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.{Node, Scene}
import torrent.view.torrentView


object MainApp extends JFXApp3
  with UnixLocale
  with OnShutDown
:
  lazy val root = new VBox(new MainMenu, torrentView)
  lazy val modal = new ModalPane
  lazy val notifications = new NotificationBox

  def start(): Unit =
    config.Theme
    config.Language
    stage = new PrimaryStage:
      title <== Tr.appName
      scene = new Scene(new StackPane, 800, 500):
        content = Seq[Node](MainApp.root, modal, notifications)

  /**Thread-safe*/
  def showError(message: String | Translate): Unit =
    val eitherMessage = message match
      case str: String => Left(str)
      case tr: Translate => Right(tr)
    Platform.runLater:
      notifications.children += new PopupNotification(eitherMessage.left.toOption.orNull):
        eitherMessage.foreach(this.message <== _)
        styleClass += Styles.DANGER
  /**Thread-safe*/
  def showError(error: Throwable): Unit = showError(error.getMessage)
