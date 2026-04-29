package fx

import atlantafx.base.controls.SelectableTextFlow
import constant.{Constants, Tr}
import scalafx.beans.property.ObjectProperty
import scalafx.scene.Node
import scalafx.scene.control.{Button, ScrollPane}
import scalafx.scene.layout.{HBox, Priority, Region, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.text.{Font, Text, TextFlow}

import java.io.{PrintWriter, StringWriter}


class ErrorView(
  error: Throwable,
  scrollable: Boolean = true,
  inset: Double = Constants.inset,
) extends VBox(Constants.inset):

  private val label = new Text:
    margin = AutoInsets(left = inset, right = inset, top = inset)
    font = Font(Constants.headingSize)
    text <== Tr.wentWrong

  val retryButton: Button = new Button:
    text <== Tr.retry
    managed <== onAction.isNotNull

  val buttons: HBox = new HBox(inset, retryButton):
    margin = AutoInsets(left = inset, right = inset)

  val selectableText: TextFlow =
    val stringWriter = new StringWriter()
    error.printStackTrace(new PrintWriter(stringWriter))
    val text = new Text(stringWriter.toString):
      fill = Color.Red
    new TextFlow(SelectableTextFlow(text)):
      hgrow = Priority.Always
      prefWidth = Region.UseComputedSize
      maxWidth = Double.MaxValue
      padding = AutoInsets(left = inset, right = inset)

  private val resultText: Node =
    if (!scrollable) selectableText
    else new ScrollPane:
      vgrow = Priority.Always
      content = selectableText

  children = Seq[Node](label, buttons, resultText)
