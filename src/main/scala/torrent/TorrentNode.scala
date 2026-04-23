package torrent

import com.frostwire.jlibtorrent.TorrentInfo
import fx.SelfProperty
import javafx.beans.property.{ReadOnlyLongProperty, SimpleLongProperty}
import javafx.beans.value as jfxbv
import javafx.scene.control as jfxsc
import scalafx.Includes.{jfxLongProperty2sfx, jfxReadOnlyLongProperty2sfx}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem
import util.{also, toIArray}


sealed abstract class TorrentNode(val name: String) extends SelfProperty:
  protected object mutableProgress extends SimpleLongProperty(this, "progress"):
    override def fireValueChangedEvent(): Unit = super.fireValueChangedEvent()
  def progress: ReadOnlyLongProperty = mutableProgress


object TorrentNode:

  class File private[TorrentNode] (name: String, val index: Int, val size: Long) extends TorrentNode(name):
    override def progress: SimpleLongProperty = mutableProgress


  class Folder private[TorrentNode] (name: String, children: ObservableBuffer[jfxsc.TreeItem[TorrentNode]])
    extends TorrentNode(name):
    import Folder.*

    private val progressListener: jfxbv.ChangeListener[Number] =
      (_, oldValue, newValue) => mutableProgress() = mutableProgress() + newValue.longValue - oldValue.longValue

    private val mutableSize = SimpleLongProperty(this, "size")
    def size: ReadOnlyLongProperty = mutableSize
    private val sizeListener: jfxbv.ChangeListener[Number] =
      (_, oldValue, newValue) => mutableSize() = mutableSize() + newValue.longValue - oldValue.longValue

    applyDiff { children.diffSum(nodeDiff(_, AddListener)) }
    children.onChange: (_, changes) =>
      val diff = changes.diffSum:
        case ObservableBuffer.Add(_, added) => added.diffSum(nodeDiff(_, AddListener))
        case ObservableBuffer.Remove(_, removed) => -removed.diffSum(nodeDiff(_, RemoveListener))
        case _ => emptyDiff
      applyDiff(diff)

    private def nodeDiff(node: jfxsc.TreeItem[TorrentNode], updateListener: UpdateListener): Diff =
      updateListener(node.getValue.progress, progressListener)
      node.getValue match
        case file: File =>
          Diff(file.progress(), file.size)
        case folder: Folder =>
          updateListener(folder.size, sizeListener)
          Diff(folder.progress(), folder.size())

    private def applyDiff(diff: Diff): Unit =
      if (diff.progress == 0) mutableProgress.fireValueChangedEvent()
      else mutableProgress() = mutableProgress() + diff.progress
      mutableSize() = mutableSize() + diff.size

  object Folder:
    private class Diff(val progress: Long, val size: Long):
      def +(other: Diff) = Diff(progress + other.progress, size + other.size)
      def unary_- = Diff(-progress, -size)
    private val emptyDiff = Diff(0, 0)
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
  
    val tree: TreeItem[TorrentNode] = data.map(_._1).toTree
    val files: IArray[TorrentNode.File] = data.map(_._2).toIArray


  private trait PreChild
  private case class PreFile(file: TorrentNode.File) extends PreChild
  private case class PreFolder(name: String, child: PreChild) extends PreChild
  extension (preChildren: List[PreChild]) private def toTree: TreeItem[TorrentNode] =
    val (files, preFolders) = preChildren.partitionMap:
      case PreFile(file) => Left(TreeItem[TorrentNode](file))
      case folder: PreFolder => Right(folder)
    val folders = preFolders.groupMap(_.name)(_.child).map: (name, preChildren) =>
      val subTree = preChildren.toTree
      subTree.value = TorrentNode.Folder(name, subTree.children)
      subTree
    val children = folders.toList.sortBy(_.value.name) ::: files.sortBy(_.value.name)
    new TreeItem[TorrentNode].also(_.children = children)
