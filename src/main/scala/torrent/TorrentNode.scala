package torrent

import com.frostwire.jlibtorrent.TorrentInfo
import fx.SelfProperty
import javafx.beans.property.{ReadOnlyLongProperty, SimpleFloatProperty, SimpleLongProperty}
import javafx.beans.value as jfxbv
import javafx.scene.control as jfxsc
import scalafx.Includes.{jfxLongProperty2sfx, jfxReadOnlyLongProperty2sfx}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem
import util.toIArray


sealed abstract class TorrentNode(val name: String) extends SelfProperty:
  val progress = new SimpleFloatProperty(this, "progress")


object TorrentNode:

  class File private[TorrentNode] (name: String, val index: Int, val size: Long) extends TorrentNode(name)


  class Folder private[TorrentNode] (name: String, children: ObservableBuffer[jfxsc.TreeItem[TorrentNode]])
    extends TorrentNode(name):
    import Folder.*

    private val mutableSize = SimpleLongProperty(this, "size")
    def size: ReadOnlyLongProperty = mutableSize
    private val sizeListener: jfxbv.ChangeListener[Number] =
      (_, oldValue, newValue) => mutableSize.set(size.get + newValue.longValue - oldValue.longValue)

    applyDiff { children.diffSum(nodeDiff(_, AddListener)) }
    children.onChange: (_, changes) =>
      val diff = changes.diffSum:
        case ObservableBuffer.Add(_, added) => added.diffSum(nodeDiff(_, AddListener))
        case ObservableBuffer.Remove(_, removed) => -removed.diffSum(nodeDiff(_, RemoveListener))
        case _ => emptyDiff
      applyDiff(diff)

    private def nodeDiff(node: jfxsc.TreeItem[TorrentNode], updateListener: UpdateListener): Diff = node.getValue match
      case file: File => Diff(file.size)
      case folder: Folder =>
        updateListener(folder.size, sizeListener)
        Diff(folder.size.value)

    private def applyDiff(diff: Diff): Unit =
      mutableSize.value = mutableSize.value + diff.size

  object Folder:
    private class Diff(val size: Long):
      def +(other: Diff) = Diff(size + other.size)
      def unary_- = Diff(-size)
    private val emptyDiff = Diff(0)
    extension [T](it: Iterable[T]) private def diffSum(map: T => Diff) =
      it.foldLeft(emptyDiff)((acc, item) => acc + map(item))

    private trait UpdateListener:
      def apply[T](observable: jfxbv.ObservableValue[T], listener: jfxbv.ChangeListener[T]): Unit
    private object AddListener extends UpdateListener:
      def apply[T](observable: jfxbv.ObservableValue[T], listener: jfxbv.ChangeListener[T]): Unit =
        observable.addListener(listener)
    private object RemoveListener extends UpdateListener:
      def apply[T](observable: jfxbv.ObservableValue[T], listener: jfxbv.ChangeListener[T]): Unit =
        observable.removeListener(listener)


  class Root private[torrent] (tempInfo: TorrentInfo):
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
