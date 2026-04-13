import sbt.*
import sbt.Keys.*


object ShareCode {
  private def paths = List(
    "util/JavaPlatform.java",
  )

  lazy val task = Def.task[Seq[File]] {
    val cacheDirectory = streams.value.cacheDirectory
    val thisFile = (Compile / baseDirectory).value / "project" / "ShareCode.scala"
    for (path <- paths) yield {
      val cache = cacheDirectory / path
      val source = (Compile / baseDirectory).value / "project" / path
      val target = (Compile / sourceManaged).value / path
      FileFunction.cached(cache, FilesInfo.lastModified) { _ =>
        println(s"Sharing $path")
        IO.copy(Some(source, target))
      } { Set(source, thisFile) }
      target
    }
  }
}
