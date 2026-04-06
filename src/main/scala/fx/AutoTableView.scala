package fx

import config.ColumnWidthConfig
import constant.Translate
import javafx.beans.{property as jfxbp, value as jfxbv}
import javafx.scene.{control as jfxsc, input as jfxsi}
import scalafx.Includes.jfxObservableValue2sfx
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.*


sealed trait AutoColumn[F] extends TableColumnBase[?, F]:
  private[fx] def columnInit(tableName: String, nameOrIndex: Translate | Int): Unit =
    val (name, configName) = nameOrIndex match
      case tr: Translate => (Some(tr), tr.getName)
      case int: Int => (None, s"unnamed-$int")
    name.foreach(text <== _)
    ColumnWidthConfig(tableName, configName, this)
    comparator = null

  def cellText: Nothing = sys.error("Don't call this")
  def cellTextSortable: Nothing = sys.error("Don't call this")
  def cellText_=(factory: F => String): Unit
  def cellTextSortable_=(factory: F => String): Unit =
    cellText = factory
    comparator = Ordering.by(factory)


abstract class AutoTableView[T] extends TableView[T] with AutoTableBase[T]:
  rowFactory = _ => onRowCreate(new TableRow[T])
  protected class Column[F](nameOrIndex: Translate | Int, getter: T => jfxbv.ObservableValue[F])
    extends TableColumn[T, F] with AutoColumn[F]:
    columnInit(tableName, nameOrIndex)
    cellValueFactory = f => getter(f.value)
    def cellText_=(factory: F => String): Unit = cellFactory = (cell, value) => cell.text = factory(value)

abstract class AutoTreeView[T] extends TreeTableView[T] with AutoTableBase[T]:
  rowFactory = _ => onRowCreate(new TreeTableRow[T])
  protected class Column[F](nameOrIndex: Translate | Int, getter: T => jfxbv.ObservableValue[F])
    extends TreeTableColumn[T, F] with AutoColumn[F]:
    columnInit(tableName, nameOrIndex)
    cellValueFactory = _.value.value.flatMap(t => getter(t))
    def cellText_=(factory: F => String): Unit = cellFactory = (cell, value) => cell.text = factory(value)


trait AutoTableBase[T] extends Control:

  def tableName: String
  def selectionModel: ObjectProperty[? <: jfxsc.TableSelectionModel[?]]

  //Deselect on second click
  private var preSelectedIndex = -1
  private def selectedIndex = selectionModel.getValue.getSelectedIndex
  this.addEventFilter(jfxsi.MouseEvent.MOUSE_PRESSED, _ => preSelectedIndex = selectedIndex)
  private[fx] def onRowCreate[Row <: IndexedCell[T]](row: Row): Row =
    row.onMouseClicked = event =>
      if (selectedIndex == preSelectedIndex && selectedIndex >= 0)
        selectionModel.getValue.clearSelection()
        event.consume()
    row

  protected def selfProp: (T & SelfProperty) => jfxbv.ObservableValue[T] =
    getSelfProperty.asInstanceOf[SelfProperty => jfxbv.ObservableValue[T]]


trait SelfProperty:
  val selfProperty: jfxbv.ObservableValue[this.type] = new jfxbp.SimpleObjectProperty(this, getClass.getName, this)

private val getSelfProperty = (_: SelfProperty).selfProperty
