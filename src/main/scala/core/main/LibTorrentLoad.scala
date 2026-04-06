package core.main

import atlantafx.base.controls.RingProgressIndicator
import constant.{Constants, Tr}
import fx.{AutoInsets, ModalBox, ModalErrorView, Worker}
import org.libtorrent4j.LibTorrent
import scalafx.application.Platform
import scalafx.geometry.Pos
import scalafx.scene.control.ControlIncludes.jfxProgressIndicator2sfx
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, VBox}
import scalafx.scene.text.Font
import torrent.TorrentView
import util.{JavaUtil, buffered, jar}

import java.io.File
import java.net.URL
import scala.util.Using


private[main] object LibTorrentLoad:
  def apply(): Unit =
    val ext = JavaUtil.os match
      case JavaUtil.OS.Windows => "dll"
      case JavaUtil.OS.MacOS => "dylib"
      case JavaUtil.OS.Linux => "so"
    val file = new File(JavaUtil.jarFile.getParentFile, s"lib/libtorrent4j.$ext")
    System.setProperty("libtorrent4j.jni.path", file.getAbsolutePath)

    if (!file.exists) new LoadWorker(file).start()
    else MainApp.root.children += new TorrentView


private class LoadWorker(file: File) extends Worker:
  def run(): Unit =
    val progress = showLoading
    try { download(progress) }
    catch case error: Exception => showError(error)
    finally showSuccess()


  private def download(progress: RingProgressIndicator): Unit =
    val os = JavaUtil.os match
      case JavaUtil.OS.Windows => "windows"
      case JavaUtil.OS.MacOS => "macos"
      case JavaUtil.OS.Linux => "linux"
    val arch =
      val sysArch = System.getProperty("os.arch").toLowerCase
      if (List("aarch64", "arm64", "arm").contains(sysArch)) "arm64"
      else if (List("amd64", "x86_64", "x86").contains(sysArch)) "x86_64"
      else sys.error(s"Unknown architecture: $sysArch")
    val version = LibTorrent.libtorrent4jVersion

    val url = s"https://repo1.maven.org/maven2/org/libtorrent4j/libtorrent4j-$os/2.1.0-39/libtorrent4j-$os-$version.jar"
    val fileJarPath = s"lib/$arch/${file.getName}"

    Using(new URL(url).openConnection.getInputStream.buffered.jar) { input =>
      Iterator.continually(input.getNextJarEntry)
        .takeWhile(_ != null)
        .find(_.getName == fileJarPath)
        .map { entry =>
          val size = entry.getSize.toDouble
          def onProgress(read: Long): Unit = Platform.runLater(progress.setProgress(read.toDouble / size))
          JavaUtil.downloadWithProgress(input, file, onProgress)
        }
        .getOrElse(sys.error(s"Couldn't find appropriate file in jar (searched for $fileJarPath)"))
    }


  private def showLoading =
    val label = new Label:
      font = new Font(16)
      text <== Tr.loadingComponents

    val progress = new RingProgressIndicator()

    val row = new HBox(Constants.modalPadding, label, progress):
      alignment = Pos.Center

    val box = new VBox with ModalBox:
      padding = AutoInsets(left = Constants.modalPadding, right = Constants.modalPadding)
      children = Seq(ModalBox.space, row, ModalBox.space)

    ui:
      MainApp.modal.setPersistent(true)
      MainApp.modal.show(box)

    progress

  private def showError(error: Throwable): Unit =
    val box = new ModalErrorView(error):
      maxWidth = 600
      retryButton.onAction = _ => start()
    ui:
      MainApp.modal.show(box)

  private def showSuccess(): Unit =
    ui:
      MainApp.modal.hide(true)
      MainApp.modal.setPersistent(false)
      MainApp.root.children += new TorrentView
