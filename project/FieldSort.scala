import sbt.*
import sbt.Keys.*

import scala.collection.compat.{toOptionCompanionExtension, toTraversableLikeExtensionMethods}


object FieldSort {

  private case class Entry(sourceFileName: String, objectName: String)
  private val entries = List(
    Entry("constant/Translate.scala", "Tr"),
    Entry("constant/Constants.scala", "Constants"),
  )

  private sealed trait LineBegin
  private case object Val extends LineBegin
  private case object Obj extends LineBegin

  private def prependEmptyLine(lines: List[String]): List[String] =
    lines match {
      case head :: tail => (System.lineSeparator + head) :: tail
      case Nil => Nil
    }

  private def processLines(lines: List[String]) = {
    val groupedLines = lines
      .foldLeft { List.empty[(LineBegin, List[String])] } { case (acc, line) =>
        if (line.isEmpty) acc
        else if (line.startsWith("  val"))    (Val, line :: Nil) :: acc
        else if (line.startsWith("  object")) (Obj, line :: Nil) :: acc
        else acc.head.copy(_2 = line :: acc.head._2) :: acc.tail
      }
      .groupMap
        { case (begin, lines) => (begin, lines.lengthCompare(1) > 0) }
        { case (begin, lines) => lines.reverse.mkString(System.lineSeparator) }

    val valShort  = groupedLines.getOrElse((Val, false), Nil).sorted
    val valLong   = groupedLines.getOrElse((Val, true), Nil).sorted
    val objShort  = groupedLines.getOrElse((Obj, false), Nil).sorted
    val objLong   = groupedLines.getOrElse((Obj, true), Nil).sorted.map(System.lineSeparator + _)

    valShort ::: prependEmptyLine(valLong) ::: prependEmptyLine(objShort) ::: objLong
  }

  private def processEntry(entry: Entry, source: File): Set[File] = {
    import entry.*
    println(s"Sorting $sourceFileName")
    val regex = s"^object $objectName\\W".r
    val (beforeLines, defLine :: afterLinesRaw) = IO.readLines(source).span(regex.findFirstMatchIn(_).isEmpty)
    val afterLines = processLines(afterLinesRaw)
    if (afterLines.isEmpty) throw new Exception(s"Couldn't find object Tr fields in $sourceFileName")
    IO.writeLines(source, beforeLines ::: defLine :: afterLines)
    Set(source)
  }
  
  lazy val task = Def.task[Unit] {
    for ((entry, index) <- entries.zipWithIndex) yield {
      val cache = streams.value.cacheDirectory / s"fieldSort$index"
      val source = (Compile / scalaSource).value / entry.sourceFileName
      val thisFile = (Compile / baseDirectory).value / "project" / "FieldSort.scala"
      FileFunction.cached(cache, FilesInfo.hash) { _ => processEntry(entry, source) } { Set(source, thisFile) }
    }
  }
}
