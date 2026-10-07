package vlc.control

import javafx.beans.property.SimpleIntegerProperty
import scalafx.Includes.jfxNode2sfx
import scalafx.geometry.Insets
import uk.co.caprica.vlcj.player.base.TrackDescription

import scala.jdk.CollectionConverters.*


private abstract class FloatingTracks(using FloatingMenu.Parent) extends FloatingMenu:

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

  protected class TrackItem(vlcId: Int) extends FloatingMenu.SelectableItem:
    padding = Insets(
      left    = inset * 1.5,
      right   = inset * 1.5,
      top     = inset,
      bottom  = inset,
    )
    label.text = getTracks.asScala.find(_.id == vlcId).fold("???")(_.description)
    selectIcon.visible <== selectedId.isEqualTo(vlcId)
    id = vlcId.toString
    onMouseClicked = event =>
      onSelect(vlcId)
      event.consume()
