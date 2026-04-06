package torrent

import atlantafx.base.theme.{Styles, Tweaks}
import constant.{Tr, Translate}
import fx.{AutoSplitPane, AutoTableView, AutoTreeView}
import org.kordamp.ikonli.fluentui.{FluentUiRegularAL, FluentUiRegularMZ}
import org.kordamp.ikonli.javafx.FontIcon
import org.libtorrent4j.TorrentStatus
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
    new Column(Tr.naming, selfProp) { cellTextSortable = _.name },
    new Column(Tr.download, _.downloadStr),
    new Column(Tr.upload, _.uploadStr),
    new Column(Tr.progress, _.progress) { cellText = progressText },
  )
  items = TorrentNode.Root.list

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

private def stateView(cell: Cell[TorrentStatus.State], value: TorrentStatus.State): Unit =
  import FluentUiRegularAL.*
  import FluentUiRegularMZ.*
  import TorrentStatus.State.*
  val iconTooltip = value match
    case CHECKING_FILES       => (DOCUMENT_SEARCH_20, Tr.checkingFiles)
    case CHECKING_RESUME_DATA => (DOCUMENT_SEARCH_20, Tr.checkingFiles)
    case DOWNLOADING_METADATA => (ARROW_SYNC_20,      Tr.downloadingMeta)
    case DOWNLOADING          => (ARROW_DOWNLOAD_20,  Tr.downloading)
    case FINISHED             => (CHECKMARK_20,       Tr.finished)
    case SEEDING              => (ARROW_UPLOAD_20,    Tr.seeding)
    case _                    => (QUESTION_20,        Tr.unknownState)

  cell.alignment = Pos.Center
  cell.graphic = new Button("", new FontIcon(iconTooltip._1)):
    styleClass += Styles.FLAT
  cell.tooltip = new Tooltip:
    text <== iconTooltip._2

private def progressText(progress: Number) = s"${(progress.floatValue * 100).toInt}%"
