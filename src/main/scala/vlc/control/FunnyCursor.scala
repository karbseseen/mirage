package vlc.control

import scalafx.scene.image.{Image, WritableImage}
import scalafx.scene.{Cursor, ImageCursor}
import util.SystemInfo
import util.SystemInfo.OS

import scala.language.implicitConversions


private def getFunnyCursor: Cursor =

  class CursorImage(imageUrl: String, val targetX: Double, val targetY: Double):
    val image = Image(imageUrl)
    
  val cursors = List(
    CursorImage("/cursor/16x31.png", 7.5, 0),
    CursorImage("/cursor/17x22.png", 8.5, 0),
  ).sortBy: cursor =>
    -(cursor.image.width() max cursor.image.height())

  val possibleSizes = (16 to 48)
    .map(size => ImageCursor.getBestSize(size, size))
    .distinct

  cursors
    .view
    .flatMap: cursor =>
      possibleSizes
        .filter(size => size.width >= cursor.image.width() && size.height >= cursor.image.height())
        .minByOption(size => size.width - cursor.image.width() + size.height - cursor.image.height())
        .map((cursor, _))
    .headOption
    .map: (cursor, size) =>
      implicit inline def d2i(inline d: Double): Int = math.round(d).toInt

      val srcImage = cursor.image
      val dstImage = WritableImage(size.width, size.height)
      val reader = srcImage.pixelReader.get
      val writer = dstImage.pixelWriter

      if (SystemInfo.os == OS.Windows)
        writer.setPixels(0, 0, srcImage.width(), srcImage.height(), reader, 0, 0)
      else
        for
          x <- 0 until srcImage.width()
          y <- 0 until srcImage.height()
        do
          val srcArgb = reader.getArgb(x, y)
          val dstA = srcArgb >>> 24
          val dstR = 255 - ((srcArgb >> 16) & 0xff)
          val dstG = 255 - ((srcArgb >> 8) & 0xff)
          val dstB = 255 - (srcArgb & 0xff)
          val dstArgb = (dstA << 24) | (dstR << 16) | (dstG << 8) | dstB
          writer.setArgb(x, y, dstArgb)

      ImageCursor(dstImage, cursor.targetX, cursor.targetY)
    .getOrElse(Cursor.Default)
