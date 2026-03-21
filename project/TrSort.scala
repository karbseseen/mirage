import sbt.*
import sbt.Keys.*


object TrSort {
  private val sourceFileName = "util/Translate.scala"

  private def edit(source: File): Set[File] = {
    println(s"Sorting $sourceFileName")
    val regex = "^object Tr\\W".r
    val (beforeLines, defLine :: afterLinesRaw) = IO.readLines(source).span(regex.findFirstMatchIn(_).isEmpty)
    val afterLines = afterLinesRaw.filter(_.nonEmpty).sorted
    if (afterLines.isEmpty) throw new Exception(s"Couldn't find object Tr fields in $sourceFileName")
    IO.writeLines(source, beforeLines ::: defLine :: afterLines)
    Set(source)
  }
  
  lazy val task = Def.task[Unit] {
    val cache = streams.value.cacheDirectory / "trSort"
    val source = (Compile / scalaSource).value / sourceFileName
    val thisFile = (Compile / baseDirectory).value / "project" / "TrSort.scala"
    FileFunction.cached(cache, FilesInfo.hash) { _ => edit(source) } { Set(source, thisFile) }
  }
}
