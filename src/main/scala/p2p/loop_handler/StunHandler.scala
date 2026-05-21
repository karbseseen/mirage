package p2p.loop_handler

import p2p.{P2p, Peer, Stun}
import scalafx.Includes.jfxObjectProperty2sfx

import java.net.{Inet4Address, Inet6Address}
import java.nio.ByteBuffer
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS


private[p2p] class StunHandler:

  private inline val TaskPeriod       = 3_000
  private inline val WaitAfterAddress = 10_000
  private inline val WaitAfterReceive = 3_000

  private val runnable: Runnable = () =>
    val now = System.currentTimeMillis
    for
      socket <- List(P2p.v4RemoteSocket, P2p.v6RemoteSocket)
      if now - socket.lastAddressTime > WaitAfterAddress
      if now - socket.lastReceiveTime > WaitAfterReceive
    do
      Stun.sendRequest(socket.channel)
  P2p.scheduler.scheduleWithFixedDelay(runnable, 0, TaskPeriod, MILLISECONDS)

  def parseReceived(data: ByteBuffer): Boolean =
    Stun.parseResponse(data) match
      case Stun.NotAStun => false
      case Stun.Error => true
      case success: Stun.Success =>
        val socket = success.address.getAddress match
          case _: Inet4Address => P2p.v4RemoteSocket
          case _: Inet6Address => P2p.v6RemoteSocket
        socket.address() = Some(success.address)
        socket.lastAddressTime = System.currentTimeMillis
        true
