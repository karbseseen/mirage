package core.main

import constant.Constants
import core.MainMenu
import javafx.beans.{InvalidationListener, Observable}
import scalafx.Includes.jfxScene2sfx
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.Dimension2D
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

  def decoratorSize: Dimension2D = _decoratorSize
  private var _decoratorSize = Dimension2D(0, 0)

  def start(): Unit =
    config.Theme

    stage = new PrimaryStage with NotificationStage:
      this.setIcon()
      title = Constants.appName
      scene = new Scene(new StackPane):
        content = Seq[Node](MainApp.root, modal, notificationBox)

    new InvalidationListener:
      private val scene = stage.scene()
      private val properties = IArray(stage.width, stage.height, scene.width, scene.height)
      def invalidated(o: Observable): Unit =
        if (properties.forall(_() > 0))
          _decoratorSize = Dimension2D(stage.width() - scene.width(), stage.height() - scene.height())
          if (decoratorSize.width > 0 || decoratorSize.height > 0)
            properties.foreach(_.removeListener(this))
      properties.foreach(_.addListener(this))

    p2p.registerNotifications()
    player.PlayerSync
