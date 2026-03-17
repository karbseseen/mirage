ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.8.2"

lazy val root = (project in file(".")).settings(name := "mirage")

Global / onChangedBuildSource := ReloadOnSourceChanges

Compile / packageBin / packageOptions += {
  val modules = update.value.allModuleReports.toList
    .flatMap(_.artifacts)
    .map { case (artifact, file) => file.getPath -> artifact }
    .toMap

  val mavenRegex = "https://repo1\\.maven\\.org/(.*)".r
  val classPaths = (Compile / dependencyClasspath).value
    .map(_.data.getPath)
    .flatMap(path => modules.getOrElse(path, throw new Exception(s"$path not found")).url)
    .map(_.toString)
    .sorted
    .map {
      case mavenRegex(path) => s"lib/$path"
      case invalid => throw new Error(s"Invalid dependency: $invalid")
    }

  Package.ManifestAttributes("Class-Path" -> classPaths.mkString(" "))
}
