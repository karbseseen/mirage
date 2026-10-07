package vlc.control

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon


private class IconView(size: Int) extends FontIcon:
  setStyle(s"-fx-icon-size: ${size}px; -fx-icon-color: white;")
  def this(icon: Ikon, size: Int) =
    this(size)
    setIconCode(icon)
