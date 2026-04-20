package torrent

import com.frostwire.jlibtorrent.{TorrentInfo, TorrentStatus}
import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import fx.SelfProperty
import javafx.beans.binding.StringExpression
import javafx.beans.property.{SimpleFloatProperty, SimpleIntegerProperty, SimpleObjectProperty, SimpleStringProperty}
import javafx.scene.control as jfxsc
import scalafx.Includes.{jfxObjectProperty2sfx, jfxTreeItem2sfx}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem
import util.also

import java.math.RoundingMode
import scala.annotation.tailrec
import scala.collection.mutable


sealed abstract class TorrentNodeBase:
  val state = new SimpleObjectProperty[State](this, "state")
  val progress = new SimpleFloatProperty(this, "progress")


sealed abstract class TorrentNode(val name: String) extends TorrentNodeBase with SelfProperty:
  private[TorrentNode] def toTree = new jfxsc.TreeItem[TorrentNode].also(_.value = this)

object TorrentNode:

  class Root(_name: String, val infoHash: String) extends TorrentNodeBase:

    state.value = State(null)

    val name = SimpleStringProperty(this, "name", _name)
    val (downSpeed, downSpeedStr) = speedProperties("download")
    val (upSpeed, upSpeedStr) = speedProperties("upload")

    private[torrent] val tree = new jfxsc.TreeItem[TorrentNode]
    private var _files = Array.empty[File]
    def files: IArray[File] = _files.asInstanceOf[IArray[File]]


    private def speedProperties(name: String) =
      val intProperty = new SimpleIntegerProperty(this, name)
      val strProperty = intProperty.flatMap(speed => speedStr(speed.doubleValue))
      (intProperty, strProperty)

    @tailrec private def speedStr(value: Double, units: List[Translate] = Tr.Speed.allList): StringExpression =
      def binding(scale: Int) =
        val valueStr = java.math.BigDecimal(value)
          .setScale(scale, RoundingMode.HALF_UP)
          .stripTrailingZeros
          .toPlainString
        b"$valueStr ${units.head}"
  
      if (value >= 1000 && units.tail.nonEmpty) speedStr(value / 1024, units.tail)
      else if (value >= 100) binding(0)
      else if (value >= 10) binding(1)
      else binding(2)

    private[torrent] def setFiles(info: TorrentInfo): Unit =
      val data = List.tabulate(info.numFiles): index =>
        val it = info.files.filePath(index).split(java.io.File.separatorChar).reverseIterator
        if (!it.hasNext) sys.error("Empty split array iterator")
        val file = new File(it.next, index)
        val preChild = it.foldLeft[PreChild](PreFile(file)) { case (child, prefix) => PreFolder(prefix, child) }
        (preChild, file)
      tree.children = data.map(_._1).toTreeChildren
      _files = data.map(_._2).toArray
    
  end Root

  class Folder private[TorrentNode](name: String, preChildren: List[PreChild]) extends TorrentNode(name):
    override private[TorrentNode] def toTree = super.toTree.also(_.children = preChildren.toTreeChildren)
    
  class File private[TorrentNode](name: String, val index: Int) extends TorrentNode(name)


  /**UI thread only*/
  val roots: ObservableBuffer[Root] = ObservableBuffer.empty
  private val rootMap = mutable.Map.empty[String, Root]
  /**UI thread only*/
  def getTorrent(hash: String): Option[Root] = rootMap.get(hash)
  roots.onChange: (_, changes) =>
    changes.foreach:
      case ObservableBuffer.Add(_, added) => rootMap ++= added.map { torrent => torrent.infoHash -> torrent }
      case ObservableBuffer.Remove(_, removed) => rootMap --= removed.map { torrent => torrent.infoHash }
      case _ => ()


  private trait PreChild
  private case class PreFile(file: File) extends PreChild
  private case class PreFolder(name: String, child: PreChild) extends PreChild
  extension (preChildren: List[PreChild])
    private def toTreeChildren: List[TreeItem[TorrentNode]] =
      val (files, preFolders) = preChildren.partitionMap:
        case PreFile(file) => Left(file)
        case folder: PreFolder => Right(folder)
      val folders = preFolders.groupMap(_.name)(_.child).map(new Folder(_, _)).toList
      val nodes = folders.sortBy(_.name) ::: files.sortBy(_.name)
      nodes.map(_.toTree)
  