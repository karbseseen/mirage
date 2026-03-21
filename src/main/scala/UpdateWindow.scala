import atlantafx.base.controls.SelectableTextFlow
import atlantafx.base.theme.Styles
import org.kohsuke.github.{GHArtifact, GHRepository, GHWorkflowRun, GitHub}
import scalafx.Includes.*
import scalafx.collections.ObservableBuffer
import scalafx.concurrent.{Service, Task}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.control.ScrollPane.ScrollBarPolicy
import scalafx.scene.layout.{HBox, Priority, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.text.{Font, Text}
import scalafx.stage.{Modality, Stage}
import util.{AutoTableView, SelfProperty, Tr}

import java.io.{PrintWriter, StringWriter}
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.math.Ordering.Implicits.infixOrderingOps


private case class Run(value: GHWorkflowRun, artifact: GHArtifact) extends SelfProperty


class UpdateStage(parent: Stage) extends Stage:
  title <== Tr.update
  initModality(Modality.WindowModal)
  initOwner(parent.scene.value.getWindow)
  scene = new UpdateScene

private class UpdateScene extends Scene(600, 400):

  private lazy val repo = GitHub.connectAnonymously.getRepository("karbseseen/mirage")

  val service = Service(Task {
    implicit val runOrdering: Ordering[Run] = Ordering.by(_.value.getCreatedAt.getTime)

    val allRuns = for {
      run <- repo.queryWorkflowRuns().list().asScala
      artifact <- run.listArtifacts().asScala.find(_.getName == "main-jar")
    } yield Run(run, artifact)

    allRuns
      .groupMapReduce(_.value.getHeadCommit.getId)(identity)(_ max _)
      .values
      .toSeq
  })


  service.onScheduled = _ =>
    val label = new Label:
      text <== Tr.loading
      font = new Font(16)

    val progress = new ProgressIndicator

    progress.prefWidth <== label.height
    progress.prefHeight <== label.height

    root = new StackPane:
      children = new HBox(label, progress):
        spacing <== label.height / 4
        alignment = Pos.Center


  service.onSucceeded = _ =>
    val label = new Label:
      text <== Tr.selectCommit
      alignmentInParent = Pos.CenterLeft
      font = new Font(16)

    val updateButton = new Button:
      text <== Tr.reload
      alignmentInParent = Pos.CenterRight
      styleClass += Styles.SMALL
      onAction = _ => service.restart()

    val table = new AutoTableView[Run]:
      columns ++= Seq(
        tableColumn(Tr.naming, _.value.getHeadCommit.getMessage),
        tableColumn(Tr.date, _.value.getUpdatedAt),
      )
      items = ObservableBuffer(service.getValue*)

    val upperRow = new StackPane:
      margin = Insets(6)
      children = Seq(label, updateButton)

    root = new VBox(upperRow, table)


  service.onFailed = _ =>
    val inset = 10.0

    val stringWriter = new StringWriter()
    val printWriter = new PrintWriter(stringWriter)
    service.getException.printStackTrace(printWriter)

    val retry = new Button:
      this.text <== Tr.retry
      margin = Insets(inset)
      onAction = _ => service.restart()

    val text = new Text(stringWriter.toString):
      fill = Color.Red
    val selectableText = new SelectableTextFlow(text):
      this.padding = Insets(left = inset, right = 0, top = 0, bottom = 0)
    val textScroll = new ScrollPane:
      hbarPolicy = ScrollBarPolicy.Never
      vgrow = Priority.Always
      content = selectableText
    selectableText.prefWidthProperty <== textScroll.width

    root = new VBox(retry, textScroll)


  service.start()
