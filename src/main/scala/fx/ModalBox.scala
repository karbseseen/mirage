package fx

import constant.Constants
import scalafx.geometry.Insets
import scalafx.scene.layout.{Pane, Region, VBox}


sealed trait ModalBox extends Pane:
  style = "-fx-background-color: -color-bg-default"
  maxWidth = Constants.modalWidth
  maxHeight = Region.UsePrefSize
  margin = Insets(Constants.modalMargin)

  protected def space(minHeight: Double = 0): Region = ModalBox.space(minHeight)
  protected def space: Region = ModalBox.space

object ModalBox:
  def space(_minHeight: Double): Region = new Region:
    minHeight = _minHeight
    prefHeight = Constants.modalSpace
  def space: Region = space(Constants.modalPadding)

  def spacing: Region = new Region:
    prefHeight = Constants.modalPadding


class ModalErrorView(error: Throwable) extends ErrorView(error, inset = Constants.modalPadding) with ModalBox

trait ModalVBox extends VBox with ModalBox:
  padding = Insets(Constants.modalPadding)
