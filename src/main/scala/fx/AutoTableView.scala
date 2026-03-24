package fx

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.{TableColumn, TableView}


trait SelfProperty:
  val selfProperty: ReadOnlyObjectProperty[this.type] = new ReadOnlyObjectProperty(this, getClass.getName, this)

class AutoTableView[S <: SelfProperty] extends TableView[S]:
  protected def tableColumn[F](name: Translate, getter: S => F): TableColumn[S, S] =
    new TableColumn[S, S]:
      text <== name
      cellValueFactory = _.value.selfProperty.asInstanceOf[ReadOnlyObjectProperty[S]]
      cellFactory = (cell, value) => cell.text = getter(value).toString
