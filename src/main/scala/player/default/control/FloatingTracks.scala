package player.default.control

import constant.Tr
import javafx.beans.property.SimpleIntegerProperty
import scalafx.Includes.{jfxIntegerProperty2sfx, jfxNode2sfx}
import scalafx.geometry.Insets
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.TrackDescription


private sealed abstract class FloatingTracks(using FloatingMenu.Parent) extends FloatingMenu:

  val selectedId = SimpleIntegerProperty(this, "selectedId", -1)

  protected def getTracks: Iterable[TrackDescription]
  protected def select(vlcId: Int): Unit

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
    label.text = getTracks.find(_.id == vlcId).fold("???")(_.description)
    selectIcon.visible <== selectedId.isEqualTo(vlcId)
    id = vlcId.toString
    onMouseClicked = event =>
      select(vlcId)
      event.consume()


private trait FloatingTrackMenus extends FloatingMenu.Parent:

  protected val videoMenu: FloatingTracks = new FloatingTracks:
    protected def getTracks: Iterable[TrackDescription] = player.videoTrackDescriptions
    protected def select(vlcId: Int): Unit = player.videoTrack = vlcId

  protected val audioMenu: FloatingTracks = new FloatingTracks:
    protected def getTracks: Iterable[TrackDescription] = player.audioTrackDescriptions
    protected def select(vlcId: Int): Unit = player.audioTrack = vlcId

  protected val subtitleMenu: FloatingTracks = new FloatingTracks:
    protected def getTracks: Iterable[TrackDescription] = player.subtitleTrackDescriptions
    protected def select(vlcId: Int): Unit = player.subtitleTrack = vlcId
    override def removeItem(vlcId: Int): Unit = if (vlcId != -1) super.removeItem(vlcId)
    children += new TrackItem(-1) { label.text <== Tr.noSubtitles }


  def addTrack(vlcId: Int, trackType: TrackType): Unit =
    getTracks(trackType).foreach(_.addItem(vlcId))

  def removeTrack(vlcId: Int, trackType: TrackType): Unit =
    getTracks(trackType).foreach(_.removeItem(vlcId))

  def onTrackSelect(vlcId: Int, trackType: TrackType): Unit =
    getTracks(trackType).foreach(_.selectedId() = vlcId)

  private def getTracks(trackType: TrackType) =
    Some(trackType).collect:
      case TrackType.VIDEO => videoMenu
      case TrackType.AUDIO => audioMenu
      case TrackType.TEXT => subtitleMenu
