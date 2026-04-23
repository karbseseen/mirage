package torrent

import atlantafx.base.theme.{Styles, Tweaks}
import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import fx.{AutoColumnBase, AutoSplitPane, AutoTableView, AutoTreeView}
import javafx.beans.binding.StringExpression
import org.kordamp.ikonli.fluentui.FluentUiRegularAL
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxIndexedCell2sfx, jfxObjectProperty2sfx, jfxText2sfxText}
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.control.*
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{HBox, Priority}

import java.math.RoundingMode
import scala.annotation.tailrec


object TorrentView extends AutoSplitPane:
  override def splitName: String = "torrent"

  vgrow = Priority.Always
  orientation = Orientation.Vertical
  items += TorrentTable

  private var hasFiles = false
  Torrent.selected <== TorrentTable.selectionModel.flatMap(_.selectedItemProperty).map(_.select)
  Torrent.selected.subscribe: selected =>
    val root = Option(selected).flatMap(_.node).map(_.tree).orNull
    TorrentFileTable.root = root
    if (root == null && hasFiles)
      items -= TorrentFileTable
      hasFiles = false
    else if (root != null && !hasFiles)
      items += TorrentFileTable
      hasFiles = true


object TorrentTable extends AutoTableView[Torrent]:
  def tableName: String = "torrent-root"

  styleClass ++= Seq(Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  items = Torrent.all

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


object TorrentFileTable extends AutoTreeView[TorrentNode]:
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
