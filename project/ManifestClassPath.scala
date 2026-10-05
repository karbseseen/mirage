import _root_.util.LibInfo
import sbt.*
import sbt.Keys.*

import scala.collection.compat.toOptionCompanionExtension


object ManifestClassPath {
  lazy val task = Def.task {
    val urlPrefixRegex = List(
      "https://repo1.maven.org/maven2",
      "https://dl.frostwire.com/maven",
    ).map(_.replace(".", "\\.")).mkString("|")
    val urlRegex = s"(?:$urlPrefixRegex)/(.+)".r

    val providedUrls = update.value.configuration(Provided).toList
      .flatMap(_.modules)
      .flatMap(_.artifacts)
      .flatMap(_._1.url)
      .toSet

    val pathsUnsorted = for {
      attr <- (Compile / dependencyClasspath).value
      artifact <- attr.get(AttributeKey[Artifact]("artifact"))
      url <- artifact.url
    } yield url.toString match {
      case urlStr@urlRegex(path) => (Option.when(!providedUrls.contains(url))(s"lib/$path"), urlStr)
      case invalid => throw new Error(s"Invalid dependency: $invalid")
    }

    implicit val optStrOrdering: Ordering[Option[String]] = Ordering.by { _.fold(1 -> "")(0 -> _) }
    val paths = pathsUnsorted.sorted

    def toManifest(paths: Seq[String]) = paths.map { path =>
      LibInfo.values
        .find(lib => path.contains(lib.urlKeyword))
        .fold(path)(lib => path.replace("-" + lib.platform, "-platform"))
    }.mkString(" ")

    Package.ManifestAttributes(
      "Class-Path" -> toManifest(paths.flatMap(_._1)),
    )
  }
}
