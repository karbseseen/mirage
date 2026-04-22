package torrent

import com.frostwire.jlibtorrent.TorrentInfo
import fx.SelfProperty
import javafx.beans.property.{ReadOnlyLongProperty, SimpleFloatProperty, SimpleLongProperty}
import javafx.beans.value.ChangeListener
import javafx.scene.control as jfxsc
import scalafx.Includes.jfxReadOnlyLongProperty2sfx
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem
import util.{sumMap, toIArray}


sealed abstract class TorrentNode(val name: String) extends SelfProperty:
  val progress = new SimpleFloatProperty(this, "progress")

object TorrentNode:

  class File private[torrent] (name: String, val index: Int, val size: Long) extends TorrentNode(name)

  class Folder private[torrent] (name: String, children: ObservableBuffer[jfxsc.TreeItem[TorrentNode]])
    extends TorrentNode(name):

    private val mutableSize = SimpleLongProperty(this, "size")
    def size: ReadOnlyLongProperty = mutableSize
    private val sizeListener: ChangeListener[Number] =
      (_, oldValue, newValue) => mutableSize.set(size.get + newValue.longValue - oldValue.longValue)
    children.onChange: (_, changes) =>
      val sizeDiff = changes.sumMap:
        case ObservableBuffer.Add(_, added) => added.map(_.getValue).sumMap:
          case file: File => file.size
          case folder: Folder => folder.size.addListener(sizeListener); folder.size.value
        case ObservableBuffer.Remove(_, removed) => -removed.map(_.getValue).sumMap:
          case file: File => file.size
          case folder: Folder => folder.size.removeListener(sizeListener); folder.size.value
        case _ => 0
      mutableSize.set(size.get + sizeDiff)


private[torrent] class FileInfo(tempInfo: TorrentInfo):
  import FileInfo.*

  private val infoFiles = tempInfo.files
  private val data: List[(PreChild, TorrentNode.File)] = List.tabulate(tempInfo.numFiles): index =>
    val it = infoFiles.filePath(index).split(java.io.File.separatorChar).reverseIterator
    if (!it.hasNext) sys.error("Empty split array iterator")
    val file = TorrentNode.File(it.next, index, infoFiles.fileSize(index))
    val preChild = it.foldLeft[PreChild](PreFile(file)) { case (child, prefix) => PreFolder(prefix, child) }
    (preChild, file)

  val treeChildren: List[TreeItem[TorrentNode]] = data.map(_._1).toTreeChildren
  val files: IArray[TorrentNode.File] = data.map(_._2).toIArray
  val totalSize: Long = infoFiles.totalSize

object FileInfo:
  private trait PreChild
  private case class PreFile(file: TorrentNode.File) extends PreChild
  private case class PreFolder(name: String, child: PreChild) extends PreChild
  extension (preChildren: List[PreChild]) private def toTreeChildren: List[TreeItem[TorrentNode]] =
    val (files, preFolders) = preChildren.partitionMap:
      case PreFile(file) => Left(TreeItem[TorrentNode](file))
      case folder: PreFolder => Right(folder)
    val folders = preFolders.groupMap(_.name)(_.child).map: (name, preChildren) =>
      val subTree = new TreeItem[TorrentNode]
      val children = preChildren.toTreeChildren
      subTree.value = TorrentNode.Folder(name, subTree.children)
      subTree.children = children
      subTree
    folders.toList.sortBy(_.value.name) ::: files.sortBy(_.value.name) 
