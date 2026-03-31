import MainApp.stage
import atlantafx.base.controls.ModalPane
import atlantafx.base.theme as afxbt
import config.Theme.given_Config_Theme
import config.{Config, Language, Theme}
import constant.{Constants, Tr}
import fx.NotificationBox
import scalafx.beans.BeanIncludes.jfxProperty2sfx
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.*
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafx.scene.text.Font


private def addMenu(using modal: ModalPane) =
  lazy val torrentDialog =
    val header = new Label:
      font = new Font(20)
      text <== Tr.addTorrent

    val input = new TextField:
      hgrow = Priority.Always
      focusTraversable = false
      promptText <== Tr.enterMagnet

    val button = new Button:
      disable <== input.text.isEmpty
      text <== Tr.add
      onAction = _ => modal.hide(true)

    val row = new HBox(Constants.inset, input, button):
      vgrow = Priority.Always
      alignment = Pos.Center

    new VBox(Constants.inset, header, row):
      maxWidth = 500
      maxHeight = 150
      style = "-fx-background-color: -color-bg-default"
      margin = Insets(Constants.inset)
      padding = Insets(Constants.inset * 1.5)

  val torrent = new MenuItem:
    text <== Tr.torrent
    onAction = _ => modal.show(torrentDialog)

  new Menu:
    text <== Tr.add
    items = Seq(torrent)


private def settingsMenu(using notifications: NotificationBox) =
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
    onAction = _ => new UpdateStage(stage).show()

  val clearToken = new MenuItem:
    text <== Tr.clearToken
    visible <== GithubToken.property.isNotNull
    onAction = _ => GithubToken.clear(Some(notifications))

  new Menu:
    text <== Tr.settings
    items = Seq(language, theme, update, clearToken)


class MainMenu(using ModalPane, NotificationBox) extends MenuBar:
  menus = Seq(addMenu, settingsMenu)
  alignmentInParent = Pos.TopCenter
