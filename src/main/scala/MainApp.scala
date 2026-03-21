import atlantafx.base.theme.PrimerLight
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.{PrimaryStage, userAgentStylesheet}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.VBox
import util.Tr


object MainApp extends JFXApp3:
  override def start(): Unit =
    userAgentStylesheet = new PrimerLight().getUserAgentStylesheet
    stage = new PrimaryStage:
      title <== Tr.appName
      scene = new Scene(new VBox, 800, 500):
        content = new MainMenu
