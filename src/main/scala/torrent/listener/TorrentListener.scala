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
  class Part[A <: Alert[?] : AlertTypeInt](typedAlert: A => Unit) extends AlertListener:
    def types: Array[Int] = Array(summon[AlertTypeInt[A]].value)
    def alert(alert: Alert[?]): Unit = typedAlert(alert.asInstanceOf[A])

  case class AlertTypeInt[A <: Alert[?]](value: Int)
  object AlertTypeInt:
    given [S <: alert, A <: Alert[S]](using tag: ClassTag[S]): AlertTypeInt[A] =
      AlertTypeInt { tag.runtimeClass.getField("alert_type").get(null).asInstanceOf[Integer].intValue }


abstract class TorrentListener:
  private var parts: List[Part[?]] = Nil

  protected def listen[A <: Alert[?] : AlertTypeInt](handler: A => Unit): Unit = parts = new Part(handler) :: parts
  protected def listenError[A <: Alert[?] : AlertTypeInt](getError: A => ErrorCode): Unit = listen[A](getError(_).check)

  extension (error: ErrorCode)
    protected def check: Boolean =
      val isError = error.isError
      if (isError) MainApp.showError(error.message)
      !isError

  def register(): Unit =
    parts.foreach(Torrent.session.addListener)
