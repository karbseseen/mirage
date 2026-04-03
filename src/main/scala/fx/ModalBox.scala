package fx

import constant.Constants
import scalafx.Includes.jfxPane2sfx
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.layout.{Pane, Region}


trait ModalBox extends Pane:
  style = "-fx-background-color: -color-bg-default"
  maxWidth = Constants.modalWidth
  maxHeight = Region.UsePrefSize
  margin = Insets(Constants.modalMargin)
  
  protected def space(minHeight: Double = 0): Region = ModalBox.space(minHeight)


class ModalErrorView(error: Throwable) extends ErrorView(error, inset = Constants.modalPadding) with ModalBox


object ModalBox:
  def space(_minHeight: Double): Region = new Region:
    minHeight = _minHeight
    prefHeight = Constants.modalSpace
  def space: Region = space(Constants.modalPadding)
