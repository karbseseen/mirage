import atlantafx.base.theme.PrimerLight
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.{PrimaryStage, userAgentStylesheet}
import scalafx.scene.Scene
import scalafx.scene.layout.VBox
import util.{AppBase, Tr}


object MainApp extends JFXApp3 with AppBase:
  override def start(): Unit =
    userAgentStylesheet = new PrimerLight().getUserAgentStylesheet
    stage = new PrimaryStage:
      title <== Tr.appName
      scene = new Scene(new VBox, 800, 500):
        content = new MainMenu
