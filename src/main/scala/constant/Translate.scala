package constant

import config.{Config, Language}
import javafx.beans.property as jfxbp

import java.util.function.Consumer
import scala.annotation.tailrec
import scala.language.implicitConversions


case class Translate(
  en: String,
  ru: String = "",
):
  @tailrec final def apply(language: Language): String =
    val value = productElement(language.ordinal).asInstanceOf[String]
    if value.nonEmpty then value
    else language.default match
      case None => ""
      case Some(default) => apply(default)

  private val mutableProperty = new jfxbp.SimpleStringProperty(this, en)
  def property: jfxbp.ReadOnlyStringProperty = mutableProperty
  Config[Language].subscribe { lang => mutableProperty.set(apply(lang)) }

object Translate:
  implicit def asProperty(translate: Translate): jfxbp.ReadOnlyStringProperty = translate.property


object Tr:
  val appName         = Translate("Mirage",                     "Мираж")
  val branch          = Translate("Branch",                     "Ветка")
  val clearToken      = Translate("Clear Github token",         "Удалить Github токен")
  val createToken     = Translate("You can create it here",     "Его можно создать здесь")
  val date            = Translate("Date",                       "Дата")
  val file            = Translate("File",                       "Файл")
  val go              = Translate("Go",                         "Вперед")
  val language        = Translate("Language",                   "Язык")
  val loading         = Translate("Loading",                    "Загрузка")
  val naming          = Translate("Name",                       "Название")
  val reload          = Translate("Reload",                     "Перезагрузить")
  val resetToken      = Translate("Reset token",                "Обновить токен")
  val retry           = Translate("Retry",                      "Попробовать еще раз")
  val selectCommit    = Translate("Select a commit",            "Выберите комит")
  val settings        = Translate("Settings",                   "Настройки")
  val theUpdate       = Translate("Update",                     "Обновление")
  val theme           = Translate("Theme",                      "Тема")
  val toUpdate        = Translate("Update",                     "Обновить")
  val tokenCleared    = Translate("Token deleted successfully", "Токен успешно удален")
  val tokenNotCleared = Translate("Couldn't delete token",      "Не удалось удалить токен")
  val tokenNotSaved   = Translate("Couldn't save token",        "Не удалось сохранить токен")
  val tokenSaved      = Translate("Token saved successfully",   "Токен успешно сохранен")
