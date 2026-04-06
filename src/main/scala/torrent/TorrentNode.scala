package torrent

import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import fx.SelfProperty
import javafx.beans.binding.StringExpression
import javafx.beans.property.{SimpleFloatProperty, SimpleIntegerProperty, SimpleObjectProperty}
import javafx.scene.control as jfxsc
import org.libtorrent4j.{TorrentHandle, TorrentStatus}
import scalafx.Includes.{jfxObjectProperty2sfx, jfxTreeItem2sfx}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem

import java.math.RoundingMode
import scala.annotation.tailrec


sealed abstract class TorrentNodeBase(val name: String) extends SelfProperty:
  val state = new SimpleObjectProperty[TorrentStatus.State](this, "state")
  val progress = new SimpleFloatProperty(this, "progress")
  private[torrent] val tree = new jfxsc.TreeItem[TorrentNode]


sealed abstract class TorrentNode(name: String) extends TorrentNodeBase(name):
  tree.value = this

object TorrentNode:

  class Root(handle: TorrentHandle)
    extends TorrentNodeBase( /*Option(handle.getName).filter(_.nonEmpty).getOrElse*/("???") ):
    val infoHash: String = "dummy hash"//handle.infoHash.toHex
    
    val (download, downloadStr) = speedProperties("download")
    val (upload, uploadStr) = speedProperties("upload")

    /*private val fileInfo = handle.torrentFile
    private val data = if (fileInfo == null) Nil else List.tabulate(fileInfo.numFiles) { index =>
      val it = fileInfo.files.filePath(index).split(File.separatorChar).reverseIterator
      if (!it.hasNext) sys.error("Empty split array iterator")
      val file = new File(it.next, index)
      val preChild = it.foldLeft[PreChild](PreFile(file)) { case (child, prefix) => PreFolder(prefix, child) }
      (file, preChild)
    }
    val files: IArray[File] = data.map(_._1).toIArray
    tree.children = data.map(_._2).toTreeChildren*/
    
    //debug
    state.value = TorrentStatus.State.DOWNLOADING
    private val debugChildren = List(
      PreFolder("folder 1", PreFolder("folder in 1", PreFile(new File("file 0", 0)))),
      PreFolder("folder 1", PreFolder("another folder in 1", PreFile(new File("file 1", 1)))),
      PreFolder("folder 1",PreFile(new File("file 2", 2))),
      PreFolder("folder 2", PreFolder("folder in 2", PreFile(new File("file 3", 3)))),
      PreFile(new File("just a file 4", 4)),
    )
    tree.children = scala.util.Random.shuffle(debugChildren).toTreeChildren

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

  object Root:
    val list = ObservableBuffer(new Root(null), new Root(null))


  class Folder private[TorrentNode](name: String, preChildren: List[PreChild]) extends TorrentNode(name):
    tree.children = preChildren.toTreeChildren
    
  class File private[TorrentNode](name: String, val index: Int) extends TorrentNode(name)


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
      nodes.map(_.tree)
  