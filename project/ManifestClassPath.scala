import GetOS.os
import sbt.*
import sbt.Keys.*

import scala.collection.compat.toTraversableLikeExtensionMethods


object ManifestClassPath {
  lazy val task = Def.task {
    val mavenRegex = "https://repo1\\.maven\\.org/maven2/(.*)".r
    val platformRegex = s"(.*)-$os\\.jar".r

    val providedUrls = update.value.configuration(Provided).toList
      .flatMap(_.modules)
      .flatMap(_.artifacts)
      .flatMap(_._1.url)
      .toSet

    val paths = for {
      attr <- (Compile / dependencyClasspath).value
      artifact <- attr.get(AttributeKey[Artifact]("artifact"))
      url <- artifact.url
      if !providedUrls.contains(url)
    } yield url.toString match {
      case mavenRegex(path) => (s"lib/maven2/$path", artifact.classifier.contains(os))
      case invalid => throw new Error(s"Invalid dependency: $invalid")
    }

    val (platformPaths, simplePaths) = paths
      .sortBy(_._1)
      .partitionMap {
        case (simplePath, false) => Right(simplePath)
        case (platformRegex(platformPath), true) => Left(s"$platformPath-platform.jar")
        case (invalid, true) => throw new Error(s"Invalid platform dependency: $invalid")
      }

    Package.ManifestAttributes(
      "Class-Path" -> (platformPaths ++ simplePaths).mkString(" "),
      "Platform-Path-Num" -> platformPaths.size.toString,
    )
  }
}
