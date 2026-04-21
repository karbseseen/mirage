package torrent

import atlantafx.base.theme.{Styles, Tweaks}
import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import fx.{AutoSplitPane, AutoTableView, AutoTreeView}
import javafx.beans.binding.StringExpression
import org.kordamp.ikonli.fluentui.FluentUiRegularAL
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxNode2sfx, jfxObservableValue2sfx}
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.control.*
import scalafx.scene.layout.Priority
import torrent.{State, Torrent, TorrentNode}

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

  columns ++= Seq(
    new Column(Tr.state, _.state) { cellFactory = stateView },
    new Column(Tr.naming, _.name) { comparator = Ordering[String] },
    new Column(Tr.download, _.downSpeed) { cellFactory = speedView },
    new Column(Tr.upload, _.upSpeed) { cellFactory = speedView },
    new Column(Tr.progress, _.progress) { cellText = progressText },
  )
  items = Torrent.all


class TorrentFileTable extends AutoTreeView[TorrentNode]:
  def tableName: String = "torrent-file"

  styleClass ++= Seq(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  showRoot = false

  columns ++= Seq(
    new Column(Tr.naming, selfProp) { cellFactory = nameView; comparator = nameOrdering },
    new Column(Tr.progress, _.progress) { cellText = progressText },
  )


private def nameView(cell: Cell[TorrentNode], value: TorrentNode): Unit =
  val icon = value match
    case folder: TorrentNode.Folder => FluentUiRegularAL.FOLDER_20
    case file: TorrentNode.File => FluentUiRegularAL.DOCUMENT_20
  cell.graphic = FontIcon(icon)
  cell.text = value.name

private def stateView(cell: Cell[State], value: State): Unit =
  cell.alignment = Pos.Center
  cell.graphic = FontIcon(value.icon)
  cell.tooltip = new Tooltip:
    text <== value.tooltip

private def speedView(cell: Cell[Number], value: Number): Unit =
  cell.alignment = Pos.CenterRight
  cell.text.unbind()
  cell.text <== speedExpression(value.doubleValue)

private def progressText(progress: Number) = s"${(progress.floatValue * 100).toInt}%"


private def nameOrdering = Ordering.by((_: TorrentNode).isInstanceOf[TorrentNode.File]).orElseBy(_.name)


@tailrec private def speedExpression(value: Double, units: List[Translate] = Tr.Speed.allList): StringExpression =
  def binding(scale: Int) =
    val valueStr = java.math.BigDecimal(value)
      .setScale(scale, RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
    b"$valueStr ${units.head}"

  if (value >= 1000 && units.tail.nonEmpty) speedExpression(value / 1024, units.tail)
  else if (value >= 100) binding(0)
  else if (value >= 10) binding(1)
  else binding(2)
