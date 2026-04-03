package core

import atlantafx.base.controls.SelectableTextFlow
import atlantafx.base.theme.Styles
import constant.{Constants, Tr}
import core.main.MainApp
import fx.PropertyInterpolation.b
import fx.{AutoTableView, ErrorView, NotificationBox, SelfProperty}
import javafx.concurrent as jfxc
import javafx.scene.Node
import org.kohsuke.github.{GHArtifact, GHWorkflowRun, GitHub}
import scalafx.Includes.*
import scalafx.collections.ObservableBuffer
import scalafx.concurrent.Task
import scalafx.geometry.Pos.Center
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.control.ScrollPane.ScrollBarPolicy
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.text.{Font, Text, TextFlow}
import scalafx.stage.{Modality, Stage}
import util.*

import java.io.{File, FileOutputStream, PrintWriter, StringWriter}
import java.time.ZoneId
import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Using


private case class Run(value: GHWorkflowRun, artifact: GHArtifact) extends SelfProperty


private[core] class UpdateStage(parent: Stage = MainApp.stage) extends Stage:
  title <== Tr.theUpdate
  initModality(Modality.WindowModal)
  initOwner(parent.scene.value.getWindow)
  scene = new UpdateScene


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

    val x = content
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
      font = new Font(16)

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

  override def succeeded(): Unit =
    val table = new AutoTableView[Run]:
      private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
      columns ++= Seq(
        tableColumn(Tr.branch, _.value.getHeadBranch),
        tableColumn(Tr.naming, _.value.getHeadCommit.getMessage),
        tableColumn(Tr.date, _.value.getCreatedAt.toInstant.atZone(ZoneId.systemDefault).format(formatter)),
      )
      items = ObservableBuffer(getValue*)

    val label = new Label:
      text <== b"${Tr.selectCommit}:"
      hgrow = Priority.Always
      maxWidth = Double.MaxValue
      font = new Font(16)

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
  def call: Unit =
    val tempFile = new File(JavaUtil.jarFile.getAbsolutePath + ".temp")
    try
      run.artifact.download { input =>
        Using(FileOutputStream(tempFile)) { input.transferTo(_) }
      }
    catch case error: Exception =>
      tempFile.delete()
      throw error
    finally
      JavaUtil.lock.release()
      JavaUtil.jarFile.delete()
      tempFile.renameTo(JavaUtil.jarFile)
  override def succeeded(): Unit =
    JavaUtil.restart()
