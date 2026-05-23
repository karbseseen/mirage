package core

import constant.Tr
import core.main.MainApp
import org.kordamp.ikonli.fluentui.{FluentUiRegularAL, FluentUiRegularMZ}
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.jfxText2sfxText
import scalafx.application.Platform.runLater
import scalafx.scene.control.*
import scalafx.scene.layout.{HBox, Priority, Region}
import torrent.view.TorrentModal


class MainMenu extends HBox:

  private val addMenu = new Menu:
    text <== Tr.add
    graphic = FontIcon(FluentUiRegularAL.ADD_24)
    items += new MenuItem:
      text <== Tr.torrent
      onAction = _ => MainApp.modal.show(TorrentModal.add)
    items += new MenuItem:
      text <== Tr.file
      onAction = _ => MainApp.modal.show(TorrentModal.create)

  private val space = new Region:
    styleClass += "menu-bar"
    hgrow = Priority.Always

  private val settingsMenu = new Menu(null, FontIcon(FluentUiRegularMZ.SETTINGS_24)):
    items += new MenuItem
    onShowing = event =>
      MainApp.modal.show(new MainSettings)
      runLater(hide())

  private val leftMenu = new MenuBar { menus = Seq(addMenu) }
  private val rightMenu = new MenuBar { menus = Seq(settingsMenu) }
  children = Seq(leftMenu, space, rightMenu)
