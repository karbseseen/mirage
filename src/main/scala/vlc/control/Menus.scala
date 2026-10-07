package vlc.control

import constant.Tr
import javafx.animation.{KeyFrame, KeyValue, Timeline}
import org.kordamp.ikonli.fluentui.FluentUiFilledAL
import scalafx.Includes.jfxText2sfxText
import scalafx.animation.Interpolator.EaseBoth
import scalafx.scene.Node
import scalafx.scene.layout.HBox
import scalafx.util.Duration
import uk.co.caprica.vlcj.player.base.TrackDescription
import vlc.VlcStage


private trait Menus:
  protected val stage: VlcStage
  import stage.player

  def extraParts: List[Node] = List(videoMenu, audioMenu, titleMenu, settingsMenu).map(_.holder)

  val videoMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.video.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.video.setTrack(vlcId)

  val audioMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.audio.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.audio.setTrack(vlcId)

  val titleMenu: FloatingTracks = new FloatingTracks:
    def getTracks: java.util.List[TrackDescription] = player.subpictures.trackDescriptions
    def onSelect(vlcId: Int): Unit = player.subpictures.setTrack(vlcId)
    override def removeItem(vlcId: Int): Unit = if (vlcId != -1) super.removeItem(vlcId)
    children += new TrackItem(-1) { label.text <== Tr.noSubtitles }

  protected val settingsMenu: FloatingMenu = new FloatingMenu:

    children += new FloatingMenu.SelectableItem:
      label.text <== Tr.crop
      label.graphic = IconView(FluentUiFilledAL.CROP_24, 24)
      selectIcon.visible = false

      private val animation = Timeline(
        KeyFrame(Duration(0), KeyValue(stage.videoCropCoef, 0)),
        KeyFrame(Duration(250), KeyValue(stage.videoCropCoef, 1, EaseBoth)),
      )
      onMouseClicked = _ =>
        selectIcon.visible() = !selectIcon.visible()
        animation.setRate(if (selectIcon.visible()) 1 else -1)
        animation.play()
