
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.8.2"
lazy val root = (project in file(".")).settings(name := "mirage")
Compile / mainClass := Some("Main")

Global / onChangedBuildSource := ReloadOnSourceChanges

Compile / packageBin / packageOptions += ManifestClassPath.task.value
Compile / compile := (Compile / compile).dependsOn(TrSort.task).value

lazy val trSort = taskKey[Unit]("TrSort")
trSort := { TrSort.task.value }


libraryDependencies ++= Seq(
  "org.kohsuke" % "github-api" % "1.330",
  "org.scalafx" %% "scalafx" % "25.0.2-R37",
  "io.github.mkpaz" % "atlantafx-base" % "2.1.0",
  //"io.github.palexdev" % "materialfx" % "11.17.0",
)
/*libraryDependencies ++= Seq("base", "graphics", "controls")
  .map { libName => "org.openjfx" % s"javafx-$libName" % "25.0.2" classifier os }*/
