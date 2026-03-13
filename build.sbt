ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.8.2"

lazy val root = (project in file(".")).settings(name := "mirage")

libraryDependencies += "org.eclipse.aether" % "aether-api" % "1.1.0"

lazy val exportDeps = taskKey[Unit]("Export dependencies")
exportDeps := {
  val file = baseDirectory.value / "target" / "dependencies.txt"
  val value = libraryDependencies.value.mkString("\n")
  IO.write(file, value)
}
