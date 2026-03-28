import MainApp.stage
import atlantafx.base.theme as afxbt
import config.Theme.given_Config_Theme
import config.{Config, Language, Theme}
import constant.Tr
import scalafx.beans.BeanIncludes.jfxProperty2sfx
import scalafx.geometry.Pos
import scalafx.scene.control.{Menu, MenuBar, MenuItem, RadioMenuItem, ToggleGroup}


private def settingsMenu =
  val language = new Menu:
    private val group = new ToggleGroup
    text <== Tr.language
    items = Language.values.map { language =>
      new RadioMenuItem(language.name):
        toggleGroup = group
        if (language == Config[Language].value) selected = true
        onAction = _ => Config[Language].value = language
    }

  val theme = new Menu:
    private val group = new ToggleGroup
    text <== Tr.theme
    items = Theme.map.values.toList.sortBy(_.getName).map { theme =>
      new RadioMenuItem(theme.getName):
        toggleGroup = group
        if (theme == Config[afxbt.Theme].value) selected = true
        onAction = _ => Config[afxbt.Theme].setValue(theme)
    }
  
  val update = new MenuItem:
    text <== Tr.toUpdate
    onAction = _ => new UpdateStage(stage).show()

  val clearToken = new MenuItem:
    text <== Tr.clearToken
    visible <== GithubToken.property.isNotNull
    onAction = _ => GithubToken.clear(Some(MainApp.notifications))

  new Menu:
    text <== Tr.settings
    items = Seq(language, theme, update, clearToken)


class MainMenu extends MenuBar:
  menus = Seq(settingsMenu)
  alignmentInParent = Pos.TopCenter
