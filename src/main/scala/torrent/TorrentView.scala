package torrent

import atlantafx.base.theme.{Styles, Tweaks}
import com.frostwire.jlibtorrent.TorrentStatus
import constant.{Tr, Translate}
import fx.{AutoSplitPane, AutoTableView, AutoTreeView}
import org.kordamp.ikonli.fluentui.{FluentUiRegularAL, FluentUiRegularMZ}
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.{jfxNode2sfx, jfxObservableValue2sfx}
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.control.*
import scalafx.scene.layout.Priority


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


class TorrentTable extends AutoTableView[TorrentNode.Root]:
  def tableName: String = "torrent-root"

  styleClass ++= Seq(Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED

  columns ++= Seq(
    new Column(Tr.state, _.state) { cellFactory = stateView },
    new Column(Tr.naming, _.name) { comparator = Ordering[String] },
    new Column(Tr.download, _.downSpeedStr),
    new Column(Tr.upload, _.upSpeedStr),
    new Column(Tr.progress, _.progress) { cellText = progressText },
  )
  items = TorrentNode.roots

class TorrentFileTable extends AutoTreeView[TorrentNode]:
  def tableName: String = "torrent-file"

  styleClass ++= Seq(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE)
  styleClass -= Styles.BORDERED
  showRoot = false

  private val typeOrdering = Ordering.by((_: TorrentNode).isInstanceOf[TorrentNode.File])
  columns ++= Seq(
    new Column(0, selfProp) { cellFactory = fileView; comparator = typeOrdering },
    new Column(Tr.state, _.state) { cellFactory = stateView },
    new Column(Tr.naming, selfProp) { cellTextSortable = _.name },
    new Column(Tr.progress, _.progress) { cellText = progressText },
  )


private def fileView(cell: Cell[TorrentNode], value: TorrentNode): Unit =
  val icon = value match
    case folder: TorrentNode.Folder => FluentUiRegularAL.FOLDER_20
    case file: TorrentNode.File => FluentUiRegularAL.DOCUMENT_20
  cell.graphic = new FontIcon(icon)

private def stateView(cell: Cell[State], value: State): Unit =
  cell.alignment = Pos.Center
  cell.graphic = new Button("", new FontIcon(value.icon)):
    styleClass += Styles.FLAT
  cell.tooltip = new Tooltip:
    text <== value.tooltip

private def progressText(progress: Number) = s"${(progress.floatValue * 100).toInt}%"
