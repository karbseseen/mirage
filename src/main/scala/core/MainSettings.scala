package core

import atlantafx.base.controls.Tile
import config.{Language, SingleTheme, Theme}
import constant.{Constants, Tr, Translate}
import core.main.MainApp
import fx.ModalVBox
import javafx.beans.InvalidationListener
import javafx.util.StringConverter
import org.kordamp.ikonli.fluentui.FluentUiFilledAL
import org.kordamp.ikonli.javafx.FontIcon
import scalafx.Includes.jfxProperty2sfx
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Pos
import scalafx.scene.control.{ChoiceBox, Label, Separator}
import scalafx.scene.layout.VBox
import scalafx.scene.text.Font

import java.lang


class MainSettings extends VBox with ModalVBox:

  alignment = Pos.Center

  children += new Label:
    text <== Tr.settings
    font = Font(Constants.headingSize)

  children += space

  children += choice(Tr.language):
    new ChoiceBox(ObservableBuffer from Language.values):
      selectionModel().select(Language.config())
      value.addListener((_,_,value) => Language.config() = value)
      converter() = new StringConverter[Language]:
        def toString(language: Language): String = language.name
        def fromString(name: String): Language = Language.values.find(_.name == name).orNull

  children += choice(Tr.theme):
    new ChoiceBox(ObservableBuffer from Theme.values):
      selectionModel().select(Theme.config())
      value.addListener((_,_,value) => Theme.config() = value)

  children += darknessChoice
  Language.config.addListener(_ => children(4) = darknessChoice)
  private def darknessChoice = choice(Tr.brightness):
    class LocalizedDarkness(val value: Theme.Darkness):
      override val toString: String = value.name(Language.config())
    new ChoiceBox(ObservableBuffer from Theme.Darkness.values.map(LocalizedDarkness(_))):
      disable <== Theme.config.map[lang.Boolean](_.isInstanceOf[SingleTheme])
      disable.subscribe: disable =>
        val darkness =
          if (!disable) Theme.Darkness.config()
          else if (Theme.config().asInstanceOf[SingleTheme].isDark) Theme.Darkness.Dark
          else Theme.Darkness.Light
        items().forEach(item => if (item.value == darkness) value() = item)

  children += new Separator

  children += new Tile:
    titleProperty <== Tr.toUpdate
    setAction(FontIcon(FluentUiFilledAL.CHEVRON_RIGHT_24))
    setActionHandler(() => UpdateStage().show())

  children += new Tile:
    titleProperty <== Tr.clearToken
    setActionHandler(() => GithubToken.clear(Some(MainApp.notifications)))
    visible <== GithubToken.property.isNotNull
    managed <== visible


  private def choice(name: Translate)(choiceBox: ChoiceBox[?]) =
    new Tile:
      titleProperty <== name
      setAction(choiceBox)
      setActionHandler: () =>
        choiceBox.requestFocus()
        choiceBox.showing = true
