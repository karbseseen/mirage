package torrent.listener

import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.swig.alert
import com.frostwire.jlibtorrent.{AlertListener, ErrorCode}
import core.main.MainApp
import torrent.Torrent
import torrent.listener.TorrentListener.*

import scala.language.reflectiveCalls
import scala.reflect.ClassTag


object TorrentListener:
  def listen[A <: Alert[?]](handler: A => Unit)(using alertType: AlertTypeInt[A]): Unit =
    Torrent.session.addListener:
      new AlertListener:
        def types: Array[Int] = Array(alertType.value)
        def alert(alert: Alert[?]): Unit = handler(alert.asInstanceOf[A])

  def listenError[A <: Alert[?] : AlertTypeInt](getError: A => ErrorCode): Unit =
    listen[A](getError(_).check)

  case class AlertTypeInt[A <: Alert[?]](value: Int)
  object AlertTypeInt:
    given [S <: alert, A <: Alert[S]](using tag: ClassTag[S]): AlertTypeInt[A] =
      AlertTypeInt { tag.runtimeClass.getField("alert_type").get(null).asInstanceOf[Integer].intValue }


extension (error: ErrorCode) def check: Boolean =
  val isError = error.isError
  if (isError) MainApp.showError(error.message)
  !isError
