package vlc

import config.{Config, given}
import constant.Tr
import javafx.scene.text as jfxst
import org.virtuslab.yaml.YamlCodec
import scalafx.Includes.jfxProperty2sfx
import scalafx.scene.control.Button
import util.fx.PopupNotification
import util.fx.TextFlowInterpolation.*

import scala.collection.immutable


case class VlcSavedState(
  time: Long,
  videoTrackId: Int,
  audioTrackId: Int,
  subtitleTractId: Int,
  customTitle: Option[String],
) derives YamlCodec


object VlcSavedState:

  private type Map = immutable.Map[String, immutable.Map[Int, VlcSavedState]]
  val config: Config.ObjectProp[Map] = Config.ObjectProp(this, "player-state", immutable.Map.empty)

  private[vlc] def save()(using stage: VlcStage): Unit =
    val player = stage.player
    val (hash, fileIndex) = stage.media.getHash
    val state = VlcSavedState(
      player.status.time,
      player.video.track,
      player.audio.track,
      player.subpictures.track,
      Some(stage.customTitle()).filter(_.nonEmpty),
    )
    config() = config().updatedWith(hash):
      case None => Some(Map(fileIndex -> state))
      case Some(map) => Some(map + (fileIndex -> state))

  private[vlc] def restore()(using stage: VlcStage): Option[VlcSavedState] =
    val (hash, fileIndex) = stage.media.getHash
    config().get(hash).flatMap(_.get(fileIndex)).map: state =>
      val player = stage.player
      player.video.setTrack(state.videoTrackId)
      player.audio.setTrack(state.audioTrackId)
      player.subpictures.setTrack(state.subtitleTractId)
      state.customTitle
        .filter(_ => stage.customTitle().isEmpty)
        .foreach(stage.updateCustomTitle)
      if (state.time > 10_000 && state.time < player.status.length - 10_000)
        stage.notificationBox.children += notification(state)
      state

  private def notification(state: VlcSavedState)(using stage: VlcStage) =
    new PopupNotification(focusMovable = false):
      notification =>

      graphic <== Tr.resumeFrom.map[jfxst.TextFlow]: resumeFrom =>
        flow"$resumeFrom ${time2str(state.time).bold}?"

      primaryActions += new Button:
        focusTraversable = false
        text <== Tr.stayHere
        onAction = _ => notification.close()

      primaryActions += new Button:
        focusTraversable = false
        text <== Tr.resume
        defaultButton = true
        onAction = _ =>
          stage.player.controls.setTime(state.time)
          notification.close()
