package fx

import com.sun.javafx.binding.{FlatMappedBinding, MappedBinding, StringFormatter}
import javafx.beans.binding.StringExpression
import javafx.beans.value.ObservableValue

import java.util.function.Function


object PropertyInterpolation:
  extension (context: StringContext)
    def b(args: ObservableValue[?] | String*): StringExpression =
      val parts = context.parts.zipAll(args, "", "").flatMap { case (a, b) => List(a, b) }
      StringFormatter.concat(parts*)


extension[T] (observable: ObservableValue[T])
  def mapBinding[U](func: Function[? >: T, ? <: U]) = MappedBinding(observable, func)
  def flatMapBinding[U](func: Function[? >: T, ? <: ObservableValue[U]]) = FlatMappedBinding(observable, func)
