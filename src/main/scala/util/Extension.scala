package util

import java.io.{BufferedInputStream, InputStream, PrintStream}
import java.util.jar.JarInputStream
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


extension [T](any: T)
  inline def also(func: T => Unit): T =
    func(any)
    any


extension [T : ClassTag](iterable: IterableOnce[T])
  def toIArray: IArray[T] = IArray.from(iterable)


extension [T](t: Try[T])
  def printError(string: Throwable => String = _.getMessage, out: PrintStream = System.err): Try[T] =
    t match
      case Failure(error) => out.println(string(error)); t
      case Success(_) => t


extension(input: InputStream)
  def buffered = new BufferedInputStream(input)
  def jar = new JarInputStream(input)
