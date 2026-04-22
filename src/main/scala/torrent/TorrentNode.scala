package torrent

import com.frostwire.jlibtorrent.TorrentInfo
import fx.SelfProperty
import javafx.beans.property.SimpleFloatProperty
import scalafx.scene.control.TreeItem
import util.{also, toIArray}


sealed abstract class TorrentNode(val name: String) extends SelfProperty:
  val progress = new SimpleFloatProperty(this, "progress")

object TorrentNode:
  class Folder private[torrent](name: String, children: List[TorrentNode]) extends TorrentNode(name)
  class File private[torrent](name: String, val index: Int) extends TorrentNode(name)


private[torrent] class FileInfo(info: TorrentInfo):
  import FileInfo.*
  private val data: List[(PreChild, TorrentNode.File)] = List.tabulate(info.numFiles): index =>
    val it = info.files.filePath(index).split(java.io.File.separatorChar).reverseIterator
    if (!it.hasNext) sys.error("Empty split array iterator")
    val file = TorrentNode.File(it.next, index)
    val preChild = it.foldLeft[PreChild](PreFile(file)) { case (child, prefix) => PreFolder(prefix, child) }
    (preChild, file)
  val treeChildren: List[TreeItem[TorrentNode]] = data.map(_._1).toTreeChildren
  val files: IArray[TorrentNode.File] = data.map(_._2).toIArray

object FileInfo:
  private trait PreChild
  private case class PreFile(file: TorrentNode.File) extends PreChild
  private case class PreFolder(name: String, child: PreChild) extends PreChild
  extension (preChildren: List[PreChild]) private def toTreeChildren: List[TreeItem[TorrentNode]] =
    val (files, preFolders) = preChildren.partitionMap:
      case PreFile(file) => Left(TreeItem[TorrentNode](file))
      case folder: PreFolder => Right(folder)
    val folders = preFolders.groupMap(_.name)(_.child).map: (name, preChildren) =>
      val children = preChildren.toTreeChildren
      val folder = TorrentNode.Folder(name, children.map(_.getValue))
      TreeItem[TorrentNode](folder).also(_.children = children)
    folders.toList.sortBy(_.value.name) ::: files.sortBy(_.value.name) 
