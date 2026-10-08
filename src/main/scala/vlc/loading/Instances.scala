package vlc.loading

import scalafx.animation.Interpolator.Linear
import scalafx.animation.{Animation, RotateTransition}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.image.ImageView
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.util.Duration


private var instances: List[Impl] = Nil

val _ = loadingRoundImage(10, "/loading/handicapped1.gif", 0.6)
val _ = loadingRoundImage(4, "/loading/handicapped2.gif", 0.75)
val _ = loadingRoundImage(10, "/loading/guy1.gif", 0.8)
val _ = loadingRoundImage(7, "/loading/fight.gif", 0.67)
val _ = loadingRoundImage(6, "/loading/dog1.gif", 0.85)
val _ = loadingRoundImage(10, "/loading/bird1.gif", 0.89)
val _ = loadingRoundImage(7, "/loading/bird2.gif", 0.7, bgColor = Color.web("#53c3fb"), borderColor = Color.White)
val _ = loadingRoundImage(10, "/loading/bird4.gif", 0.72, bgColor = Color.web("#a9c459"), borderColor = Color.web("#7c6c66"))
val _ = loadingRoundImage(10, "/loading/bird5.gif", 0.8, bgColor = Color.web("#a9c459"), borderColor = Color.web("#4d5928"))

val _ = loadingRectImage(10, "/loading/bird3.gif")
val _ = loadingRectImage(9, "/loading/guy2.gif")
val _ = loadingRectImage(8, "/loading/guy_fall.gif")
val _ = rotatingLoading(10, "/loading/mushroom.png", 0.33, 1500)
val _ = loadingRectImage(10, "/loading/handicapped3.gif")
val _ = loadingRectImage(8, "/loading/handicapped4.gif", corderRadius = 99999)
val _ = loadingRectImage(7, "/loading/spongebob1.gif", size = 0.35)

val _ = loadingRectImage(Probability.christmas(10), "/loading/santa1.gif", 0.26)
val _ = loadingRectImage(Probability.christmas(6), "/loading/santa2.gif", corderRadius = 99999)
val _ = loadingRectImage(Probability.christmas(9) :: Probability.default(3) :: Nil, "/loading/guy_on_ice.gif")
val _ = loadingRoundImage(Probability.christmas(9) :: Probability.default(3) :: Nil, "/loading/ski_rex.gif", 0.9)

val _ = rotatingLoading(Probability.maslenitsa(10) :: Probability.default(3) :: Nil, "/loading/pancake.png", duration = 2500)


private def loadingRoundImage(
  probabilities: Probabilities,
  imageUrl: String,
  imageSizeCoef: Double = 0.5,
  bgColor: Color = Color.White,
  borderColor: Color = null,
  borderWidth: Double = 4.5,
  size: Double = 0.26,
) = Impl(probabilities): loading =>
  val (loadingWidth, loadingHeight) = loading.widthHeight(size)

  new StackPane:
    maxWidth <== loadingWidth
    maxHeight <== loadingHeight

    private val roundCorners = CornerRadii(100, asPercent = true)
    background = Background(Array(BackgroundFill(bgColor, roundCorners, Insets.Empty)))
    if (borderColor != null)
      border = Border(BorderStroke(borderColor, BorderStrokeStyle.Solid, roundCorners, BorderWidths(borderWidth)))

    children += new ImageView(imageUrl):
      fitWidth <== loadingWidth * imageSizeCoef
      fitHeight <== loadingHeight * imageSizeCoef
      preserveRatio = true
      alignmentInParent = Pos.Center


private def loadingRectImage(
  probabilities: Probabilities,
  imageUrl: String,
  size: Double = 0.3,
  corderRadius: Double = 15,
) = Impl(probabilities): loading =>
  new ImageView(imageUrl):
    private val (loadingWidth, loadingHeight) = loading.widthHeight(size, image().getWidth / image().getHeight)
    fitWidth <== loadingWidth
    fitHeight <== loadingHeight
    preserveRatio = true
    clip = new Rectangle:
      width   <== loadingWidth
      height  <== loadingHeight
      arcWidth  = corderRadius
      arcHeight = corderRadius


private def rotatingLoading(
  probabilities: Probabilities,
  imageUrl: String,
  size: Double = 0.3,
  duration: Long = 1000,
) = Impl(probabilities): loading =>
  new ImageView(imageUrl):
    private val (loadingWidth, loadingHeight) = loading.widthHeight(size, image().getWidth / image().getHeight)
    fitWidth <== loadingWidth
    fitHeight <== loadingHeight

    private val animation = new RotateTransition(Duration(duration), this):
      fromAngle = 0
      toAngle = 360
      cycleCount = Animation.Indefinite
      interpolator = Linear
      play()

    loading.visible.addListener: (_,_,visible) =>
      if (visible) animation.playFromStart()
      else animation.stop()
