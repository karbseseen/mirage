package core.main

import util.also

import java.net.{DatagramPacket, DatagramSocket, InetAddress, InetSocketAddress}
import java.nio.{ByteBuffer, ByteOrder}
import scala.annotation.tailrec
import scala.util.Random


val MagicCookie = 0x2112A442

object MessageType:
  val BindingRequest = 0x0001
  val BindingSuccessResponse = 0x0101
  val BindingErrorResponse = 0x0111

@main def main(): Unit =

  val socket = new DatagramSocket
  val server = new InetSocketAddress("stun.l.google.com", 19302)
  val txId = new Array[Byte](12).also(Random.nextBytes)

  socket.send:
    val buf = ByteBuffer.allocate(20)
    buf.order(ByteOrder.BIG_ENDIAN)

    buf.putShort(MessageType.BindingRequest.toShort)
    buf.putShort(0.toShort)
    buf.putInt(MagicCookie)
    buf.put(txId)

    DatagramPacket(buf.array, buf.position(0).remaining(), server)

  val response =
    val packet = DatagramPacket(new Array[Byte](1024), 1024)
    socket.receive(packet)
    ByteBuffer.wrap(packet.getData)
      .order(ByteOrder.BIG_ENDIAN)
      .limit(packet.getLength)

  val msgType = response.getShort
  val msgLength = response.getShort
  val cookie = response.getInt
  if (cookie != MagicCookie) sys.error("Invalid STUN magic cookie")

  val txId2 = new Array[Byte](12).also(response.get)
  if (!txId2.sameElements(txId)) sys.error("Invalid txId")


  @tailrec def getAddress: Option[InetSocketAddress] =
    if (response.remaining < 4) None
    else
      val attrType = response.getShort
      val attrLen = response.getShort

      if (attrType == 0x0020)
        response.position(response.position + 1)
        val family = response.get
        val port = (response.getShort & 0xffff) ^ (MagicCookie >>> 16)
        val address = family match
          case 1 if attrLen >= 4 => // IPv4
            val ipInt = response.getInt ^ MagicCookie
            ByteBuffer.allocate(4).putInt(ipInt).array
          case 2 if attrLen >= 16 => // IPv6
            val xor = ByteBuffer.allocate(16)
              .putInt(MagicCookie)
              .put(txId)
            Array.tabulate(16)(_ => (response.get ^ xor.get).toByte)

        Some(InetSocketAddress(InetAddress.getByAddress(address), port))

      else
        response.position(((response.position + attrLen - 1) & ~0x3) + 4)
        getAddress

  println(getAddress)
