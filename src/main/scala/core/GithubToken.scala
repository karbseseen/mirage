package core

import atlantafx.base.theme.Styles
import com.github.javakeyring.Keyring
import constant.Tr
import scalafx.Includes.jfxProperty2sfx
import scalafx.beans.property.{ReadOnlyStringProperty, StringProperty}
import util.also
import util.fx.{NotificationStage, PopupNotification}

import scala.util.Using


private object GithubToken:
  private val domain = "mirage"
  private val key = "github-token"

  private val mutableProperty =
    val value = Using(Keyring.create)(_.getPassword(domain, key)).toOption.orNull
    new StringProperty(this, "Github token", value)
  def property: ReadOnlyStringProperty = mutableProperty

  def get: Option[String] = Option(mutableProperty.value)

  def set(value: String): Boolean =
    Using(Keyring.create)(_.setPassword(domain, key, value)).isSuccess.also: result =>
      if (result) mutableProperty.value = value
      NotificationStage.notifications += new PopupNotification:
        message <== (if (result) Tr.tokenSaved else Tr.tokenNotSaved)
        styleClass += (if (result) Styles.SUCCESS else Styles.DANGER)

  def clear(): Boolean =
    Using(Keyring.create)(_.deletePassword(domain, key)).isSuccess.also: result =>
      if (result) mutableProperty.value = null
      NotificationStage.notifications += new PopupNotification:
        message <== (if (result) Tr.tokenCleared else Tr.tokenNotCleared)
        styleClass += (if (result) Styles.SUCCESS else Styles.DANGER)
