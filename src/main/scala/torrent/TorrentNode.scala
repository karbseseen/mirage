package torrent

import com.frostwire.jlibtorrent.{Priority, TorrentInfo}
import fx.SelfProperty
import javafx.beans.property.*
import javafx.beans.value as jfxbv
import javafx.scene.control as jfxsc
import scalafx.Includes.{jfxIntegerProperty2sfx, jfxLongProperty2sfx, jfxObjectProperty2sfx, jfxReadOnlyLongProperty2sfx, jfxReadOnlyObjectProperty2sfx}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem
import torrent.TorrentNode.FolderInclude
import util.{also, toIArray}

import java.lang


sealed abstract class TorrentNode(val name: String) extends SelfProperty:
  def include: ReadOnlyObjectProperty[? <: FolderInclude]
  def toggleInclude(): Unit

  protected object mutableProgress extends SimpleLongProperty(this, "progress"):
    override def fireValueChangedEvent(): Unit = super.fireValueChangedEvent()
  def progress: ReadOnlyLongProperty = mutableProgress


object TorrentNode:

  class File private[TorrentNode] (name: String, val index: Int, val size: Long) extends TorrentNode(name):
    val include: SimpleObjectProperty[FileInclude] = SimpleObjectProperty(this, "include", Include.No)
    def toggleInclude(): Unit =
      val priority = if (include() == Include.Yes) Priority.IGNORE else Priority.NORMAL
      Option(Torrent.selected()).foreach(_.torrent.handle.filePriority(index, priority))

    override def progress: SimpleLongProperty = mutableProgress


  class Folder private[TorrentNode] (name: String, children: ObservableBuffer[jfxsc.TreeItem[TorrentNode]])
    extends TorrentNode(name):
    import Folder.*

    private val mutableInclude: SimpleObjectProperty[FolderInclude] = SimpleObjectProperty(this, "include", Include.No)
    def include: ReadOnlyObjectProperty[FolderInclude] = mutableInclude
    def toggleInclude(): Unit =
      val priority = if (include() == Include.Yes) Priority.IGNORE else Priority.NORMAL
      for selected <- Option(Torrent.selected()) do
        val priorities = selected.torrent.handle.filePriorities
        allFiles.foreach(file => priorities(file.index) = priority)
        selected.torrent.handle.prioritizeFiles(priorities)
    private def allFiles: List[File] = children.toList.flatMap:
      _.getValue match
        case file: File => Some(file)
        case folder: Folder => folder.allFiles

    private var childIncludeCountX2 = 0
    private val includeListener: jfxbv.ChangeListener[FolderInclude] = (_, oldValue, newValue) =>
      childIncludeCountX2 += newValue.value - oldValue.value
      mutableInclude() =
        if (childIncludeCountX2 == 0) Include.No
        else if (childIncludeCountX2 == childCount() * 2) Include.Yes
        else Include.Part

    private val progressListener: jfxbv.ChangeListener[Number] =
      (_, oldValue, newValue) => mutableProgress() = mutableProgress() + newValue.longValue - oldValue.longValue

    private val mutableSize = SimpleLongProperty(this, "size")
    def size: ReadOnlyLongProperty = mutableSize
    private val sizeListener: jfxbv.ChangeListener[Number] =
      (_, oldValue, newValue) => mutableSize() = mutableSize() + newValue.longValue - oldValue.longValue

    private val childCount = SimpleIntegerProperty(this, "childCount")

    applyDiff { children.diffSum(nodeDiff(_, AddListener)) }
    children.onChange: (_, changes) =>
      val diff = changes.diffSum:
        case ObservableBuffer.Add(_, added) => added.diffSum(nodeDiff(_, AddListener))
        case ObservableBuffer.Remove(_, removed) => -removed.diffSum(nodeDiff(_, RemoveListener))
        case _ => emptyDiff
      applyDiff(diff)

    private def nodeDiff(node: jfxsc.TreeItem[TorrentNode], updateListener: UpdateListener): Diff =
      nodeDiff(node.getValue, updateListener)
    private def nodeDiff(node: TorrentNode, updateListener: UpdateListener): Diff =
      updateListener(node.include, includeListener)
      updateListener(node.progress, progressListener)
      val size = node match
        case file: File => file.size
        case folder: Folder =>
          updateListener(folder.size, sizeListener)
          folder.size()
      Diff(node.include().value, node.progress(), size, 1)

    private def applyDiff(diff: Diff): Unit =
      childIncludeCountX2 += diff.includeX2
      if (diff.progress == 0) mutableProgress.fireValueChangedEvent()
      else mutableProgress() = mutableProgress() + diff.progress
      mutableSize() = mutableSize() + diff.size
      childCount() = childCount() + diff.children

  object Folder:
    private class Diff(val includeX2: Int, val progress: Long, val size: Long, val children: Int):
      def +(other: Diff) = Diff(
        includeX2 + other.includeX2,
        progress + other.progress,
        size + other.size,
        children + other.children,
      )
      def unary_- = Diff(-includeX2, -progress, -size, -children)
    private val emptyDiff = Diff(0, 0, 0, 0)
    extension [T](it: Iterable[T]) private def diffSum(map: T => Diff) =
      it.foldLeft(emptyDiff)((acc, item) => acc + map(item))

    private trait UpdateListener:
      def apply[T](observable: jfxbv.ObservableValue[T], listener: jfxbv.ChangeListener[? >: T]): Unit
    private object AddListener extends UpdateListener:
      def apply[T](observable: jfxbv.ObservableValue[T], listener: jfxbv.ChangeListener[? >: T]): Unit =
        observable.addListener(listener)
    private object RemoveListener extends UpdateListener:
      def apply[T](observable: jfxbv.ObservableValue[T], listener: jfxbv.ChangeListener[? >: T]): Unit =
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

    tree.value = Folder("", tree.children)


  sealed trait FolderInclude { def value: Int }
  sealed trait FileInclude extends FolderInclude
  object Include:
    object No extends FileInclude { def value = 0 }
    object Yes extends FileInclude { def value = 2 }
    object Part extends FolderInclude { def value = 1 }


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
