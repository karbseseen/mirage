package torrent.view

import atlantafx.base.theme.{Styles, Tweaks}
import com.frostwire.jlibtorrent.{TorrentFlags, TorrentStatus}
import constant.{Constants, Tr, Translate}
import core.main.MainApp
import fx.PropertyInterpolation.b
import fx.{AutoColumnBase, AutoSplitPane, AutoTableView, AutoTreeView}
import javafx.beans.InvalidationListener
import javafx.beans.binding.StringExpression
import org.kordamp.ikonli.fluentui.FluentUiRegularAL
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxIndexedCell2sfx, jfxObjectProperty2sfx, jfxObservableValue2sfx, jfxScene2sfx, jfxText2sfxText}
import scalafx.beans.binding.Bindings
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.control.*
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{HBox, Priority}
import torrent.{Torrent, TorrentNode}

import java.lang
import java.math.RoundingMode
import scala.annotation.tailrec


val torrentTable = new AutoTableView[Torrent]:
  def tableName: String = "torrent-root"

  styleClass ++= Seq(Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  items = Torrent.all

  rowSet: (row, torrent) =>
    val notNew = torrent.state.map(!_.isNew: lang.Boolean)
    val playPause = new MenuItem:
      text <== torrent.state.flatMap(state => if (state.paused) Tr.resume else Tr.pause)
      visible <== notNew
      onAction = _ =>
        if (torrent.state().paused)
          torrent.handle.resume()
          torrent.handle.setFlags(TorrentFlags.AUTO_MANAGED)
        else
          torrent.handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
          torrent.handle.pause()
    val delete = new MenuItem:
      text <== Tr.delete
      onAction = _ => MainApp.modal.show(TorrentModal.delete(torrent))
    val recheckFiles = new MenuItem:
      text <== Tr.recheckFiles
      visible.bind(torrent.state.map(state =>
        !state.isNew &&
        !state.paused &&
        state.value != TorrentStatus.State.CHECKING_RESUME_DATA &&
        state.value != TorrentStatus.State.CHECKING_FILES
      ))
      onAction = _ => torrent.handle.forceRecheck()
    val reannounce = new MenuItem:
      text <== Tr.reannounce
      visible <== notNew
      onAction = _ => torrent.handle.forceReannounce()
    row.contextMenu = ContextMenu(playPause, delete, recheckFiles, reannounce)
  rowUnset: row =>
    row.contextMenu = null

  columns += new Column(Tr.state, _.state):
    cellInit(_.alignment = Pos.Center)
    cellSet: (cell, value) =>
      cell.graphic = FontIcon(value.icon)
      cell.tooltip = new Tooltip:
        text <== value.tooltip
    cellUnset: cell =>
      cell.graphic = null
      cell.tooltip = null

  columns ++= Seq(
    new Column(Tr.naming, _.name) { comparator = Ordering[String] },
    new Column(Tr.download, _.downSpeed) with SpeedColumn,
    new Column(Tr.upload, _.upSpeed) with SpeedColumn,
  )

  columns += new Column(Tr.progress, _.progress):
    cellInit(_.alignment = Pos.CenterRight)
    cellText(progress => s"${(progress.floatValue * 100).toInt}%")

  columns += new Column(Tr.size, _.size):
    cellInit(_.alignment = Pos.CenterRight)
    cellTextBind(size => sizeExpression(size.doubleValue, Tr.Size.allList))


val torrentFileTable = new AutoTreeView[TorrentNode]:
  def tableName: String = "torrent-file"

  styleClass ++= Seq(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  showRoot = false

  columns += new Column(Tr.naming, selfProp):
    treeColumn = this
    comparator = Ordering.by((_: TorrentNode).isInstanceOf[TorrentNode.File]).orElseBy(_.name)
    cellSet: (cell, value) =>
      val include = new CheckBox:
        selected <== value.include.isEqualTo(TorrentNode.Include.Yes)
        indeterminate <== value.include.isEqualTo(TorrentNode.Include.Part)
        this.addEventFilter(MouseEvent.MousePressed, _.consume())
        onMouseClicked = _ => value.toggleInclude()
      val icon = value match
        case folder: TorrentNode.Folder => FluentUiRegularAL.FOLDER_20
        case file: TorrentNode.File => FluentUiRegularAL.DOCUMENT_20
      cell.graphic = HBox(include, FontIcon(icon))
      cell.text = value.name
    cellUnset: cell =>
      cell.graphic = null
      cell.text = null

  columns += new Column(Tr.progress, _.progress):
    cellInit(_.alignment = Pos.CenterRight)
    cellSet: (cell, value) =>
      val size = cell.getTableRow.getTreeItem.getValue match
        case file: TorrentNode.File => file.size
        case folder: TorrentNode.Folder => folder.size.get
      cell.text = s"${value.longValue * 100 / size}%"
    cellUnset: cell =>
      cell.text = null

  columns += new Column(Tr.size, selfProp):
    cellInit(_.alignment = Pos.CenterRight)
    cellTextBind:
      case file: TorrentNode.File => sizeExpression(file.size.doubleValue, Tr.Size.allList)
      case folder: TorrentNode.Folder => folder.size.flatMap(size => sizeExpression(size.doubleValue, Tr.Size.allList))


val torrentView = new AutoSplitPane:
  override def splitName: String = "torrent"

  vgrow = Priority.Always
  orientation = Orientation.Vertical
  items += torrentTable

  private var hasFiles = false
  Torrent.selected <== torrentTable.selectionModel.flatMap(_.selectedItemProperty).map(_.select)
  Torrent.selected.subscribe: selected =>
    val root = Option(selected).flatMap(_.node).map(_.tree).orNull
    torrentFileTable.root = root
    if (root == null && hasFiles)
      items -= torrentFileTable
      hasFiles = false
    else if (root != null && !hasFiles)
      items += torrentFileTable
      hasFiles = true

  private def createStartButton(torrent: Torrent) = new Button:
    styleClass += Styles.ACCENT
    alignmentInParent = Pos.TopLeft
    translateX = Constants.inset
    translateY <== Bindings.createDoubleBinding(
      () => torrentTable.localToScene(0.0, torrentTable.getHeight).y - this.getHeight - Constants.inset,
      torrentTable.localToSceneTransformProperty,
      torrentTable.height,
      this.height,
    )
    text <== b"${Tr.letsGo}!"
    onAction = _ =>
      torrent.state() = torrent.state().copy(isNew = false)
      torrent.handle.resume()
      torrent.handle.setFlags(TorrentFlags.AUTO_MANAGED)
  private var startButton: Option[Button] = None
  private val selectedState = Torrent.selected.flatMap(_.torrent.state)
  private val selectedInclude = Torrent.selected.flatMap(_.node.map(_.tree.value().include).orNull)
  private val selectedListener: InvalidationListener = _ =>
    val canStart = Option(selectedState()).exists(_.isFileSelect) &&
      !Option(selectedInclude()).contains(TorrentNode.Include.No)
    if (canStart && startButton.isEmpty)
      val startButton = createStartButton(Torrent.selected().torrent)
      MainApp.stage.scene().getChildren += startButton
      this.startButton = Some(startButton)
    else if (!canStart)
      for startButton <- startButton do
        MainApp.stage.scene().getChildren -= startButton
        this.startButton = None
  selectedState.addListener(selectedListener)
  selectedInclude.addListener(selectedListener)

/**********************************************************************************************************************/

private trait SpeedColumn:
  this: AutoColumnBase[Number] =>
  cellInit(_.alignment = Pos.CenterRight)
  cellTextBind(num => sizeExpression(num.doubleValue, Tr.Speed.allList))

@tailrec private def sizeExpression(value: Double, units: List[Translate]): StringExpression =
  def binding(scale: Int) =
    val valueStr = java.math.BigDecimal(value)
      .setScale(scale, RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
    b"$valueStr ${units.head}"

  if (value >= 1000 && units.tail.nonEmpty) sizeExpression(value / 1024, units.tail)
  else if (value >= 100) binding(0)
  else if (value >= 10) binding(1)
  else binding(2)
