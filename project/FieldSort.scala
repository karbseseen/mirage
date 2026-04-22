import sbt.*
import sbt.Keys.*

import scala.collection.compat.{toOptionCompanionExtension, toTraversableLikeExtensionMethods}


object FieldSort {
  private val fieldStartRegex = "^ {2}(val|object)".r

  private case class Entry(sourceFileName: String, objectName: String)
  private val entries = List(
    Entry("constant/Translate.scala", "Tr"),
    Entry("constant/Constants.scala", "Constants"),
  )

  private implicit class EitherGet[T](either: Either[T, T]) {
    def get: T = either.fold(identity, identity)
  }

  private def processLines(lines: List[String]) =  {
    val (short, long) = lines
      .foldLeft(List.empty[Either[String, String]]) { case (acc, line) =>
        if (line.isEmpty) acc
        else if (fieldStartRegex.findFirstMatchIn(line).nonEmpty) Left(line) :: acc
        else Right(acc.head.get + System.lineSeparator + line) :: acc.tail
      }
      .partitionMap(identity)
    short.sorted ::: long.sorted.map(System.lineSeparator + _)
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
