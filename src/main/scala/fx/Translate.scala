package fx

import javafx.beans.property.{ReadOnlyStringProperty, SimpleObjectProperty, SimpleStringProperty}

import scala.annotation.tailrec
import scala.language.implicitConversions


enum Language(val name: String, val default: Language | Null = null):
  case en extends Language("English")
  case ru extends Language("Русский", en)

object Language:
  val current = new SimpleObjectProperty[Language](this, "Language", Language.en)


case class Translate(
  en: String,
  ru: String = "",
):
  @tailrec final def apply(language: Language): String =
    val value = productElement(language.ordinal).asInstanceOf[String]
    if value.nonEmpty then value
    else apply(language.default)

  private val mutableProperty = new SimpleStringProperty(this, en)
  def property: ReadOnlyStringProperty = mutableProperty
  Language.current.subscribe { lang => if (lang != null) mutableProperty.set(apply(lang)) }

object Translate:
  implicit def asProperty(translate: Translate): ReadOnlyStringProperty = translate.property
