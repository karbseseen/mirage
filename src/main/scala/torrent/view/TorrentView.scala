package torrent.view

import atlantafx.base.theme.{Styles, Tweaks}
import com.frostwire.jlibtorrent.{TorrentFlags, TorrentStatus}
import com.sun.javafx.binding.MappedBinding
import constant.{Constants, Tr, Translate}
import core.main.MainApp
import fx.PropertyInterpolation.b
import fx.{AutoColumnBase, AutoSplitPane, AutoTableView, AutoTreeView, mapBinding}
import javafx.beans.InvalidationListener
import javafx.beans.binding.{ObjectBinding, StringExpression}
import javafx.beans.property.SimpleLongProperty
import org.kordamp.ikonli.fluentui.FluentUiRegularAL
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxIndexedCell2sfx, jfxObjectProperty2sfx, jfxObservableValue2sfx, jfxScene2sfx, jfxText2sfxText, jfxTreeItem2sfx}
import scalafx.beans.binding.Bindings
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.control.*
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{HBox, Priority}
import torrent.{Hash, State, Torrent, TorrentMedia, TorrentNode}
import vlc.VlcStage

import java.lang
import java.math.RoundingMode
import scala.annotation.tailrec
import scala.collection.mutable


private object TorrentTable extends AutoTableView[Torrent]:
  def tableName: String = "torrent-root"

  styleClass ++= Seq(Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  items = Torrent.all

  placeholder = new Label:
    text <== Tr.clickAddButton

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

    Option(torrent.handle.torrentFile).filter(_.numFiles == 1) match
      case Some(info) => row.onMouseClicked = event =>
        if (event.getClickCount == 2)
          VlcStage.play(Some(TorrentMedia(torrent, 0)))
      case _ => row.onMouseClicked = null

  rowUnset: row =>
    row.contextMenu = null
    row.onMouseClicked = null

  columns += new Column(Tr.state, _.state):
    comparator = Ordering.by(!(_: State).isNew).orElseBy(_.value.ordinal).orElseBy(!_.paused)
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
    new Column(Tr.progress, _.progress) with ProgressColumn,
  )

  columns += new Column(Tr.size, _.size):
    comparator = Ordering.by(_.longValue)
    cellInit(_.alignment = Pos.CenterRight)
    cellTextBind(size => sizeExpression(size.doubleValue, Tr.Size.allList))

  columns += new Column(Tr.peers, _.peerNum):
    comparator = Ordering.by(_.intValue)
    cellInit(_.alignment = Pos.CenterRight)
    cellText(_.intValue.toString)


