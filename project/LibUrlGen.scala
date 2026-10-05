import _root_.util.LibInfo
import sbt.*
import sbt.Keys.*

import scala.collection.compat.toTraversableLikeExtensionMethods


object LibUrlGen {
  lazy val task = Def.task[Seq[File]] {
    val cacheDirectory = streams.value.cacheDirectory
    val cache = cacheDirectory / "LibUrlGen"
    val buildSbt = (Compile / baseDirectory).value / "build.sbt"
    val thisFile = (Compile / baseDirectory).value / "project" / "LibUrlGen.scala"
    val defaultTarget = (Compile / resourceManaged).value / "libUrl.txt"
    val specialTarget = (Compile / resourceManaged).value / "libUrlSpecial.txt"

    val updateReport = update.value
    val classpath = (Compile / dependencyClasspath).value

    val cached = FileFunction.cached(cache, FilesInfo.lastModified) { _ =>
      println(s"Updating ${defaultTarget.name} and ${specialTarget.name}")

      val providedUrls = updateReport.configuration(Provided).toList
        .flatMap(_.modules)
        .flatMap(_.artifacts)
        .flatMap(_._1.url)
        .toSet

      val all = for {
        attr <- classpath
        artifact <- attr.get(AttributeKey[Artifact]("artifact"))
        url <- artifact.url
      } yield {
        val raw = url.toString
        val str = LibInfo.values
          .find(lib => raw.contains(lib.urlKeyword))
          .fold(raw)(lib => raw.replace("-" + lib.platform, "-platform"))
        (str, providedUrls.contains(url))
      }

      val (notProvided, provided) = all.partitionMap {
        case (url, false) => Left(url)
        case (url, true) => Right(url)
      }

      IO.write(defaultTarget, notProvided.sorted.mkString("\n"))
      IO.write(specialTarget, provided.sorted.mkString("\n"))
      Set(defaultTarget, specialTarget)
    }

    cached(Set(buildSbt, thisFile)).toSeq
  }
}
