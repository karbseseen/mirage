package torrent

import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.TorrentStatus.State.CHECKING_FILES
import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import javafx.beans.binding.StringExpression
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.fluentui.{FluentUiRegularAL, FluentUiRegularMZ}


sealed trait State:
  def icon: Ikon
  def tooltip: StringExpression


object State:

  class Resumed private[State] (val icon: Ikon, val tooltip: StringExpression) extends State
  class Paused (val resumed: Resumed) extends State:
    def icon = FluentUiRegularMZ.PAUSE_24
    def tooltip: StringExpression = b"${Tr.paused} (${resumed.tooltip})"
    override def hashCode: Int = resumed.hashCode + 1
    override def equals(other: Any): Boolean = other match
      case other: Paused => resumed == other.resumed
      case _ => false

  private val map =
    import FluentUiRegularAL.*
    import FluentUiRegularMZ.*
    import TorrentStatus.State.*
    Map(
      CHECKING_FILES        -> Resumed(DOCUMENT_SEARCH_24,         Tr.checkingFiles   ),
      CHECKING_RESUME_DATA  -> Resumed(ARROW_ROTATE_CLOCKWISE_24,  Tr.checkingFiles   ),
      DOWNLOADING_METADATA  -> Resumed(ARROW_SYNC_24,              Tr.downloadingMeta ),
      DOWNLOADING           -> Resumed(ARROW_DOWNLOAD_24,          Tr.downloading     ),
      FINISHED              -> Resumed(CHECKMARK_24,               Tr.finished        ),
      SEEDING               -> Resumed(CHECKMARK_CIRCLE_24,        Tr.seeding         ),
    ) withDefaultValue         Resumed(QUESTION_24,                Tr.unknownState    )

  def apply(libtorrentState: TorrentStatus.State): Resumed = map(libtorrentState)
