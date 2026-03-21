package util

import scalafx.beans.property.{ObjectProperty, ReadOnlyStringProperty, StringProperty}

import scala.annotation.tailrec
import scala.language.implicitConversions


enum Language(val name: String, val default: Language | Null = null):
  case en extends Language("English")
  case ru extends Language("Русский", en)

object Language:
  val current = new ObjectProperty[Language](this, "Language", Language.en)


case class Translate(
  en: String,
  ru: String = "",
):
  @tailrec final def apply(language: Language): String =
    val value = productElement(language.ordinal).asInstanceOf[String]
    if value.nonEmpty then value
    else apply(language.default)

  private val mutableProperty = new StringProperty(this, en)
  def property: ReadOnlyStringProperty = mutableProperty
  Language.current.subscribe { lang => if (lang != null) mutableProperty.set(apply(lang)) }

object Translate:
  implicit def asProperty(translate: Translate): ReadOnlyStringProperty = translate.property


object Tr:
  val appName       = Translate("Mirage",           "Мираж")
  val date          = Translate("Date",             "Дата")
  val file          = Translate("File",             "Файл")
  val language      = Translate("Language",         "Язык")
  val loading       = Translate("Loading",          "Загрузка")
  val naming        = Translate("Name",             "Название")
  val reload        = Translate("Reload",           "Перезагрузить")
  val retry         = Translate("Retry",            "Попробовать еще раз")
  val selectCommit  = Translate("Select a commit",  "Выберите комит")
  val settings      = Translate("Settings",         "Настройки")
  val update        = Translate("Update",           "Обновить")
