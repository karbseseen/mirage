import _root_.util.JavaPlatform._


ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.8.2"
lazy val root = (project in file(".")).settings(name := "mirage")
Compile / mainClass := Some("core.main.Main")

Global / onChangedBuildSource := ReloadOnSourceChanges

Compile / packageBin / packageOptions += ManifestClassPath.task.value
Compile / compile := (Compile / compile).dependsOn(FieldSort.task).value
Compile / sourceGenerators += ShareCode.task.taskValue

lazy val fieldSort = taskKey[Unit]("FieldSort")
fieldSort := FieldSort.task.value

lazy val shareCode = taskKey[Unit]("ShareCode")
shareCode := ShareCode.task.value

resolvers += "FrostWire Maven" at "https://dl.frostwire.com/maven"
libraryDependencies ++= Seq(
  "org.scalafx" %% "scalafx" % "25.0.2-R37",
  "io.github.mkpaz" % "atlantafx-base" % "2.1.0",
  //"io.github.palexdev" % "materialfx" % "11.17.0",
  "com.frostwire" % "jlibtorrent" % jlibtorrentVersion,
  "com.frostwire" % s"jlibtorrent-$jlibtorrentPlatform" % jlibtorrentVersion % Provided,
  "org.kordamp.ikonli" % "ikonli-javafx" % "12.4.0",
  "org.kordamp.ikonli" % "ikonli-fluentui-pack" % "12.4.0",
  "org.virtuslab" %% "scala-yaml" % "0.3.1",
  "org.kohsuke" % "github-api" % "1.330",
  "com.github.javakeyring" % "java-keyring" % "1.0.4",
)
/*libraryDependencies ++= Seq("base", "graphics", "controls")
  .map { libName => "org.openjfx" % s"javafx-$libName" % "25.0.2" classifier os }*/


Compile / packageBin / mappings :=
  (Compile / packageBin / mappings).value
    .filter { case (file, path) => !path.endsWith(".tasty") }

