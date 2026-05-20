package p2p

import java.net.{DatagramPacket, DatagramSocket, InetAddress, InetSocketAddress}
import java.nio.channels.DatagramChannel
import java.nio.{ByteBuffer, ByteOrder}
import scala.annotation.tailrec
import scala.util.Random


private object Stun:

  private val MagicCookie = 0x2112A442
  private object MessageType:
    val Request:          Short = 0x0001
    val SuccessResponse:  Short = 0x0101
  private object AttributeType:
    val XorMappedAddress: Short = 0x0020

  private val transactionId = Random.nextBytes(12)
  private val server = InetSocketAddress("stun.l.google.com", 19302)

  trait Result
  class Success(val address: InetSocketAddress) extends Result
  object Error extends Result
  object NotAStun extends Result

  def sendRequest(socket: DatagramChannel): Unit =
    val buffer = ByteBuffer.allocate(20)
      .order(ByteOrder.BIG_ENDIAN)
      .putShort(MessageType.Request)
      .putShort(0)
      .putInt(MagicCookie)
      .put(transactionId)
    socket.send(buffer, server)

  def parseResponse(packet: DatagramPacket): Result =
    if (packet.getLength < 20) NotAStun
    else
      val buffer = ByteBuffer.wrap(packet.getData)
        .order(ByteOrder.BIG_ENDIAN)
        .limit(packet.getLength)
        .position(2)
      if (
        buffer.getShort != packet.getLength - 20 ||
          buffer.getInt != MagicCookie ||
          transactionId.exists(_ != buffer.get)
      ) NotAStun
      else parseResponseInner(buffer)

  @tailrec private def parseResponseInner(buffer: ByteBuffer): Result =
    if (buffer.remaining < 4) Error
    else
      val attrType = buffer.getShort
      val attrLen = buffer.getShort

      if (buffer.remaining < attrLen) Error
      else if (attrType != AttributeType.XorMappedAddress)
        buffer.position(((buffer.position + attrLen - 1) & ~0x3) + 4)
        parseResponseInner(buffer)
      else
        buffer.position(buffer.position + 1)
        val family = buffer.get
        val port = (buffer.getShort & 0xffff) ^ (MagicCookie >>> 16)
        val address = Some(family).collect:
          case 1 if attrLen >= 4 =>
            val ipInt = buffer.getInt ^ MagicCookie
            ByteBuffer.allocate(4).putInt(ipInt).array
          case 2 if attrLen >= 16 =>
            val xor = ByteBuffer.allocate(16)
              .putInt(MagicCookie)
              .put(transactionId)
            Array.tabulate(16)(_ => (buffer.get ^ xor.get).toByte)
        address.fold(Error): address =>
          Success(InetSocketAddress(InetAddress.getByAddress(address), port))
