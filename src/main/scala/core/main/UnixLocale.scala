package core.main

import java.util.Locale
import scala.jdk.CollectionConverters.*


private[main] trait UnixLocale:
  setUnixLocale("LANG", Locale.Category.DISPLAY)
  setUnixLocale("LC_TIME", Locale.Category.FORMAT)


private def setUnixLocale(env: String, category: Locale.Category): Unit = sys.env.get(env)
  .map(_.split("[._]"))
  .filter(_.nonEmpty)
  .flatMap { parts =>
    val locales = Locale.availableLocales.iterator.asScala
      .filter(_.getLanguage == parts.head)
      .toList
    parts.tail.headOption
      .flatMap { country => locales.find(_.getCountry == country) }
      .orElse(locales.headOption)
  }
  .foreach { Locale.setDefault(category, _) }
