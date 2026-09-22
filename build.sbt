import _root_.util.LibInfo._


ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.8.2"
lazy val root = (project in file(".")).settings(name := "mirage")
Compile / mainClass := Some("core.main.Bootstrap")

Global / onChangedBuildSource := ReloadOnSourceChanges

Compile / packageBin / packageOptions += ManifestClassPath.task.value
Compile / compile := (Compile / compile).dependsOn(FieldSort.task).value
Compile / sourceGenerators += ShareCode.task.taskValue

lazy val fieldSort = taskKey[Unit]("FieldSort")
fieldSort := FieldSort.task.value

lazy val shareCode = taskKey[Unit]("ShareCode")
shareCode := ShareCode.task.value


val jlibtorrentVersion = "2.0.12.7"
val lwjglVersion = "3.4.3"
val webrtcAecVersion = "1.0.3"
val ikonliVersion = "12.4.0"

resolvers += "FrostWire Maven" at "https://dl.frostwire.com/maven"

libraryDependencies ++= Seq(
  "org.scalafx" %% "scalafx" % "25.0.2-R37",
  "io.github.mkpaz" % "atlantafx-base" % "2.1.0",
  //"io.github.palexdev" % "materialfx" % "11.17.0",
  "com.frostwire" % "jlibtorrent" % jlibtorrentVersion,
  "com.frostwire" % s"jlibtorrent-${jlibtorrent.platform}" % jlibtorrentVersion % Provided,
  "uk.co.caprica" % "vlcj-javafx" % "1.2.1",
  "org.lwjgl" % "lwjgl" % lwjglVersion,
  "org.lwjgl" % "lwjgl" % lwjglVersion % Provided classifier s"natives-${lwjgl.platform}",
  "org.lwjgl" % "lwjgl-opus" % lwjglVersion,
  ("org.lwjgl" % "lwjgl-opus" % lwjglVersion % Provided classifier s"natives-${lwjglOpus.platform}").exclude("org.lwjgl", "lwjgl"),
  ("cn.enaium.webrtc.aec3" % "webrtc-aec3-kmp-jvm" % webrtcAecVersion).excludeAll(ExclusionRule("cn.enaium.webrtc.aec3")),
  "cn.enaium.webrtc.aec3" % s"webrtc-aec3-kmp-jni-jvm-${webrtcAec.platform}" % webrtcAecVersion % Provided,
  "org.kordamp.ikonli" % "ikonli-javafx" % ikonliVersion,
  "org.kordamp.ikonli" % "ikonli-fluentui-pack" % ikonliVersion,
  "org.virtuslab" %% "scala-yaml" % "0.3.1",
  "org.kohsuke" % "github-api" % "1.330",
  "com.github.javakeyring" % "java-keyring" % "1.0.4",
)
/*libraryDependencies ++= Seq("base", "graphics", "controls")
  .map { libName => "org.openjfx" % s"javafx-$libName" % "25.0.2" classifier os }*/


Compile / packageBin / mappings :=
  (Compile / packageBin / mappings).value
    .filter { case (file, path) => !path.endsWith(".tasty") }

