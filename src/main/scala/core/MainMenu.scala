package core

import config.{Language, Theme}
import constant.Tr
import core.main.MainApp
import scalafx.beans.BeanIncludes.jfxProperty2sfx
import scalafx.geometry.Pos
import scalafx.scene.control.*
import torrent.view.TorrentModal


object MainMenu:

  val addMenu: Menu =
    val torrentAdd = new MenuItem:
      text <== Tr.torrent
      onAction = _ => MainApp.modal.show(TorrentModal.add)

    val torrentCreate = new MenuItem:
      text <== Tr.file
      onAction = _ => MainApp.modal.show(TorrentModal.create)

    new Menu:
      text <== Tr.add
      items = Seq(torrentAdd, torrentCreate)


  val settingsMenu: Menu =
    val language = new Menu:
      private val group = new ToggleGroup
      text <== Tr.language
      items = Language.values.map { language =>
        new RadioMenuItem(language.name):
          toggleGroup = group
          if (language == Language.config()) selected = true
          onAction = _ => Language.config() = language
      }

    val theme = new Menu:
      private val group = new ToggleGroup
      text <== Tr.theme
      items = Theme.map.values.toList.sortBy(_.getName).map { theme =>
        new RadioMenuItem(theme.getName):
          toggleGroup = group
          if (theme == Theme.config()) selected = true
          onAction = _ => Theme.config() = theme
      }

    val update = new MenuItem:
      text <== Tr.toUpdate
      onAction = _ => new UpdateStage().show()

    val clearToken = new MenuItem:
      text <== Tr.clearToken
      visible <== GithubToken.property.isNotNull
      onAction = _ => GithubToken.clear(Some(MainApp.notifications))

    new Menu:
      text <== Tr.settings
      items = Seq(language, theme, update, clearToken)


  val value: MenuBar = new MenuBar:
    menus = Seq(addMenu, settingsMenu)
    alignmentInParent = Pos.TopCenter
