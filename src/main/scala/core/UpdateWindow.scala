package core

import atlantafx.base.theme.Styles
import constant.{Constants, Tr}
import core.boot.JarUpdater
import core.boot.JarUpdater.deleteFileArgPrefix
import core.main.MainApp
import fx.PropertyInterpolation.b
import fx.{AutoTableView, ErrorView, NotificationBox, SelfProperty}
import javafx.concurrent as jfxc
import javafx.scene.Node
import org.kohsuke.github.{GHArtifact, GHWorkflowRun, GitHub}
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.collections.ObservableBuffer
import scalafx.concurrent.Task
import scalafx.geometry.Pos.Center
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.control.ScrollPane.ScrollBarPolicy
import scalafx.scene.layout.*
import scalafx.scene.text.{Font, Text, TextFlow}
import scalafx.stage.{Modality, Stage}
import util.*

import java.io.{File, FileOutputStream}
import java.time.ZoneId
import java.time.format.{DateTimeFormatter, FormatStyle}
import java.util.jar.{Attributes, Manifest}
import java.util.zip.ZipEntry
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Using


private case class Run(value: GHWorkflowRun, artifact: GHArtifact) extends SelfProperty


private[core] class UpdateStage extends Stage:
  title <== Tr.theUpdate
  initModality(Modality.WindowModal)
  initOwner(MainApp.stage.scene.value.getWindow)
  scene = new UpdateScene
  icons.addAll(fx.getIconImages)


private class UpdateScene extends Scene(new StackPane, 600, 400):
  val notifications = new NotificationBox
  content = Seq(new Region, notifications)
  def mainView: Node = content(0)
  def mainView_=(view: Node): Unit = content.set(0, view)

  private def setToken(token: String): Unit =
    GithubToken.set(token, Some(notifications))
    new InfoService(this, token).start()

  private def setTokenView(): Unit =
    val input = new TextField:
      hgrow = Priority.Always
      focusTraversable = false
      promptText = Constants.githubToken
    val enter = new Button:
      this.text <== Tr.go
      disable <== input.text.isEmpty
      onAction = _ => setToken(input.getText)

    val text = new Text:
      this.text <== b"${Tr.createToken}: "
    val link = new Hyperlink(Constants.createTokenLinkText):
      onMouseClicked = _ => MainApp.hostServices.showDocument(Constants.createTokenLink)
    val textFlow = new TextFlow(text, link.delegate)

    mainView = new ScrollPane:
      padding = Insets(Constants.inset)
      hbarPolicy = ScrollBarPolicy.Never
      content = new VBox(Constants.inset, new HBox(Constants.inset, input, enter), textFlow)
      textFlow.prefWidth <== width - Constants.inset * 2

  def resetTokenButton: Button = new Button:
    text <== Tr.resetToken
    onMouseClicked = _ => setTokenView()

  GithubToken.get match
    case Some(token) => new InfoService(this, token).start()
    case None => setTokenView()


private abstract class UpdateService[T](scene: UpdateScene) extends jfxc.Service[T]:
  def call: T
  override protected def succeeded(): Unit

  protected def createTask: jfxc.Task[T] = Task(call)

  override def scheduled(): Unit =
    val label = new Label:
      text <== b"${Tr.loading} "
      font = Font(Constants.heading2Size)

    val progress = new ProgressIndicator:
      prefWidth <== label.height
      prefHeight <== label.height

    scene.mainView = new HBox(label, progress):
      alignment = Pos.Center

  override def failed(): Unit =
    scene.mainView = new ErrorView(getException, scrollable = true):
      retryButton.onAction = _ => restart()
      buttons.children += UpdateService.this.scene.resetTokenButton


private class InfoService(scene: UpdateScene, token: String) extends UpdateService[Seq[Run]](scene):
  def call: Seq[Run] =
    implicit val runOrdering: Ordering[Run] = Ordering.by(_.value.getCreatedAt.getTime)
    val allRuns = for {
      run <- GitHub.connectUsingOAuth(token).getRepository("karbseseen/mirage").queryWorkflowRuns().list().asScala
      artifact <- run.listArtifacts().asScala.find(_.getName.endsWith(".jar"))
    } yield Run(run, artifact)
    allRuns
      .groupMapReduce(_.value.getHeadCommit.getId)(identity)(_ max _)
      .values
      .toSeq
      .sorted(using runOrdering.reverse)

  override def succeeded(): Unit =
    val table = new AutoTableView[Run]:
      def tableName: String = "update-commit"
      private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
      columns ++= Seq(
        new Column(Tr.branch, selfProp) { cellText(_.value.getHeadBranch) },
        new Column(Tr.naming, selfProp) { cellText(_.value.getHeadCommit.getMessage) },
        new Column(Tr.date, selfProp) { cellText(_.value.getCreatedAt.toInstant.atZone(ZoneId.systemDefault).format(formatter)) },
      )
      items = ObservableBuffer(getValue*)

    val label = new Label:
      text <== b"${Tr.selectCommit}:"
      hgrow = Priority.Always
      maxWidth = Double.MaxValue
      font = Font(Constants.heading2Size)

    val reloadButton = new Button:
      styleClass ++= Styles.WARNING :: Styles.BUTTON_OUTLINED :: Nil
      text <== Tr.reload
      onAction = _ => restart()

    val updateButton = new Button:
      styleClass += Styles.ACCENT
      disable <== table.selectionModel.selectInteger("selectedIndex").isEqualTo(-1)
      text <== Tr.toUpdate
      onAction = _ => new DownloadService(InfoService.this.scene, table.getSelectionModel.getSelectedItem).start()

    val upperRow = new HBox(Constants.inset, label, reloadButton, updateButton):
      margin = Insets(Constants.inset)
      alignment = Center

    scene.mainView = new VBox(upperRow, table)


private class DownloadService(scene: UpdateScene, run: Run) extends UpdateService[Unit](scene):

  private val thisJar = JavaUtil.getJarFile
  private val newJar = File(thisJar.getAbsolutePath + ".temp")
  private val updaterJar =
    val updaterJarNamePrefix = thisJar.getName match
      case s"$prefix.jar" => prefix
      case name           => name
    File(thisJar.getParentFile, s"$updaterJarNamePrefix-updater.jar")

  def call: Unit =
    try work()
    catch case error: Exception =>
      newJar.delete()
      updaterJar.delete()
      throw error

  override def succeeded(): Unit =
    Platform.exit()

  private def work(): Unit =
    run.artifact.download: input =>
      Using(FileOutputStream(newJar)):
        input.transferTo(_)

    val updateCls = classOf[JarUpdater].getName
    val updaterPath = updateCls.replace('.', '/') + ".class"

    val manifest = new Manifest
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    manifest.getMainAttributes.put(Attributes.Name.MAIN_CLASS, updateCls)

    Using.resources(
      getClass.getResourceAsStream("/" + updaterPath),
      FileOutputStream(updaterJar).jar(manifest),
    ): (input, output) =>
      if (input == null) throw NullPointerException(s"Couldn't find source $updaterPath")
      output.putNextEntry(new ZipEntry(updaterPath))
      input.transferTo(output)

    val currentCmd = CurrentCmd()
    val updaterCmd =
      currentCmd.head ::
      "-jar" ::
      updaterJar.getAbsolutePath ::
      JarUpdater.getMyPid ::
      thisJar.getAbsolutePath ::
      newJar.getAbsolutePath ::
      currentCmd :::
      s"$deleteFileArgPrefix${updaterJar.getAbsolutePath}" ::
      Nil

    MainApp.shutdownHook:
      Runtime.getRuntime.exec(updaterCmd.toArray)
