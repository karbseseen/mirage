package util

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{TableColumn, TableView}


trait SelfProperty:
  val selfProperty: ObjectProperty[this.type] = new ObjectProperty[this.type](this, getClass.getName, this)

class AutoTableView[S <: SelfProperty] extends TableView[S]:
  protected def tableColumn[F](name: String, getter: S => F): TableColumn[S, S] =
    new TableColumn[S, S]:
      text = name
      cellValueFactory = _.value.selfProperty.asInstanceOf[ObjectProperty[S]]
      cellFactory = (cell, value) => cell.text = getter(value).toString
