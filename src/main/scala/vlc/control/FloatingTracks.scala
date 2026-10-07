package vlc.control

import javafx.beans.property.SimpleIntegerProperty
import scalafx.Includes.jfxNode2sfx
import scalafx.scene.control.Button
import uk.co.caprica.vlcj.player.base.TrackDescription

import scala.jdk.CollectionConverters.*


private abstract class FloatingTracks extends FloatingMenu:

  val selectedId = SimpleIntegerProperty(this, "selectedId", -1)

  def getTracks: java.util.List[TrackDescription]
  def onSelect(vlcId: Int): Unit

  def addItem(vlcId: Int): Unit =
    val index = children.indexWhere(_.id().toIntOption.exists(_ > vlcId))
    children.add(if (index >= 0) index else children.size, TrackItem(vlcId))

  def removeItem(vlcId: Int): Unit =
    val index = children.indexWhere(_.id().toIntOption.exists(_ == vlcId))
    if (index >= 0) children.remove(index)

  override def bindControl(control: FloatingMenu.Control, controls: Controls): Unit =
    super.bindControl(control, controls)
    control.visible = false
    control.managed <== control.visible
    children.onChange((children, _) => control.visible = children.size > 1)

  protected class TrackItem(vlcId: Int) extends FloatingMenu.Item:
    underline <== selectedId.isEqualTo(vlcId)
    text = getTracks.asScala.find(_.id == vlcId).fold("???")(_.description)
    id = vlcId.toString
    onAction = _ => onSelect(vlcId)
