package util.fx

import atlantafx.base.util.Animations
import com.sun.javafx.binding.{FlatMappedBinding, MappedBinding}
import javafx.animation.KeyValue
import javafx.beans.value.{ObservableValue, WritableValue}
import javafx.scene.image.Image
import javafx.util.Duration
import scalafx.Includes.jfxProperty2sfx
import scalafx.application.Platform
import scalafx.stage.Stage

import java.util.function.Function
import scala.language.implicitConversions
import scala.util.Try


def ensureRunLater(task: => Unit): Unit =
  if (Platform.isFxApplicationThread) task
  else Platform.runLater(task)


extension (stage: Stage)
  def setIcon(): Unit =
    stage.icons.addAll:
      Iterator.unfold(1): index =>
        Try(Image(s"icon/icon-$index.png")).toOption.map((_, index + 1))


extension[T] (observable: ObservableValue[T])
  def mapBinding[U](func: Function[? >: T, ? <: U]) = MappedBinding(observable, func)
  def flatMapBinding[U](func: Function[? >: T, ? <: ObservableValue[U]]) = FlatMappedBinding(observable, func)


object JfxSuperBind:
  extension[T <: AnyRef, P <: javafx.beans.property.Property[T]] (observable: ObservableValue[P])
    def <~=(value: ObservableValue[? <: T]): Unit =
      observable.addListener: (_, oldProperty, newProperty) =>
        Option(oldProperty).foreach(_.unbind())
        Option(newProperty).foreach(_ <== value)

object SfxSuperBind:
  extension[T <: AnyRef, P <: scalafx.beans.property.Property[?, T]] (observable: ObservableValue[P])
    def <~=(value: ObservableValue[? <: T]): Unit =
      observable.addListener: (_, oldProperty, newProperty) =>
        Option(oldProperty).foreach(_.unbind())
        Option(newProperty).foreach(_ <== value)


implicit inline def duration(value: Int): Duration = Duration(value)
inline def easeValue[T](target: WritableValue[T], endValue: T): KeyValue = KeyValue(target, endValue, Animations.EASE)
