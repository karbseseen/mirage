package fx

import javafx.scene.image.Image

import scala.util.Try


def getIconImages = Iterator.unfold(1): index =>
  Try(Image(s"icon/icon-$index.png")).toOption.map((_, index + 1))
