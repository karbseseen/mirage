package torrent

import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.TorrentStatus.State.*
import constant.{Tr, Translate}
import fx.PropertyInterpolation.b
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.fluentui.FluentUiRegularAL.*
import org.kordamp.ikonli.fluentui.FluentUiRegularMZ.*


class State (
  val value: TorrentStatus.State,
  val paused: Boolean,
  val isNew: Boolean,
):
  private val isFileSelect: Boolean = isNew && paused &&
    (value == TorrentStatus.State.DOWNLOADING || value == TorrentStatus.State.FINISHED)

  private val (defaultIcon, defaultTooltip): (Ikon, Translate) = value match
    case CHECKING_FILES =>        (DOCUMENT_SEARCH_24,        Tr.checkingFiles  )
    case DOWNLOADING_METADATA =>  (ARROW_SYNC_24,             Tr.downloadingMeta)
    case DOWNLOADING =>           (ARROW_DOWNLOAD_24,         Tr.downloading    )
    case FINISHED =>              (CHECKMARK_24,              Tr.finished       )
    case SEEDING =>               (CHECKMARK_CIRCLE_24,       Tr.seeding        )
    case CHECKING_RESUME_DATA =>  (ARROW_ROTATE_CLOCKWISE_24, Tr.checkingFiles  )
    case UNKNOWN =>               (QUESTION_24,               Tr.unknownState   )

  val (icon, tooltip) =
    if (isFileSelect) (TEXT_BULLET_LIST_TREE_24,  Tr.selectFiles.property           )
    else if (paused)  (PAUSE_24,                  b"${Tr.paused} ($defaultTooltip)" )
    else              (defaultIcon,               defaultTooltip.property           )

  def copy(
    value: TorrentStatus.State = value,
    paused: Boolean = paused,
  ): State =
    State(value, paused, isNew && !isFileSelect)
