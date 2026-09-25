package p2p.phone

import util.{also, loop}

import scala.collection.mutable


private class Wsola(windowSize: Int, windowRange: Int):
  import Wsola.*

  private val buffer = Buffer()
  private val crossFade = CrossFade(windowSize)

  private var firstHalfWindowGone = false
  private var bufferOffset, windowOffset, crossFadeSize, counter = 0
  private var counterCoef = windowSize * 0.5f
  private var _speed = 1f

  reset()
  private def reset(): Unit =
    firstHalfWindowGone = false
    bufferOffset = windowRange / -2
    windowOffset = 0
    crossFadeSize = windowSize / 2
    counter = 0

  def speed: Float = _speed
  def speed_=(value: Float): Unit =
    counterCoef = value * windowSize * 0.5f
    _speed = value

  def put(data: Array[Float]): Unit =
    buffer.put(data)
    if (!firstHalfWindowGone && buffer.size >= crossFadeSize)
      firstHalfWindowGone = true
      crossFade.put(buffer.iterator, buffer.iterator, crossFadeSize)
    while (moveToNextWindow()) {}

  def available: Int = crossFade.size
  def availableForFlush: Int = crossFade.size + ((buffer.size - windowOffset - crossFadeSize) max 0)

  def get(size: Int): Array[Float] = crossFade.get(size)

  private def moveToNextWindow(): Boolean =
    if (buffer.size < windowOffset + windowSize) return false
    val window2rangeStart = ((counter + 1) * counterCoef).toInt - (counter * counterCoef).toInt + bufferOffset
    if (buffer.size < window2rangeStart + (windowSize - crossFadeSize) + windowRange) return false

    val window1iterator = buffer.iterator(windowOffset + crossFadeSize)
    crossFadeSize = windowSize - crossFadeSize

    var maxCos = Float.MinValue

    val window1lengthSqr =
      var result = 0f
      val it1 = window1iterator.clone
      loop(crossFadeSize): _ =>
        val value1 = it1.next
        result += value1 * value1
      result

    loop(window2rangeStart max 0, window2rangeStart + windowRange): offset =>
      val it1 = window1iterator.clone
      val it2 = buffer.iterator(offset)
      var window2lengthSqr, dot = 0f

      loop(crossFadeSize): index =>
        val value2 = it2.next
        window2lengthSqr += value2 * value2
        dot += it1.next * value2

      val cos = dot / math.sqrt(window1lengthSqr * window2lengthSqr).toFloat
      if (maxCos < cos)
        maxCos = cos
        windowOffset = offset

    if (window2rangeStart > 0)
      buffer.move(window2rangeStart)
      windowOffset -= window2rangeStart
      bufferOffset = 0
    else
      bufferOffset = window2rangeStart
    crossFade.put(window1iterator, buffer.iterator(windowOffset), crossFadeSize)
    counter += 1
    true

  /** @param size Needs to be not less than [[available]], ideally equal to [[availableForFlush]] */
  def flush(size: Int): Array[Float] =
    if (size < crossFade.size) throw IllegalArgumentException("size needs to be not less than available")

    val array = new Array[Float](size)
    val offset = crossFade.size
    crossFade.get(array, 0, crossFade.size)

    val it1begin = windowOffset + crossFadeSize
    doCrossFade(
      array, offset, size - offset,
      buffer.iterator(it1begin), buffer.size - it1begin,
      buffer.iterator, buffer.size,
    )

    buffer.move(buffer.size)
    reset()
    array

  private def doCrossFade(
    array: Array[Float],
    offset: Int,
    length: Int,
    it1: Buffer.Iterator,
    length1: Int,
    it2: Buffer.Iterator,
    length2: Int,
  ): Unit =
    if (length <= length1 + length2)
      val crossFadeBegin =
        val value = length - length2
        if (value < 0) it2.move(-value)
        value max 0
      val crossFadeEnd = length1 min length
      val crossFadeSize = crossFadeEnd - crossFadeBegin
      val coef =
        val c = math.Pi / (crossFadeSize - 1)
        if (c.isFinite) c else 1.0

      loop(crossFadeBegin):
        array(_) = it1.next

      loop(crossFadeSize): index =>
        val cos = (0.5 + 0.5 * math.cos(index * coef)).toFloat
        array(index + crossFadeBegin) = it1.next * cos + it2.next * (1 - cos)

      loop(crossFadeEnd, length):
        array(_) = it2.next

    else
      val coef1 = math.Pi / (length1 - 1)
      val coef2 = math.Pi / (length2 - 1)
      val offset2 = length - length2

      loop(length1): index =>
        array(index) = it1.next * (0.5 + 0.5 * math.cos(index * coef1)).toFloat

      loop(length1, offset2): index =>
        array(index) = 0

      loop(length2): index =>
        array(index + offset2) = it1.next * (0.5 - 0.5 * math.cos(index * coef2)).toFloat


private object Wsola:

  private class Buffer:
    private val queue = mutable.Queue[Array[Float]]()
    private var offset, _size = 0

    def size: Int = _size

    def put(data: Array[Float]): Unit =
      queue.enqueue(data)
      _size += data.length

    def move(offset: Int): Unit =
      this.offset += offset
      this._size -= offset
      while (this.offset > 0 && this.offset >= queue.head.length)
        this.offset -= queue.dequeue.length

    def iterator: Buffer.Iterator = Buffer.Iterator(queue.toList, offset)
    def iterator(offset: Int): Buffer.Iterator = iterator.also(_.move(offset))

  private object Buffer:
    class Iterator private[Buffer] (private var data: List[Array[Float]], private var offset: Int):
      override def clone = new Iterator(data, offset)

      def next: Float = data.head(offset).also: _ =>
        offset += 1
        if (offset == data.head.length)
          offset = 0
          data = data.tail

      def move(offset: Int): Unit =
        this.offset += offset
        while (this.offset >= data.head.length)
          this.offset -= data.head.length
          data = data.tail


  private object CrossFade:
    private class Item(val it1: Buffer.Iterator, val it2: Buffer.Iterator, var remaining: Int)

  private class CrossFade(windowSize: Int):
    private val queue = mutable.Queue[CrossFade.Item]()
    private var _size = 0
    private var hannInvert = false
    private var hannIndex = 0

    private val hann =
      val indexCoef = math.Pi * 2 / windowSize
      IArray.tabulate(windowSize): index =>
        (0.5 - 0.5 * math.cos(index * indexCoef)).toFloat

    def size: Int = _size

    def put(it1: Buffer.Iterator, it2: Buffer.Iterator, length: Int): Unit =
      queue.enqueue(CrossFade.Item(it1, it2, length))
      _size += length

    def get(size: Int): Array[Float] =
      new Array[Float](size).also(get(_, 0, size))

    def get(array: Array[Float], offset: Int, length: Int): Unit =
      var arrayIndex = offset

      while (arrayIndex < length)
        val item = queue.head
        val count = item.remaining min (length - arrayIndex)
        val arrayIndexEnd = arrayIndex + count
        val (it1, it2) =
          if (hannInvert) (item.it2, item.it1)
          else            (item.it1, item.it2)

        while (arrayIndex < arrayIndexEnd)
          array(arrayIndex) = it1.next * hann(hannIndex) + it2.next * (1 - hann(hannIndex))
          arrayIndex += 1
          hannIndex += 1

        _size -= count
        item.remaining -= count
        if (item.remaining == 0)
          queue.dequeue()
          hannInvert = !hannInvert
          if (hannIndex == windowSize) hannIndex = 0
