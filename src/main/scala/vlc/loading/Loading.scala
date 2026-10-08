package vlc.loading

import core.DailyRandom
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.scene.layout as jfxsl
import p2p.base.P2p
import scalafx.Includes.jfxNumberBinding2sfx
import scalafx.beans.binding.NumberBinding
import scalafx.geometry.Pos
import scalafx.scene.Node
import scalafx.scene.layout.{Background, StackPane}
import scalafx.scene.paint.Color

import java.time.LocalDate
import scala.language.implicitConversions
import scala.util.Random


private[vlc] class Loading extends StackPane:
  alignmentInParent = Pos.Center
  background = Background fill Color.Black.opacity(0.25)

  private var seed: Option[Long] = None
  visible() = false
  managed <== visible
  visible.addListener: (_,_,visible) =>
    if (visible)
      val daily = new DailyRandom
      if (!seed.contains(daily.seed))
        seed = Some(daily.seed)
        implicit val random: Random = daily.random

        val pairs = for
          instance <- instances
          probability <- instance.probabilities
        yield instance -> probability
        val groups = pairs.groupBy(_._2.group)
        val group = groups.selectWithProbability(_._1.probability(LocalDate.now))._2
        val instance = group.selectWithProbability(_._2.value)._1
        children = instance.apply(this)

  private[loading] def widthHeight(coef: Double, ratio: Double = 1): (NumberBinding, NumberBinding) =
    val parentWidth = parent.flatMap[Number]:
      case parent: jfxsl.Region => parent.widthProperty
      case _ => null
    val parentHeight = parent.flatMap[Number]:
      case parent: jfxsl.Region => parent.heightProperty
      case _ => null

    val width = Bindings.createDoubleBinding(
      () => (parentWidth.get min (parentHeight.get * ratio) max 200 min 800) * coef,
      parentWidth, parentHeight
    )
    val height = width / ratio

    (width, height)

  extension (observable: ObservableValue[Number])
    private def get: Double = observable.getValue match
      case null => 0
      case value => value.doubleValue


private[vlc] class Impl private[loading](
  val probabilities: Probabilities,
)(
  val apply: Loading => Node,
):
  instances = this :: instances
