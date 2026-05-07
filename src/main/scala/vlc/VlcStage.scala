package vlc

import javafx.beans.binding.Bindings
import javafx.beans.property.{SimpleDoubleProperty, SimpleObjectProperty}
import javafx.geometry.Rectangle2D
import scalafx.Includes.jfxNumberBinding2sfx
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.image.ImageView
import scalafx.scene.layout.{Background, BackgroundFill, CornerRadii, StackPane}
import scalafx.scene.paint.Color
import scalafx.stage.Stage
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer


private class VlcStage(media: VlcMedia) extends Stage:

  fullScreenExitHint = ""

  val factory = new MediaPlayerFactory
  val player: EmbeddedMediaPlayer = factory.mediaPlayers.newEmbeddedMediaPlayer
  val pauses = new Pauses(player)
  onHiding = _ =>
    media.shutdown()
    player.release()
    factory.release()

  val root: StackPane = new StackPane:
    background = Background.fill(Color.Black)
    onMouseClicked = event => if (event.getClickCount == 2) fullScreen = !fullScreen()

  val videoSize = SimpleObjectProperty(this, "videoSize", (1.0, 1.0))
  val videoCropCoef = SimpleDoubleProperty(this, "videoCropCoef")
  val imageView: ImageView = new ImageView:
    fitWidth <== root.width
    fitHeight <== root.height
    preserveRatio = true
    player.videoSurface.set(ImageViewVideoSurface(this))
    viewport <== Bindings.createObjectBinding(
      () =>
        val (videoWidth, videoHeight) = videoSize.get
        val videoRatio = videoWidth / videoHeight
        val windowRatio = root.width() / root.height()
        if (videoRatio > windowRatio)
          val videoCropWidth = videoHeight * windowRatio
          Rectangle2D(
            (videoWidth - videoCropWidth) / 2 * videoCropCoef.get,
            0,
            videoWidth * (1 - videoCropCoef.get) + videoCropWidth * videoCropCoef.get,
            videoHeight,
          )
        else
          val videoCropHeight = videoWidth / windowRatio
          Rectangle2D(
            0,
            (videoHeight - videoCropHeight) / 2 * videoCropCoef.get,
            videoWidth,
            videoHeight * (1 - videoCropCoef.get) + videoCropHeight * videoCropCoef.get,
          ),
      videoSize, videoCropCoef, root.width, root.height
    )

  val controls: Controls = new Controls(this)

  val loading: StackPane = new StackPane:
    private val rootSize = Bindings.createDoubleBinding(
      () => root.width() min root.height() min 750,
      root.width, root.height
    )
    private val size = rootSize * 0.25
    maxWidth <== size
    maxHeight <== size
    alignmentInParent = Pos.Center
    visible = false
    background = Background(Array(BackgroundFill(
      Color.White,
      CornerRadii(100, asPercent = true),
      Insets.Empty,
    )))
    children += new ImageView("/vlc/loading.gif"):
      private val size = rootSize * 0.125
      fitWidth <== size
      fitHeight <== size
      preserveRatio = true

  scene = new Scene(root, 800, 600):
    content = imageView :: loading :: controls :: controls.extraParts

  applyControlHide(this)

  show()
  player.events.addMediaPlayerEventListener(VlcHandler(this))
  player.media.play(media)


object VlcStage:
  def apply(media: VlcMedia): Stage = new VlcStage(media)


trait VlcMedia extends CallbackMedia:
  def shutdown(): Unit
