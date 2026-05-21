package p2p

import byte_codec.ByteCodec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.{DatagramPacket, InetSocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.io.StdIn
import scala.util.Random


object Tracker:

  private val peerHash = Random.nextBytes(20)
  private val buffer = ByteBuffer.allocate(2048)

  private implicit val transactionIds: TransactionIdGenerator = new TransactionIdGenerator
  private val servers = IArray(
    Server(InetSocketAddress("tracker.ducks.party",       1984)),
    Server(InetSocketAddress("ipv4announce.sktorrent.eu", 6969)),
    Server(InetSocketAddress("tracker.torrent.eu.org",    451)),
    Server(InetSocketAddress("open.demonii.com",          1337)),
    Server(InetSocketAddress("seedpeer.net",              6969)),
    Server(InetSocketAddress("tracker.bluefrog.pw",       2710)),
    Server(InetSocketAddress("torrentclub.online",        54123)),
  )
  
  def tryRequest(infoHash: Array[Byte], ): Unit =
    val now = System.currentTimeMillis
    val connectedServer = servers.find(now - _.connectionIdTime < 60_000)
    for server <- connectedServer do
      val request = AnnounceRequest(
        connectionId  = server.connectionId,
        action        = 1,
        transactionId = server.transactionId,
        infoHash      = Hash(infoHash),
        peerHash      = Hash(peerHash),
        downloaded    = 0,
        left          = ???,
        uploaded      = 0,
        event         = server.event,
        ip            = ???,
        key           = ???,
        peerNumber    = ???,
        port          = ???,
      )
      server.event = 0
    val server = servers.find(now - _.connectionIdTime < 60_000) getOrElse servers(Random.nextInt(servers.length))
    
  
  private class TransactionIdGenerator:
    val value: Iterator[Int] = Iterator.continually(Random.nextInt()).distinct

  private class Server(val address: InetSocketAddress)(using transactionIds: TransactionIdGenerator):
    val transactionId: Int = { transactionIds.value.hasNext; transactionIds.value.next }
    var connectionId = 0L
    var connectionIdTime = 0L
    var event = 2

  private class Hash(val value: Array[Byte])
  private case class Peer(ip: Int, port: Short)

  private case class ConnectRequest(protocolId: Long = 0x41727101980L, action: Int = 0, transactionId: Int)
  private case class ConnectResponse(action: Int, transactionId: Int, connectionId: Long)

  private case class AnnounceRequest(
    connectionId: Long,
    action: Int,
    transactionId: Int,
    infoHash: Hash,
    peerHash: Hash,
    downloaded: Long,
    left: Long,
    uploaded: Long,
    event: Int,
    ip: Int,
    key: Int,
    peerNumber: Int,
    port: Short,
  )

  private case class AnnounceResponse(
    action: Int,
    transactionId: Int,
    interval: Int,
    leechers: Int,
    seeders: Int,
    peers: List[Peer],
  )
  
  private implicit val HashCodec: ByteCodec[Hash] = new ByteCodec:
    override def decode(input: ByteArrayInputStream): Hash = sys.error("Don't call this")
    override def encode(value: Hash, output: ByteArrayOutputStream): Unit =
      println("Hash size: " + value.value.length)
      output.write(value.value)

  private implicit val listPeerCodec: ByteCodec[List[Peer]] = new ByteCodec:
    def encode(value: List[Peer], output: ByteArrayOutputStream): Unit = sys.error("Don't call this")
    def decode(input: ByteArrayInputStream): List[Peer] =
      Iterator.unfold(input): input =>
        Option.when(input.available >= 6)(summon[ByteCodec[Peer]].decode(input), input)
      .toList
