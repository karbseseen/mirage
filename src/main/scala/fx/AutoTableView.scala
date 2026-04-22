package fx

import config.ColumnWidthConfig
import constant.Translate
import fx.AutoCellBase.Factory
import javafx.beans.{binding as jfxbb, property as jfxbp, value as jfxbv}
import javafx.scene.{control as jfxsc, input as jfxsi}
import javafx.util as jfxu
import scalafx.Includes.jfxObservableValue2sfx
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.*
import scalafx.scene.control.ControlIncludes.jfxIndexedCell2sfx
import util.also

import scala.compiletime.uninitialized
import scala.language.implicitConversions


abstract class AutoTableView[T] extends TableView[T] with AutoTableBase[T]:
  rowFactory = _ => onRowCreate(new TableRow[T])

  protected class Column[F](nameOrIndex: Translate | Int, getter: T => jfxbv.ObservableValue[F])
    extends TableColumn[T, F] with AutoColumnBase[F]:
    columnInit(tableName, nameOrIndex)
    cellValueFactory = f => getter(f.value)

    private[fx] type Java = jfxsc.TableColumn[T, F]
    private[fx] type JavaCell = jfxsc.TableCell[T, F]
    private[fx] def createCell: JavaCell & AutoCellBase[F] = new JavaCell with AutoCellBase[F]



abstract class AutoTreeView[T] extends TreeTableView[T] with AutoTableBase[T]:
  rowFactory = _ => onRowCreate(new TreeTableRow[T])

  protected class Column[F](nameOrIndex: Translate | Int, getter: T => jfxbv.ObservableValue[F])
    extends TreeTableColumn[T, F] with AutoColumnBase[F]:
    columnInit(tableName, nameOrIndex)
    cellValueFactory = _.value.value.flatMap(t => getter(t))

    private[fx] type Java = jfxsc.TreeTableColumn[T, F]
    private[fx] type JavaCell = jfxsc.TreeTableCell[T, F]
    private[fx] def createCell: JavaCell & AutoCellBase[F] = new JavaCell with AutoCellBase[F]


sealed trait AutoTableBase[T] extends Control:

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


sealed trait AutoColumnBase[F] extends TableColumnBase[?, F]:
  private[fx] def columnInit(tableName: String, nameOrIndex: Translate | Int): Unit =
    val (name, configName) = nameOrIndex match
      case tr: Translate => (Some(tr), tr.getName)
      case int: Int => (None, s"unnamed-$int")
    name.foreach(text <== _)
    ColumnWidthConfig(tableName, configName, this)
    comparator = null

  private[fx] type Java <: jfxsc.TableColumnBase[?, F]
  private[fx] type JavaCell <: jfxsc.IndexedCell[F]
  private[fx] type CellFactory = AutoCellBase.Factory[Java, JavaCell, F]
  private type CellCallback = jfxu.Callback[Java, JavaCell]
  def cellFactory: ObjectProperty[CellCallback]
  def cellFactory_=(callback: CellCallback): Unit
  private[fx] def createCell: JavaCell & AutoCellBase[F]
  private def updateCellFactory(func: CellFactory => CellFactory): Unit =
    val factory = cellFactory.value match
      case alreadyFactory: CellFactory => alreadyFactory
      case _ => new CellFactory(() => createCell)
    cellFactory = func(factory)
  def cellInit(func: JavaCell => Unit):     Unit = updateCellFactory(_.copy(init  = Option(func)))
  def cellSet(func: (JavaCell, F) => Unit): Unit = updateCellFactory(_.copy(set   = Option(func)))
  def cellUnset(func: JavaCell => Unit):    Unit = updateCellFactory(_.copy(unset = Option(func)))

  def cellText(factory: F => String): Unit =
    cellSet { (cell, value) => cell.text = factory(value) }
    cellUnset { cell => cell.text = null }
  def cellTextBind(factory: F => jfxbv.ObservableValue[String]): Unit =
    cellSet: (cell, value) =>
      cell.text <== factory(value)
    cellUnset: cell =>
      cell.text.unbind()
      cell.text = null


object AutoCellBase:
  private[fx] case class Factory[Col <: jfxsc.TableColumnBase[?, F], Cel <: jfxsc.IndexedCell[F], F](
    createCell: () => Cel & AutoCellBase[F],
    init:   Option[Cel => Unit]      = None,
    set:    Option[(Cel, F) => Unit] = None,
    unset:  Option[Cel => Unit]      = None,
  ) extends jfxu.Callback[Col, Cel]:
    def call(column: Col): Cel = createCell().also: cell =>
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
