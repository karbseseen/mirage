package core

import atlantafx.base.theme as afxbt
import atlantafx.base.theme.Styles
import config.*
import config.Theme.given_Config_Theme
import constant.{Constants, Tr}
import core.main.MainApp
import fx.ModalBox
import scalafx.Includes.{jfxBooleanBinding2sfx, observableList2ObservableBuffer}
import scalafx.beans.BeanIncludes.jfxProperty2sfx
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.*
import scalafx.scene.layout.{GridPane, Priority, VBox}
import scalafx.scene.text.Font
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.stage.{DirectoryChooser, FileChooser}
import torrent.Torrent

import java.io.File
import java.nio.file.{Files, Paths}
import scala.collection.View


private def addMenu =
  lazy val torrentDialog =
    val header = new Label:
      vgrow = Priority.Always
      font = new Font(20)
      text <== Tr.addTorrent

    val magnetLabel = new Label:
      text <== Tr.magnetLink
    val magnetInput = new TextField:
      hgrow = Priority.Always

    val inFileLabel = new Label:
      text <== Tr.orTorrentFile
    val inFileInput = new TextField:
      hgrow = Priority.Always
    def chooseInFile = new FileChooser:
      title <== Tr.chooseTorrentFile
      extensionFilters += new ExtensionFilter(Tr.torrent.property.getValue, "*.torrent")
      extensionFilters += new ExtensionFilter(Tr.any.property.getValue, "*.*")
      List(inFileInput.text.value, Conf.torrentFile.value)
        .filter(_.nonEmpty)
        .map(File(_))
        .collectFirst:
          case file if file.isFile => file.getParentFile
          case file if file.isDirectory => file
        .foreach(initialDirectory = _)
      for file <- Option(showOpenDialog(MainApp.stage)) do inFileInput.text = file.getCanonicalPath
    val inFileButton = new Button("..."):
      onAction = _ => chooseInFile

    val outPathLabel = new Label:
      text <== Tr.whereToSave
    val outPathInput = new TextField:
      hgrow = Priority.Always
      Option(Conf.torrentSave.getValue).foreach(text = _)
      Conf.torrentSave <== text
    def chooseOutPath = new DirectoryChooser:
      title <== Tr.whereToSave
      Option(Conf.torrentSave.value).filter(_.nonEmpty).foreach(d => initialDirectory = new File(d))
      for path <- Option(showDialog(MainApp.stage)) do outPathInput.text = path.getCanonicalPath
    val outPathButton = new Button("..."):
      onAction = _ => chooseOutPath

    magnetInput.disable <== inFileInput.text.isNotEmpty
    inFileInput.disable <== magnetInput.text.isNotEmpty
    inFileButton.disable <== magnetInput.text.isNotEmpty

    val button = new Button:
      disable <== magnetInput.text.isEmpty && inFileInput.text.isEmpty || outPathInput.text.isEmpty
      styleClass += Styles.ACCENT
      text <== Tr.add
      alignmentInParent = Pos.Center
      onAction = _ =>
        val magnetLink = magnetInput.text.value
        val torrentFile = inFileInput.text.value
        val saveDir = new File(outPathInput.text.value)

        if (magnetLink.nonEmpty)
          Torrent.add(magnetLink, saveDir)
        else if (torrentFile.nonEmpty)
          Torrent.add(Files.readAllBytes(Paths.get(torrentFile)), saveDir)
          Conf.torrentFile.value = new File(torrentFile).getParentFile.getCanonicalPath

        MainApp.modal.hide(true)

    val grid = new GridPane(Constants.inset, Constants.inset):
      vgrow = Priority.Always
      alignment = Pos.Center
      add(magnetLabel,      0, 0)
      add(magnetInput,      1, 0)

      add(inFileLabel,      0, 1)
      add(inFileInput,      1, 1)
      add(inFileButton,     2, 1)

      add(ModalBox.spacing, 0, 2)

      add(outPathLabel,     0, 3)
      add(outPathInput,     1, 3)
      add(outPathButton,    2, 3)

      add(ModalBox.spacing, 0, 4)

      add(button,           0, 5, 3, 1)

    button.prefWidth <== grid.width / 2

    new VBox(header, ModalBox.space, grid) with ModalBox:
      padding = Insets(Constants.modalPadding)
      alignment = Pos.Center

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
