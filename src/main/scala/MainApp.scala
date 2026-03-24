import atlantafx.base.theme.PrimerLight
import constant.Tr
import fx.NotificationBox
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.{PrimaryStage, userAgentStylesheet}
import scalafx.scene.Scene
import scalafx.scene.layout.StackPane

import java.util.Locale
import scala.jdk.CollectionConverters.*


object MainApp extends JFXApp3:
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

  setUnixLocale("LANG", Locale.Category.DISPLAY)
  setUnixLocale("LC_TIME", Locale.Category.FORMAT)


  val notifications = new NotificationBox

  override def start(): Unit =
    userAgentStylesheet = new PrimerLight().getUserAgentStylesheet
    stage = new PrimaryStage:
      title <== Tr.appName
      scene = new Scene(new StackPane, 800, 500):
        content = new MainMenu :: notifications :: Nil
