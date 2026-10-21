package player

import uk.co.caprica.vlcj.player.base.{MediaPlayer, State, TrackDescription}
import util.also

import scala.jdk.CollectionConverters.CollectionHasAsScala


trait PlayerGetSet:
  protected val vlc: MediaPlayer
  protected val media: Media

  def isPlaying: Boolean = vlc.status.isPlaying
  def time: Long = vlc.status.time
  def length: Long = vlc.status.length
  def speedX10: Int = (vlc.status.rate * 10).round
  
  def volume: Int = vlc.audio.volume
  def videoTrack: Int = vlc.video.track
  def audioTrack: Int = vlc.audio.track
  def subtitleTrack: Int = vlc.subpictures.track

  def volume_=(value: Int): Boolean = vlc.audio.setVolume(value)
  def videoTrack_=(value: Int): Int = vlc.video.setTrack(value)
  def audioTrack_=(value: Int): Int = vlc.audio.setTrack(value)
  def subtitleTrack_=(value: Int): Int = vlc.subpictures.setTrack(value)

  def videoTrackDescriptions: Iterable[TrackDescription] = vlc.video.trackDescriptions.asScala
  def audioTrackDescriptions: Iterable[TrackDescription] = vlc.audio.trackDescriptions.asScala
  def subtitleTrackDescriptions: Iterable[TrackDescription] = vlc.subpictures.trackDescriptions.asScala

  def isFinished: Boolean = vlc.media.info.state match
    case State.STOPPED | State.ENDED | State.ERROR => true
    case _ => false
  def restart(): Unit = vlc.media.play(media)
  def restartIfFinished(): Boolean = isFinished.also(if (_) restart())
