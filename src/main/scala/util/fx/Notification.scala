package util.fx

import atlantafx.base.controls as afxbc
import atlantafx.base.theme.Styles
import constant.Constants
import javafx.animation.KeyFrame
import javafx.beans.property.{ObjectProperty, SimpleDoubleProperty, StringProperty}
import javafx.beans.{InvalidationListener, Observable, value as jfxbv}
import javafx.event.{Event, EventHandler}
import javafx.scene as jfxs
import javafx.scene.{control as jfxsc, layout as jfxsl}
import javafx.util.Duration
import scalafx.Includes.{jfxDoubleProperty2sfx, jfxDuration2sfx, jfxGroup2sfx, jfxPane2sfx, jfxProperty2sfx, observableList2ObservableBuffer}
import scalafx.animation.Timeline
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Pos
import scalafx.scene.Node
import scalafx.scene.control.{Button, Control}
import scalafx.scene.layout.{Region, VBox}
import scalafx.stage.Stage

import java.lang
import scala.language.implicitConversions


class Notification(override val delegate: afxbc.Notification) extends Control(delegate):
  def this(message: String = null, focusMovable: Boolean = true) = this(
    if (focusMovable) new afxbc.Notification(message) with Notification.FocusMovable
    else afxbc.Notification(message)
  )

  viewOrder() = Notification.counter
  Notification.counter += 1

  def message: StringProperty = delegate.messageProperty
  def message_=(value: String): Unit = delegate.setMessage(value)

  def graphic: ObjectProperty[jfxs.Node] = delegate.graphicProperty
  def graphic_=(value: Node): Unit = delegate.setGraphic(value)

  def primaryActions: ObservableBuffer[jfxsc.Button] = delegate.getPrimaryActions
  def primaryActions_=(value: Seq[Button]): Unit = delegate.setPrimaryActions(value.map(_.delegate)*)

  def onClose: ObjectProperty[EventHandler[? >: Event]] = delegate.onCloseProperty
  def onClose_=(value: EventHandler[? >: Event]): Unit = delegate.setOnClose(value)
  def close(): Unit = onClose().handle(null)

object Notification:
  trait FocusMovable extends jfxs.Node
  private var counter = 0
  implicit def fxNotification2afx(notification: Notification): afxbc.Notification = notification.delegate
  implicit def afxNotification2fx(notification: afxbc.Notification): Notification = new Notification(notification)


class PopupNotification(
  message: String = null,
  focusMovable: Boolean = true,
  closeAfter: Option[Int] = Some(7500),
) extends Notification(message, focusMovable):
  styleClass += Styles.INTERACTIVE

  private var animation: Option[Animation] = None
  private var manuallyClosed = false

  private val y = SimpleDoubleProperty(this, "y")
  private val otherY = SimpleDoubleProperty(this, "otherY")
  translateY <== y + otherY
  PopupNotification.this.animateLayoutY(otherY)

  height.addListener:
    new InvalidationListener:
      def invalidated(o: Observable): Unit =
        if (height() != 0)
          if (!manuallyClosed) animation = Some(new Animation)
          height.removeListener(this)

  onClose = _ =>
    if (!manuallyClosed)
      manuallyClosed = true
      animation.foreach(_.close(0))

  private class Animation extends Timeline(javafx.animation.Timeline(
    KeyFrame(0,   easeValue(y, -height() - Constants.inset),  easeValue(opacity, 0.85)),
    KeyFrame(240, easeValue(y, 0),                            easeValue(opacity, 1)),
  )):
    closeAfter.foreach(delay => onFinished = _ => close(delay))
    play()
    def close(after: Int): Unit =
      stop()
      rate = -1
      delay = Duration(after)
      onFinished = _ => parent() match
        case pane: jfxsl.Pane => pane.children -= PopupNotification.this
        case group: jfxs.Group => group.children -= PopupNotification.this
        case _ => ()
      play()


sealed trait JfxNotificationBox extends jfxsl.VBox
sealed trait NotificationBox extends VBox


trait NotificationStage extends Stage:
  val notificationBox: VBox & NotificationBox =
    new VBox(new jfxsl.VBox(Constants.inset) with JfxNotificationBox) with NotificationBox:
      alignmentInParent = Pos.TopRight
      maxWidth = Region.UsePrefSize
      maxHeight = Region.UsePrefSize
      padding = AutoInsets(top = Constants.inset, right = Constants.inset)
      opacity <== NotificationStage.this.focused.map[lang.Double](if (_) 1 else 0.6)

  focused.addListener((_,_,focused) => if (focused) onFocus())

  protected def onFocus(): Unit =
    val (movable, nonMoveable) = NotificationStage._notifications.toList.partition:
      _.isInstanceOf[Notification.FocusMovable]

    NotificationStage._notifications.setAll(nonMoveable*)

    val sorted = notificationBox.children.toArray ++ movable
    sorted.sortInPlaceBy(_.getViewOrder)
    notificationBox.children.setAll(sorted*)

    NotificationStage._notifications = notificationBox.children

object NotificationStage:
  private var _notifications = ObservableBuffer[jfxs.Node]()
  def notifications: ObservableBuffer[jfxs.Node] = _notifications

  /**Thread-safe*/
  def showError(error: String | Throwable | jfxbv.ObservableValue[String]): Unit =
    ensureRunLater:
      notifications += new PopupNotification:
        styleClass += Styles.DANGER
        error match
          case str: String => message = str
          case error: Throwable => message = error.getMessage
          case observable: jfxbv.ObservableValue[String @unchecked] => message <== observable
