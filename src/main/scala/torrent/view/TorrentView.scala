package torrent.view

import atlantafx.base.theme.{Styles, Tweaks}
import com.frostwire.jlibtorrent.{TorrentFlags, TorrentStatus}
import com.sun.javafx.binding.MappedBinding
import constant.{Constants, Tr, Translate}
import core.main.MainApp
import fx.PropertyInterpolation.b
import fx.{AutoColumnBase, AutoSplitPane, AutoTableView, AutoTreeView}
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
import torrent.view.TorrentView.Selected
import torrent.{Hash, State, Torrent, TorrentMedia, TorrentNode}
import vlc.VlcStage

import java.lang
import java.math.RoundingMode
import scala.annotation.tailrec
import scala.collection.mutable


private val torrentTable = new AutoTableView[Torrent]:
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


private val selectedExpr =
  val selectedItem = torrentTable.selectionModel.flatMap(_.selectedItemProperty)
  MappedBinding(selectedItem, torrent =>
    if (!torrent.handle.isValid) null
    else Selected(torrent, Option(torrent.handle.torrentFile).map(_ => TorrentNode.Root(torrent.handle)))
  )


private val torrentFileTable = new AutoTreeView[TorrentNode]:
  def tableName: String = "torrent-file"

  styleClass ++= Seq(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  showRoot = false

  rowSet: (row, value) =>
    value match
      case file: TorrentNode.File => row.onMouseClicked = event =>
        if (event.getClickCount == 2)
          VlcStage(TorrentMedia(selectedExpr().torrent, file.index))
      case _ => row.onMouseClicked = null
  rowUnset(_.onMouseClicked = null)

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


val torrentView = new AutoSplitPane:
  override def splitName: String = "torrent"

  vgrow = Priority.Always
  orientation = Orientation.Vertical
  items += torrentTable

  private val expanded = mutable.Map.empty[Hash, Expanded]
  private var hasFiles = false
  selectedExpr.subscribe: (oldSelectedNullable, newSelectedNullable) =>
    val selected = Option(newSelectedNullable)

    for
      oldSelected <- Option(oldSelectedNullable)
      oldNode <- oldSelected.node
    do
      expanded(oldSelected.torrent.hash) = Expanded(oldNode.tree)
    for
      newSelected <- selected
      newNode <- newSelected.node
      expanded <- expanded.remove(newSelected.torrent.hash)
    do
      expanded.apply(newNode.tree)

    val root = selected.flatMap(_.node).map(_.tree).orNull
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
  private val selectedState = selectedExpr.flatMap(_.torrent.state)
  private val selectedInclude = selectedExpr.flatMap(_.node.map(_.tree.value().include).orNull)
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


private[torrent] object TorrentView:
  class Selected(val torrent: Torrent, val node: Option[TorrentNode.Root])
  @volatile private var _selected: Option[Selected] = None
  def selected: Option[Selected] = _selected
  def selectedExpr: ObjectBinding[Selected] = torrent.view.selectedExpr
  selectedExpr.addListener { (_, _, value) => _selected = Option(value) }


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
