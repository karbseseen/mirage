import MainApp.stage
import scalafx.scene.control.{Menu, MenuBar, MenuItem}
import util.{Language, Tr}


private def settingsMenu =
  val languageValues = Language.values.map { language =>
    new MenuItem(language.name):
      onAction = _ => Language.current.value = language
  }
  
  val language = new Menu:
    text <== Tr.language
    items = languageValues
  
  val update = new MenuItem:
    text <== Tr.update
    onAction = _ => new UpdateStage(stage).show()

  new Menu:
    text <== Tr.settings
    items = Seq(language, update)


class MainMenu extends MenuBar:
  menus = Seq(settingsMenu)
