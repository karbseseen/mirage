import atlantafx.base.theme.Styles
import com.github.javakeyring.Keyring
import constant.Tr
import fx.{NotificationBox, PopupNotification}
import scalafx.Includes.jfxProperty2sfx
import scalafx.beans.property.{ReadOnlyStringProperty, StringProperty}

import scala.util.Using


object GithubToken:
  private val domain = "mirage"
  private val key = "github-token"

  private val mutableProperty =
    val value = Using(Keyring.create)(_.getPassword(domain, key)).toOption.orNull
    new StringProperty(this, "Github token", value)
  def property: ReadOnlyStringProperty = mutableProperty

  def get: Option[String] = Option(mutableProperty.value)

  def set(value: String, notifications: Option[NotificationBox] = None): Boolean =
    val result = Using(Keyring.create)(_.setPassword(domain, key, value)).isSuccess

    if (result) mutableProperty.value = value
    for notifications <- notifications do
      notifications.children += new PopupNotification:
        message <== (if (result) Tr.tokenSaved else Tr.tokenNotSaved)
        styleClass += (if (result) Styles.SUCCESS else Styles.DANGER)

    result

  def clear(notifications: Option[NotificationBox] = None): Boolean =
    val result = Using(Keyring.create)(_.deletePassword(domain, key)).isSuccess

    if (result) mutableProperty.value = null
    for notifications <- notifications do
      notifications.children += new PopupNotification:
        message <== (if (result) Tr.tokenCleared else Tr.tokenNotCleared)
        styleClass += (if (result) Styles.SUCCESS else Styles.DANGER)

    result
