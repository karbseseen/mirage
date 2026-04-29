package torrent.view

import atlantafx.base.theme.Styles
import com.frostwire.jlibtorrent.SessionHandle
import config.Conf
import constant.{Constants, Tr}
import core.main.MainApp
import fx.PropertyInterpolation.b
import fx.{ModalBox, ModalVBox}
import scalafx.Includes.{jfxBooleanBinding2sfx, jfxStringProperty2sfx, observableList2ObservableBuffer}
import scalafx.geometry.Pos
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout.{GridPane, HBox, Priority, VBox}
import scalafx.scene.text.Font
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.stage.{DirectoryChooser, FileChooser}
import torrent.Torrent

import java.io.File
import java.nio.file.Paths


object TorrentModal:
  
  def add: ModalBox =
    val header = new Label:
      vgrow = Priority.Always
      font = new Font(Constants.headingSize)
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
          Torrent.add(Paths.get(torrentFile), saveDir)
          Conf.torrentFile.value = new File(torrentFile).getParentFile.getCanonicalPath

        MainApp.modal.hide(true)

    val grid = new GridPane(Constants.inset, Constants.inset):
      vgrow = Priority.Always
      alignment = Pos.Center
      this.add(magnetLabel,      0, 0)
      this.add(magnetInput,      1, 0)

      this.add(inFileLabel,      0, 1)
      this.add(inFileInput,      1, 1)
      this.add(inFileButton,     2, 1)

      this.add(ModalBox.spacing, 0, 2)

      this.add(outPathLabel,     0, 3)
      this.add(outPathInput,     1, 3)
      this.add(outPathButton,    2, 3)

      this.add(ModalBox.spacing, 0, 4)

      this.add(button,           0, 5, 3, 1)

    button.prefWidth <== grid.width / 2

    new VBox(header, ModalBox.space, grid) with ModalVBox:
      alignment = Pos.Center
  
  
  def delete(torrent: Torrent): ModalBox =

    class DeleteButton extends Button:
      hgrow = Priority.Always
      maxWidth = Double.MaxValue

    val nope = new DeleteButton:
      text <== Tr.nope
      onAction = _ =>
        MainApp.modal.hide()
    val woFiles = new DeleteButton:
      styleClass += Styles.DANGER
      text <== Tr.yepWithoutFiles
      onAction = _ =>
        Torrent.session.remove(torrent.handle)
        MainApp.modal.hide()
    val wFiles = new DeleteButton:
      styleClass += Styles.DANGER
      text <== Tr.yepWithFiles
      onAction = _ =>
        Torrent.session.remove(torrent.handle, SessionHandle.DELETE_FILES)
        MainApp.modal.hide()

    val label = new Label:
      text <== b"${Tr.reallyDelete} ${torrent.name} ?"
      wrapText = true
      font = Font(Constants.headingSize)
    val buttons = HBox(Constants.inset, nope, woFiles, wFiles)

    new VBox(label, ModalBox.space, buttons) with ModalVBox:
      alignment = Pos.Center
