package player

import scala.math.Integral.Implicits.infixIntegralOps


private def time2str(msTotal: Long): String =
  val sTotal = msTotal / 1000
  val (mTotal, s) = sTotal /% 60
  val (h, m) = mTotal /% 60
  if (h > 0) f"$h%d:$m%02d:$s%02d" else f"$m%02d:$s%02d"
