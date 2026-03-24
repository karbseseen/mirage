import MainApp.stage
import constant.Tr
import fx.Language
import scalafx.geometry.Pos
import scalafx.scene.control.{Menu, MenuBar, MenuItem}


private def settingsMenu =
  val languageValues = Language.values.map { language =>
    new MenuItem(language.name):
      onAction = _ => Language.current.set(language)
  }
  
  val language = new Menu:
    text <== Tr.language
    items = languageValues
  
  val update = new MenuItem:
    text <== Tr.toUpdate
    onAction = _ => new UpdateStage(stage).show()

  val clearToken = new MenuItem:
    text <== Tr.clearToken
    visible <== GithubToken.property.isNotNull
    onAction = _ => GithubToken.clear(Some(MainApp.notifications))

  new Menu:
    text <== Tr.settings
    items = Seq(language, update, clearToken)


class MainMenu extends MenuBar:
  menus = Seq(settingsMenu)
  alignmentInParent = Pos.TopCenter
