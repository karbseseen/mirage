package fx

import atlantafx.base.controls as afxbc
import atlantafx.base.theme.Styles
import atlantafx.base.util.Animations
import constant.Constants
import javafx.beans.property.{ObjectProperty, StringProperty}
import javafx.event.{Event, EventHandler}
import javafx.scene.{layout, layout as jfxsl}
import javafx.{collections as jfxc, scene as jfxs}
import scalafx.Includes.{jfxAnimation2sfx, jfxDuration2sfx, jfxGroup2sfx, jfxPane2sfx, observableList2ObservableBuffer}
import scalafx.collections.ObservableBuffer
import scalafx.delegate.SFXDelegate
import scalafx.event.subscriptions.Subscription
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Control
import scalafx.scene.layout.Region
import scalafx.util.Duration

import scala.language.implicitConversions


class Notification(override val delegate: afxbc.Notification) extends Control(delegate):
  def this(message: String = null) = this(new afxbc.Notification(message))

  def message: StringProperty = delegate.messageProperty
  def message_=(value: String): Unit = delegate.setMessage(value)

  def onClose: ObjectProperty[EventHandler[? >: Event]] = delegate.onCloseProperty
  def onClose_=(value: EventHandler[? >: Event]): Unit = delegate.setOnClose(value)

object Notification:
  implicit def fxNotification2afx(notification: Notification): afxbc.Notification = notification.delegate
  implicit def afxNotification2fx(notification: afxbc.Notification): Notification = new Notification(notification)


class PopupNotification(
  message: String = null,
  closeAfter: Option[Duration] = Some(Constants.animationCloseAfter),
) extends Notification(message):
  styleClass += Styles.INTERACTIVE

  private def close(delay: Option[Duration]): Unit =
    val animation = Animations.slideOutUp(this, Constants.animationDuration)
    delay.foreach(animation.delay = _)
    animation.onFinished = _ => parent.value match
      case pane: jfxsl.Pane => pane.children -= this
      case group: jfxs.Group => group.children -= this
      case _ => ()
    animation.play()

  onClose = _ => close(None)

  val _ =
    lazy val subscription: Subscription = layoutBounds.onChange {
      Animations.slideInDown(this, Constants.animationDuration).play()
      closeAfter.foreach(_ => close(closeAfter))
      subscription.cancel()
    }
    subscription


class NotificationBox private (override val delegate: jfxsl.VBox)
  extends Region(delegate) with SFXDelegate[jfxsl.VBox]:
  def this() = this(new jfxsl.VBox(Constants.inset))

  alignmentInParent = Pos.TopRight
  maxWidth = Region.UsePrefSize
  maxHeight = Region.UsePrefSize
  margin = Insets(top = Constants.inset, right = Constants.inset, left = 0, bottom = 0)

  def children: ObservableBuffer[afxbc.Notification] = delegate.getChildren.asInstanceOf[jfxc.ObservableList[afxbc.Notification]]

object NotificationBox:
  implicit def fxNotificationBox2jfx(notifications: NotificationBox): jfxsl.VBox = notifications.delegate
