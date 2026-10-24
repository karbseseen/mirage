package player.default

import config.{Config, given}
import constant.Tr
import javafx.scene.text as jfxst
import org.virtuslab.yaml.YamlCodec
import player.{Player, PlayerStage, time2str}
import scalafx.Includes.{jfxObservableValue2sfx, jfxProperty2sfx, jfxScene2sfx, jfxVBox2sfx, observableList2ObservableBuffer}
import scalafx.scene.control.Button
import util.fx.{JfxNotificationBox, PopupNotification}
import util.fx.TextFlowInterpolation.*

import scala.collection.immutable


private case class PlayerSavedState(
  time: Long,
  videoTrackId: Int,
  audioTrackId: Int,
  subtitleTractId: Int,
  customTitle: Option[String],
) derives YamlCodec


object PlayerSavedState:

  private type Map = immutable.Map[String, immutable.Map[String, PlayerSavedState]]
  private val config: Config.ObjectProp[Map] = Config.ObjectProp(this, "player-state", immutable.Map.empty)

  private[player] def save()(using player: DefaultPlayer): Unit =
    val (hash, fileIndex) = player.fileId.configId
    val state = PlayerSavedState(
      player.time,
      player.videoTrack,
      player.audioTrack,
      player.subtitleTrack,
      Some(player.customTitle()).filter(_.nonEmpty),
    )
    config() = config().updatedWith(hash):
      case None => Some(Map(fileIndex -> state))
      case Some(map) => Some(map + (fileIndex -> state))

  private[player] def restore()(using player: DefaultPlayer): Option[PlayerSavedState] =
    val (hash, fileIndex) = player.fileId.configId
    config().get(hash).flatMap(_.get(fileIndex)).map: state =>
      player.videoTrack = state.videoTrackId
      player.audioTrack = state.audioTrackId
      player.subtitleTrack = state.subtitleTractId
      state.customTitle
        .filter(_ => player.customTitle().isEmpty)
        .foreach(player.setCustomTitle(_))
      if (state.time > 10_000 && state.time < player.length - 10_000)
        Option(player.scene()).toList.flatMap(_.content).collectFirst:
          case notificationBox: JfxNotificationBox =>
            notificationBox.children += notification(state)
      state

  private def notification(state: PlayerSavedState)(using player: DefaultPlayer) =
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
          player.setTime(state.time)
          notification.close()

  def -=(hash: String): Unit =
    config() = config() - hash
