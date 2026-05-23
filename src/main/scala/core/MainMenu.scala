package core

import config.{Language, Theme}
import constant.Tr
import core.main.MainApp
import scalafx.beans.BeanIncludes.jfxProperty2sfx
import scalafx.scene.control.*
import scalafx.scene.layout.{HBox, Priority, Region}
import torrent.view.TorrentModal


class MainMenu extends HBox:

  private val addMenu = new Menu:
    text <== Tr.add

    items += new MenuItem:
      text <== Tr.torrent
      onAction = _ => MainApp.modal.show(TorrentModal.add)

    items += new MenuItem:
      text <== Tr.file
      onAction = _ => MainApp.modal.show(TorrentModal.create)


  private val space = new Region:
    styleClass += "menu-bar"
    hgrow = Priority.Always


  private val settingsMenu = new Menu:
    text <== Tr.settings

    items += new Menu:
      private val group = new ToggleGroup
      text <== Tr.language
      items = Language.values.map { language =>
        new RadioMenuItem(language.name):
          toggleGroup = group
          if (language == Language.config()) selected = true
          onAction = _ => Language.config() = language
      }

    items += new Menu:
      private val group = new ToggleGroup
      text <== Tr.theme
      items = Theme.map.values.toList.sortBy(_.getName).map { theme =>
        new RadioMenuItem(theme.getName):
          toggleGroup = group
          if (theme == Theme.config()) selected = true
          onAction = _ => Theme.config() = theme
      }

    items += new MenuItem:
      text <== Tr.toUpdate
      onAction = _ => new UpdateStage().show()

    items += new MenuItem:
      text <== Tr.clearToken
      visible <== GithubToken.property.isNotNull
      onAction = _ => GithubToken.clear(Some(MainApp.notifications))


  private val leftMenu = new MenuBar { menus = Seq(addMenu) }
  private val rightMenu = new MenuBar { menus = Seq(settingsMenu) }

  maxWidth = Double.MaxValue
  hgrow = Priority.Always
  children = Seq(leftMenu, space, rightMenu)
