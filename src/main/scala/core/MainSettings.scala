package core

import atlantafx.base.controls.{Tile, ToggleSwitch}
import config.{Conf, Language, SingleTheme, Theme}
import constant.{Constants, Tr, Translate}
import core.main.MainApp
import fx.ModalVBox
import javafx.beans.InvalidationListener
import javafx.scene.input.KeyCode
import javafx.util.StringConverter
import org.kordamp.ikonli.fluentui.FluentUiFilledAL
import org.kordamp.ikonli.javafx.FontIcon
import p2p.base.P2p
import p2p.phone.Phone
import scalafx.Includes.{jfxControl2sfx, jfxObjectProperty2sfx, jfxProperty2sfx}
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Pos
import scalafx.scene.control.{CheckBox, ChoiceBox, Label, Separator, TextField}
import scalafx.scene.input.MouseEvent
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
      value <==> Language.config
      converter() = new StringConverter[Language]:
        def toString(language: Language): String = language.name
        def fromString(name: String): Language = Language.values.find(_.name == name).orNull

  children += choice(Tr.theme):
    new ChoiceBox(ObservableBuffer from Theme.values):
      value <==> Theme.config

  children += darknessChoice
  Language.config.addListener(_ => children(4) = darknessChoice)
  private def darknessChoice = choice(Tr.brightness):
    new ChoiceBox(ObservableBuffer from Theme.Darkness.values):
      disable <== Theme.config.map[lang.Boolean](_.isInstanceOf[SingleTheme])
      Theme.config.subscribe: theme =>
        if (!theme.isInstanceOf[SingleTheme]) value <==> Theme.Darkness.config
        else if (Theme.config().asInstanceOf[SingleTheme].isDark)
          value.unbind(Theme.Darkness.config)
          value() = Theme.Darkness.Dark
        else
          value.unbind(Theme.Darkness.config)
          value() = Theme.Darkness.Light
      converter() = new StringConverter[Theme.Darkness]:
        def toString(value: Theme.Darkness): String = value.name(Language.config())
        def fromString(name: String): Theme.Darkness = Theme.Darkness.values.find(_.name(Language.config()) == name).orNull

  children += new Tile:
    private val input = new TextField:
      text() = P2p.roomName()
      focused.subscribe(focus => if (!focus) P2p.roomName() = text())
      onKeyPressed = event =>
        if (event.getCode == KeyCode.ENTER)
          MainSettings.this.requestFocus()
    titleProperty <== Tr.room
    setAction(input)
    setActionHandler(() => input.requestFocus())

  children += new Separator

  children += new Tile:
    private val input = new CheckBox:
      selected <== Phone.enableEncoder
      this.addEventFilter(MouseEvent.MousePressed, _.consume())
      onMouseClicked = _ => Phone.enableEncoderCounter = Phone.enableEncoderCounter.inc
    titleProperty <== Tr.encodeCall
    setAction(input)
    setActionHandler: () =>
      input.requestFocus()
      input.onMouseClicked().handle(null)

  children += new Separator

  children += new Tile:
    titleProperty <== Tr.toUpdate
    setAction(FontIcon(FluentUiFilledAL.CHEVRON_RIGHT_24))
    setActionHandler(() => UpdateStage().show())

  children += new Tile:
    titleProperty <== Tr.clearToken
    setActionHandler(() => GithubToken.clear(Some(MainApp.notifications)))
    this.visible <== GithubToken.property.isNotNull
    this.managed <== this.visible

  children += new Separator

  children += toggleSwitch(Tr.localDiscovery):
    new ToggleSwitch:
      selectedProperty <==> Conf.torrentLocalDiscovery

  children += toggleSwitch(Tr.dhtDiscovery):
    new ToggleSwitch:
      selectedProperty <==> Conf.torrentDhtDiscovery


  private def choice(name: Translate)(choiceBox: ChoiceBox[?]) =
    new Tile:
      titleProperty <== name
      setAction(choiceBox)
      setActionHandler: () =>
        choiceBox.requestFocus()
        choiceBox.showing = true

  private def toggleSwitch(name: Translate)(switch: ToggleSwitch) =
    new Tile:
      titleProperty <== name
      setAction(switch)
      setActionHandler(() => switch.fire())
