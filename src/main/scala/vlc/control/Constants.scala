package vlc.control

import fx.AutoBg
import fx.AutoBg.+
import scalafx.scene.Node
import scalafx.scene.layout.CornerRadii
import scalafx.scene.paint.Color


private inline val shortInset = 6.0
private inline val inset = 8.0
private inline val longInset = 12.0
private inline val bgOpacity = 0.3
private inline val menuBgOpacity = 0.65
private inline val menuCornerRadii = 10.0
private inline val autoHideTimeout = 3000L

private val bgColor = Color.gray(0.12, 0.72)
private val bgHoverColor = Color.gray(1, 0.12)
private val staticBg = AutoBg.fill(bgColor, CornerRadii(99999))
private val staticBgHover = AutoBg.fill(bgHoverColor, CornerRadii(99999))
private val staticHoveredBg = staticBg + staticBgHover

private def hoverableBg(node: Node) = node.hover.map(if (_) staticHoveredBg else staticBg)
