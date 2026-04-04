package core

import atlantafx.base.theme as afxbt
import config.Theme.given_Config_Theme
import config.{Config, Language, Theme}
import constant.{Constants, Tr}
import core.main.MainApp
import fx.{AutoInsets, ModalBox}
import scalafx.beans.BeanIncludes.jfxProperty2sfx
import scalafx.geometry.Pos
import scalafx.scene.control.*
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafx.scene.text.Font


private def addMenu =
  lazy val torrentDialog =
    val header = new Label:
      vgrow = Priority.Always
      font = new Font(20)
      text <== Tr.addTorrent

    val input = new TextField:
      hgrow = Priority.Always
      focusTraversable = false
      promptText <== Tr.enterMagnet

    val button = new Button:
      disable <== input.text.isEmpty
      text <== Tr.add
      onAction = _ => MainApp.modal.hide(true)

    val row = new HBox(Constants.inset, input, button):
      vgrow = Priority.Always
      alignment = Pos.Center

    new VBox(header, ModalBox.space, row, ModalBox.space) with ModalBox:
      padding = AutoInsets(left = Constants.modalPadding, top = Constants.modalPadding, right = Constants.modalPadding)

  val torrent = new MenuItem:
    text <== Tr.torrent
    onAction = _ => MainApp.modal.show(torrentDialog)

  new Menu:
    text <== Tr.add
    items = Seq(torrent)


private def settingsMenu =
  val language = new Menu:
    private val group = new ToggleGroup
    text <== Tr.language
    items = Language.values.map { language =>
      new RadioMenuItem(language.name):
        toggleGroup = group
        if (language == Config[Language].value) selected = true
        onAction = _ => Config[Language].value = language
    }

  val theme = new Menu:
    private val group = new ToggleGroup
    text <== Tr.theme
    items = Theme.map.values.toList.sortBy(_.getName).map { theme =>
      new RadioMenuItem(theme.getName):
        toggleGroup = group
        if (theme == Config[afxbt.Theme].value) selected = true
        onAction = _ => Config[afxbt.Theme].setValue(theme)
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


private[core] class MainMenu extends MenuBar:
  menus = Seq(addMenu, settingsMenu)
  alignmentInParent = Pos.TopCenter
