package fx

import constant.Constants
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.layout.{Pane, Region}


trait ModalBox extends Pane:
  style = "-fx-background-color: -color-bg-default"
  maxWidth = Constants.modalWidth
  maxHeight = Region.UsePrefSize
  margin = Insets(Constants.modalMargin)
  padding = Insets(Constants.modalPadding)
  
  protected def space(minHeight: Double = 0): Region = ModalBox.space(minHeight)


object ModalBox:
  def space(_minHeight: Double = 0): Region = new Region:
    minHeight = _minHeight
    prefHeight = Constants.modalSpace
