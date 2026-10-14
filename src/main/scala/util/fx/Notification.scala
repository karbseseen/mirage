package util.fx

import atlantafx.base.controls as afxbc
import atlantafx.base.theme.Styles
import atlantafx.base.util.Animations
import constant.Constants
import javafx.beans.property.{ObjectProperty, StringProperty}
import javafx.beans.value as jfxbv
import javafx.event.{Event, EventHandler}
import javafx.scene.{layout, layout as jfxsl}
import javafx.{scene, collections as jfxc, scene as jfxs}
import scalafx.Includes.{jfxAnimation2sfx, jfxDuration2sfx, jfxGroup2sfx, jfxPane2sfx, jfxScene2sfx, jfxStringProperty2sfx, observableList2ObservableBuffer}
import scalafx.application.Platform
import scalafx.collections.ObservableBuffer
import scalafx.delegate.SFXDelegate
import scalafx.event.subscriptions.Subscription
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Control
import scalafx.scene.layout.{Region, VBox}
import scalafx.stage.Stage
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

  private lazy val subscription: Subscription = layoutBounds.onChange {
    Animations.slideInDown(this, Constants.animationDuration).play()
    closeAfter.foreach(_ => close(closeAfter))
    subscription.cancel()
  }
  subscription


trait NotificationStage extends Stage:
  focused.addListener((_,_,focused) => if (focused) onFocus())
  protected def onFocus(): Unit =
    NotificationStage.lastFocused.foreach:
      _.scene().content.remove(NotificationStage.view)
    scene().content.add(NotificationStage.view)
    NotificationStage.lastFocused = Some(this)

object NotificationStage:
  private var lastFocused: Option[Stage] = None
  val view: VBox = new VBox(Constants.inset):
    alignmentInParent = Pos.TopRight
    maxWidth = Region.UsePrefSize
    maxHeight = Region.UsePrefSize
    margin = AutoInsets(top = Constants.inset, right = Constants.inset)

  /**Thread-safe*/
  def showError(error: String | Throwable | jfxbv.ObservableValue[String]): Unit =
    runLaterIfNeeded:
      view.children += new PopupNotification:
        styleClass += Styles.DANGER
        error match
          case str: String => message = str
          case error: Throwable => message = error.getMessage
          case observable: jfxbv.ObservableValue[String @unchecked] => message <== observable
