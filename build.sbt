import scala.collection.compat.toTraversableLikeExtensionMethods


ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.8.2"
lazy val root = (project in file(".")).settings(name := "mirage")
Compile / mainClass := Some("Main")
Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val os = sys.props.get("os").getOrElse {
  val sysOs = System.getProperty("os.name").toLowerCase
  if (sysOs.contains("win")) "win"
  else if (sysOs.contains("mac")) "mac"
  else if (sysOs.contains("nix") || sysOs.contains("nux") || sysOs.contains("aix")) "linux"
  else throw new Exception("Unknown OS, you should provide -Dos=win|mac|linux")
}

libraryDependencies ++= Seq(
  "org.kohsuke" % "github-api" % "1.330",
  "org.scalafx" %% "scalafx" % "25.0.2-R37",
  "io.github.mkpaz" % "atlantafx-base" % "2.1.0",
  //"io.github.palexdev" % "materialfx" % "11.17.0",
)
/*libraryDependencies ++= Seq("base", "graphics", "controls")
  .map { libName => "org.openjfx" % s"javafx-$libName" % "25.0.2" classifier os }*/

Compile / packageBin / packageOptions += {
  val mavenRegex = "https://repo1\\.maven\\.org/maven2/(.*)".r
  val platformRegex = s"(.*)-$os\\.jar".r

  val paths = for {
    attr <- (Compile / dependencyClasspath).value
    artifact <- attr.metadata.get(AttributeKey[Artifact]("artifact"))
    url <- artifact.url
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
