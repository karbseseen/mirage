package util.fx

import com.sun.javafx.binding.StringFormatter
import javafx.beans.binding.StringExpression
import javafx.beans.value.ObservableValue
import scalafx.scene.paint.Color
import scalafx.scene.text.{Text, TextFlow}
import util.also

import scala.language.implicitConversions


object PropertyInterpolation:
  extension (context: StringContext)
    def b(args: ObservableValue[?] | String*): StringExpression =
      val parts = context.parts.zipAll(args, "", "").flatMap { case (a, b) => List(a, b) }
      StringFormatter.concat(parts*)


object TextFlowInterpolation:
  implicit def toText(string: String): Text = Text(string)

  extension (text: Text)
    def bold:                 Text = text.also(_.style = "-fx-font-weight: bold")
    def color(color: Color):  Text = text.also(_.fill = Color.Red)

  extension (context: StringContext)
    def flow(args: Text | String*): TextFlow =
      val parts = context.parts.zipAll(args, "", "")
        .flatMap { case (a, b) => List(a, b) }
        .foldLeft[List[Text | List[String]]](Nil):
          case (acc, text: Text)                                  => text :: acc
          case ((accHead: List[String]) :: accTail, str: String)  => (str :: accHead) :: accTail
          case (acc, str: String)                                 => List(str) :: acc
        .foldLeft[List[Text]](Nil):
          case (acc, text: Text) => text :: acc
          case (acc, str: List[String]) => Text(str.reverse.mkString) :: acc
      TextFlow(parts*)