private object TorrentFileTable
private class TorrentFileTable(torrent: Torrent, val node: TorrentNode.Root) extends AutoTreeView[TorrentNode]:
  def tableName: String = "torrent-file"

  styleClass ++= Seq(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  root = node.folder.tree
  showRoot = false
  userData = TorrentFileTable

  private def rowUnset(row: IndexedCell[TorrentNode]): Unit =
    row.onMouseClicked = null
    row.contextMenu = null
  rowUnset(rowUnset(_))
  rowSet:
    case (row, file: TorrentNode.File) => row.onMouseClicked = event =>
      if (event.getClickCount == 2)
        VlcStage.play(Some(TorrentMedia(torrent, file.index)))
      val rename = new MenuItem:
        text <== Tr.rename
        onAction = _ => MainApp.modal.show(TorrentModal.renameFile(torrent, file.index))
      row.contextMenu = ContextMenu(rename)
    case (row, _) => rowUnset(row)

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

  columns += new Column(Tr.progress, _.progress) with ProgressColumn

  columns += new Column(
    Tr.size,
    _ match
      case file: TorrentNode.File => SimpleLongProperty(file.size)
      case folder: TorrentNode.Folder => folder.size
  ):
    comparator = Ordering.by(_.longValue)
    cellInit(_.alignment = Pos.CenterRight)
    cellTextBind(value => sizeExpression(value.doubleValue, Tr.Size.allList))


object TorrentView extends AutoSplitPane:

  private[torrent] class Selected(val torrent: Torrent, _node: Option[TorrentNode.Root]):
    val fileTable: Option[TorrentFileTable] = _node.map(TorrentFileTable(torrent, _))
    def node: Option[TorrentNode.Root] = fileTable.map(_.node)
  private[torrent] def selected: Option[Selected] = Option(selectedExpr())
  private[torrent] val selectedExpr: ObjectBinding[Selected] = TorrentTable.selectionModel
    .flatMap(_.selectedItemProperty)
    .mapBinding: torrent =>
      if (!torrent.handle.isValid) null
      else Selected(torrent, Option(torrent.handle.torrentFile).map(_ => TorrentNode.Root(torrent.handle)))

  override def splitName: String = "torrent"

  vgrow = Priority.Always
  orientation = Orientation.Vertical
  items += TorrentTable

  private val expanded = mutable.Map.empty[Hash, Expanded]

  selectedExpr.subscribe: (oldSelectedNullable, newSelectedNullable) =>
    val selected = Option(newSelectedNullable)

    for
      oldSelected <- Option(oldSelectedNullable)
      oldNode <- oldSelected.node
    do
      expanded(oldSelected.torrent.hash) = Expanded(oldNode.folder.tree)

    for
      newSelected <- selected
      newNode <- newSelected.node
      expanded <- expanded.remove(newSelected.torrent.hash)
    do
      expanded.apply(newNode.folder.tree)

    items.removeIf(_.getUserData == TorrentFileTable)
    selected.flatMap(_.fileTable).foreach(items += _)

  private def createStartButton(torrent: Torrent) = new Button:
    styleClass += Styles.ACCENT
    alignmentInParent = Pos.TopLeft
    translateX = Constants.inset
    translateY <== Bindings.createDoubleBinding(
      () => TorrentTable.localToScene(0.0, TorrentTable.getHeight).y - this.getHeight - Constants.inset,
      TorrentTable.localToSceneTransformProperty,
      TorrentTable.height,
      this.height,
    )
    text <== b"${Tr.letsGo}!"
    onAction = _ =>
      torrent.state() = torrent.state().copy(isNew = false)
      torrent.handle.resume()
      torrent.handle.setFlags(TorrentFlags.AUTO_MANAGED)
  private var startButton: Option[Button] = None
  private val selectedState = selectedExpr.flatMap(_.torrent.state)
  private val selectedInclude = selectedExpr.flatMap(_.node.map(_.folder.include).orNull)
  private val selectedListener: InvalidationListener = _ =>
    val canStart = Option(selectedState()).exists(_.isFileSelect) &&
      !Option(selectedInclude()).contains(TorrentNode.Include.No)
    if (canStart && startButton.isEmpty)
      val startButton = createStartButton(selectedExpr().torrent)
      MainApp.stage.scene().getChildren += startButton
      this.startButton = Some(startButton)
    else if (!canStart)
      for startButton <- startButton do
        MainApp.stage.scene().getChildren -= startButton
        this.startButton = None
  selectedState.addListener(selectedListener)
  selectedInclude.addListener(selectedListener)


private trait SpeedColumn:
  this: AutoColumnBase[Number] =>
  comparator = Ordering.by(_.intValue)
  cellInit(_.alignment = Pos.CenterRight)
  cellTextBind(num => sizeExpression(num.doubleValue, Tr.Speed.allList))

private trait ProgressColumn:
  this: AutoColumnBase[Number] =>
  comparator = Ordering.by(_.floatValue)
  cellInit(_.alignment = Pos.CenterRight)
  cellText(progress => s"${(progress.floatValue * 100).toInt}%")

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

class Expanded(tree: TreeItem[TorrentNode]):
  private val namedChildren =
    for
      treeChild <- tree.children.toList
      folderChild <- Some(treeChild.value()).collect { case folder: TorrentNode.Folder => folder }
    yield
      folderChild.name -> Expanded(treeChild)
  val children: Map[String, Expanded] = namedChildren.toMap
  val value: Boolean = tree.expanded()

  def apply(tree: TreeItem[TorrentNode]): Unit =
    Some(tree.value()).collect:
      case folder: TorrentNode.Folder =>
        tree.expanded = value
        for
          treeChild <- tree.children
          expandedChild <- children.get(treeChild.value().name)
        do
          expandedChild.apply(treeChild)
