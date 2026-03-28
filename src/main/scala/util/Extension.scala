package util

import java.io.PrintStream
import scala.util.{Failure, Success, Try}


extension [T](t: Try[T])
  def printError(string: Throwable => String = _.getMessage, out: PrintStream = System.err): Try[T] =
    t match
      case Failure(error) => out.println(string(error)); t
      case Success(_) => t
