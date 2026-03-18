import atlantafx.base.theme.PrimerLight
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.{PrimaryStage, userAgentStylesheet}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.VBox


object MainApp extends JFXApp3:
  override def start(): Unit =
    userAgentStylesheet = new PrimerLight().getUserAgentStylesheet
    stage = new PrimaryStage:
      title = "Mirage"
      scene = new Scene(new VBox, 800, 500):
        content = createMenu

  private def createMenu: MenuBar =
    val updateItem = new MenuItem("Update"):
      onAction = _ => new UpdateStage(stage).show()

    val fileMenu = new Menu("File"):
      items = Seq(updateItem)

    new MenuBar:
      menus = Seq(fileMenu)
