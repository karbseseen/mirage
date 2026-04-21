package torrent

import fx.SelfProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.scene.control as jfxsc
import scalafx.Includes.jfxTreeItem2sfx
import scalafx.scene.control.TreeItem
import util.also


sealed abstract class TorrentNode(val name: String) extends SelfProperty:
  val progress = new SimpleFloatProperty(this, "progress")
  private[TorrentNode] def toTree = new jfxsc.TreeItem[TorrentNode].also(_.value = this)


object TorrentNode:

  class Folder private[torrent](name: String, preChildren: List[PreChild]) extends TorrentNode(name):
    override private[TorrentNode] def toTree = super.toTree.also(_.children = preChildren.toTreeChildren)
    
  class File private[torrent](name: String, val index: Int) extends TorrentNode(name)

  private[torrent] trait PreChild
  private[torrent] case class PreFile(file: File) extends PreChild
  private[torrent] case class PreFolder(name: String, child: PreChild) extends PreChild
  extension (preChildren: List[PreChild])
    private[torrent] def toTreeChildren: List[TreeItem[TorrentNode]] =
      val (files, preFolders) = preChildren.partitionMap:
        case PreFile(file) => Left(file)
        case folder: PreFolder => Right(folder)
      val folders = preFolders.groupMap(_.name)(_.child).map(new Folder(_, _)).toList
      val nodes = folders.sortBy(_.name) ::: files.sortBy(_.name)
      nodes.map(_.toTree)
