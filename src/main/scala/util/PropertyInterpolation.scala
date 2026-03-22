package util

import com.sun.javafx.binding.StringFormatter
import javafx.beans.binding.StringExpression
import javafx.beans.value.ObservableValue


object PropertyInterpolation:
  extension (context: StringContext)
    def b(args: ObservableValue[?] | String*): StringExpression =
      val parts = context.parts.zipAll(args, "", "").flatMap { case (a, b) => List(a, b) }
      StringFormatter.concat(parts*)
