package config

import constant.Constants
import javafx.beans as jfxb
import javafx.beans.property as jfxbp
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.{SplitPane, TableColumnBase}

import scala.collection.mutable 


object ColumnWidthConfig:
  private type WidthMap = mutable.Map[String, mutable.Map[String, Int]]

  private val config = Config.ObjectProp[WidthMap](this, "column-width", mutable.Map.empty)

  def apply(tableName: String, columnName: String, column: TableColumnBase[?, ?]): Unit =
    val map = config.getValue.getOrElseUpdate(tableName, mutable.Map.empty)
    column.prefWidth = map.getOrElse(columnName, Constants.defaultColumnWidth)
    column.width.subscribe(width => map(columnName) = width.intValue)


object SplitPositionConfig:
  private type PosMap = mutable.Map[String, List[Float]]

  private val config = Config.ObjectProp[PosMap](this, "split-position", mutable.Map.empty)

  def apply(name: String, split: SplitPane): Unit =
    for (position, index) <- config.getValue.getOrElseUpdate(name, Nil).zipWithIndex
      do split.setDividerPosition(index, position)

    val listener: jfxb.InvalidationListener = _ => config.getValue(name) = split.dividerPositions.toList.map(_.toFloat)

    split.dividers.onChange: (_, changes) =>
      changes.collect:
        case ObservableBuffer.Add(_, dividers) => dividers.foreach(_.positionProperty.addListener(listener))
        case ObservableBuffer.Remove(_, dividers) => dividers.foreach(_.positionProperty.removeListener(listener))
