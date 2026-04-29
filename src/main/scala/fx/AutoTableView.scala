package fx

import config.ColumnWidthConfig
import constant.Translate
import fx.AutoCellBase.Factory
import javafx.beans.{binding as jfxbb, property as jfxbp, value as jfxbv}
import javafx.scene.{control as jfxsc, input as jfxsi}
import javafx.util as jfxu
import scalafx.Includes.{jfxObjectProperty2sfx, jfxObservableValue2sfx}
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.*
import scalafx.scene.control.ControlIncludes.jfxIndexedCell2sfx
import util.also

import scala.compiletime.uninitialized
import scala.language.implicitConversions


abstract class AutoTableView[T] extends TableView[T] with AutoTableBase[T]:
  private[fx] type This = jfxsc.TableView[T]
  private[fx] type Cell = jfxsc.TableRow[T]
  private[fx] def cellFactory: ObjectProperty[Callback] = this.rowFactoryProperty
  private[fx] def cellFactory_=(callback: Callback): Unit = this.setRowFactory(callback)
  private[fx] def createCell: Cell & AutoCellBase[T] = new Cell with AutoCellBase[T]

  protected class Column[F](nameOrIndex: Translate | Int, getter: T => jfxbv.ObservableValue[F])
    extends TableColumn[T, F] with AutoColumnBase[F]:
    columnInit(tableName, nameOrIndex)
    cellValueFactory = f => getter(f.value)

    private[fx] type This = jfxsc.TableColumn[T, F]
    private[fx] type Cell = jfxsc.TableCell[T, F]
    private[fx] def createCell: Cell & AutoCellBase[F] = new Cell with AutoCellBase[F]



abstract class AutoTreeView[T] extends TreeTableView[T] with AutoTableBase[T]:
  private[fx] type This = jfxsc.TreeTableView[T]
  private[fx] type Cell = jfxsc.TreeTableRow[T]
  private[fx] def cellFactory: ObjectProperty[Callback] = this.rowFactoryProperty
  private[fx] def cellFactory_=(callback: Callback): Unit = this.setRowFactory(callback)
  private[fx] def createCell: Cell & AutoCellBase[T] = new Cell with AutoCellBase[T]

  protected class Column[F](nameOrIndex: Translate | Int, getter: T => jfxbv.ObservableValue[F])
    extends TreeTableColumn[T, F] with AutoColumnBase[F]:
    columnInit(tableName, nameOrIndex)
    cellValueFactory = _.value.value.flatMap(t => getter(t))

    private[fx] type This = jfxsc.TreeTableColumn[T, F]
    private[fx] type Cell = jfxsc.TreeTableCell[T, F]
    private[fx] def createCell: Cell & AutoCellBase[F] = new Cell with AutoCellBase[F]


sealed trait AutoTableBase[T] extends Control with WithAutoCell[T]:
  def tableName: String
  def selectionModel: ObjectProperty[? <: jfxsc.TableSelectionModel[?]]

  //Deselect on second click
  private var preSelectedIndex = -1
  private def selectedIndex = selectionModel.getValue.getSelectedIndex
  this.addEventFilter(jfxsi.MouseEvent.MOUSE_PRESSED, _ => preSelectedIndex = selectedIndex)
  private def initRow(row: Cell): Unit = row.onMouseClicked = event =>
    if (selectedIndex == preSelectedIndex && selectedIndex >= 0)
      selectionModel.getValue.clearSelection()
      event.consume()

  cellInit(initRow)
  def rowInit(func: Cell => Unit): Unit = cellInit { row => initRow(row); func(row) }
  def rowSet(func: (Cell, T) => Unit): Unit = cellSet(func)
  def rowUnset(func: Cell => Unit): Unit = cellUnset(func)

  protected def selfProp: (T & SelfProperty) => jfxbv.ObservableValue[T] =
    getSelfProperty.asInstanceOf[SelfProperty => jfxbv.ObservableValue[T]]


sealed trait AutoColumnBase[F] extends TableColumnBase[?, F] with WithAutoCell[F]:
  private[fx] def columnInit(tableName: String, nameOrIndex: Translate | Int): Unit =
    val (name, configName) = nameOrIndex match
      case tr: Translate => (Some(tr), tr.getName)
      case int: Int => (None, s"unnamed-$int")
    name.foreach(text <== _)
    ColumnWidthConfig(tableName, configName, this)
    comparator = null

  override def cellInit(func: Cell => Unit):     Unit = super.cellInit(func)
  override def cellSet(func: (Cell, F) => Unit): Unit = super.cellSet(func)
  override def cellUnset(func: Cell => Unit):    Unit = super.cellUnset(func)
  def cellText(factory: F => String): Unit =
    cellSet { (cell, value) => cell.text = factory(value) }
    cellUnset { cell => cell.text = null }
  def cellTextBind(factory: F => jfxbv.ObservableValue[String]): Unit =
    cellSet: (cell, value) =>
      cell.text <== factory(value)
    cellUnset: cell =>
      cell.text.unbind()
      cell.text = null


sealed trait WithAutoCell[F]:
  private[fx] type This
  private[fx] type Cell <: jfxsc.IndexedCell[F]
  private[fx] type Factory = AutoCellBase.Factory[This, Cell, F]
  private[fx] type Callback = jfxu.Callback[This, Cell]
  
  private[fx] def cellFactory: ObjectProperty[Callback]
  private[fx] def cellFactory_=(callback: Callback): Unit
  private[fx] def createCell: Cell & AutoCellBase[F]

  private def update(func: Factory => Factory): Unit =
    val factory = cellFactory() match
      case alreadyFactory: Factory => alreadyFactory
      case _ => new Factory(() => createCell)
    cellFactory = func(factory)
  private[fx] def cellInit(func: Cell => Unit):     Unit = update(_.copy(init  = Option(func)))
  private[fx] def cellSet(func: (Cell, F) => Unit): Unit = update(_.copy(set   = Option(func)))
  private[fx] def cellUnset(func: Cell => Unit):    Unit = update(_.copy(unset = Option(func)))


object AutoCellBase:
  private[fx] case class Factory[Input, Cell <: jfxsc.IndexedCell[F], F](
    createCell: () => Cell & AutoCellBase[F],
    init:   Option[Cell => Unit]      = None,
    set:    Option[(Cell, F) => Unit] = None,
    unset:  Option[Cell => Unit]      = None,
  ) extends jfxu.Callback[Input, Cell]:
    def call(column: Input): Cell = createCell().also: cell =>
      init.foreach(_(cell))
      cell.factory = this

sealed trait AutoCellBase[F] extends jfxsc.IndexedCell[F]:
  private var factory: AutoCellBase.Factory[?, ? >: this.type, F] = uninitialized
  override def updateItem(item: F, empty: Boolean): Unit =
    super.updateItem(item, empty)
    if (empty || item == null) factory.unset.foreach(_(this))
    else factory.set.foreach(_(this, item))


trait SelfProperty:
  val selfProperty: jfxbv.ObservableValue[this.type] = new jfxbp.SimpleObjectProperty(this, getClass.getName, this)

private val getSelfProperty = (_: SelfProperty).selfProperty
