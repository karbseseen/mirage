package util.fx

import atlantafx.base.controls as afxbc
import constant.Constants
import scalafx.Includes.jfxSkin2sfxSkin
import scalafx.geometry.Insets
import scalafx.scene.control.Control
import scalafx.scene.layout.{Pane, Region, VBox}

import scala.language.implicitConversions


class ModalPane(override val delegate: afxbc.ModalPane = new afxbc.ModalPane) extends Control(delegate):
  skin = new afxbc.ModalPaneSkin(delegate):
    override def doHide(): Unit =
      super.doHide()
      getSkinnable.setContent(null)
  
object ModalPane:
  implicit def fxModalPane2afx(modalPane: ModalPane): afxbc.ModalPane = modalPane.delegate


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
