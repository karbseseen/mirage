package torrent

import atlantafx.base.theme.{Styles, Tweaks}
import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import fx.{AutoColumnBase, AutoSplitPane, AutoTableView, AutoTreeView}
import javafx.beans.binding.StringExpression
import org.kordamp.ikonli.fluentui.FluentUiRegularAL
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxIndexedCell2sfx, jfxObservableValue2sfx, jfxText2sfxText}
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.control.*
import scalafx.scene.layout.Priority

import java.math.RoundingMode
import scala.annotation.tailrec


class TorrentView extends AutoSplitPane:
  override def splitName: String = "torrent"

  val torrents = new TorrentTable
  val files = new TorrentFileTable

  vgrow = Priority.Always
  orientation = Orientation.Vertical
  items += torrents

  private var hasFiles = false
  private val selectedTorrent = torrents.selectionModel.flatMap(_.selectedItemProperty)
  files.root <== selectedTorrent.map(_.tree)
  selectedTorrent.onChange: (_,_,root) =>
    if (root == null && hasFiles)
      items -= files
      hasFiles = false
    else if (root != null && !hasFiles)
      items += files
      hasFiles = true


class TorrentTable extends AutoTableView[Torrent]:
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
    new Column(Tr.progress, _.progress) with ProgressColumn,
  )

  columns += new Column(Tr.size, _.size):
    cellInit(_.alignment = Pos.CenterRight)
    cellTextBind(size => sizeExpression(size.doubleValue, Tr.Size.allList))


class TorrentFileTable extends AutoTreeView[TorrentNode]:
  def tableName: String = "torrent-file"

  styleClass ++= Seq(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  showRoot = false

  columns += new Column(Tr.naming, selfProp):
    comparator = Ordering.by((_: TorrentNode).isInstanceOf[TorrentNode.File]).orElseBy(_.name)
    cellSet: (cell, value) =>
      val icon = value match
        case folder: TorrentNode.Folder => FluentUiRegularAL.FOLDER_20
        case file: TorrentNode.File => FluentUiRegularAL.DOCUMENT_20
      cell.graphic = FontIcon(icon)
      cell.text = value.name
    cellUnset: cell =>
      cell.graphic = null
      cell.text = null

  columns += new Column(Tr.progress, _.progress) with ProgressColumn

  columns += new Column(Tr.size, selfProp):
    cellInit(_.alignment = Pos.CenterRight)
    cellTextBind:
      case file: TorrentNode.File => sizeExpression(file.size.doubleValue, Tr.Size.allList)
      case folder: TorrentNode.Folder => folder.size.flatMap(size => sizeExpression(size.doubleValue, Tr.Size.allList))

/**********************************************************************************************************************/

private trait ProgressColumn:
  this: AutoColumnBase[Number] =>
  cellInit(_.alignment = Pos.CenterRight)
  cellText(progress => s"${(progress.floatValue * 100).toInt}%")

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
