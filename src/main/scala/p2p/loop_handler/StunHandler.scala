package p2p.loop_handler

import p2p.{P2p, Peer, Stun}
import scalafx.Includes.jfxObjectProperty2sfx


private[p2p] class StunHandler extends LoopHandler:

  private inline val Period = 3_000
  private inline val DiePeriod = 30_000

  def loop(now: Long, activePeers: List[Peer]): Unit =
    for socket <- List(P2p.v4RemoteSocket, P2p.v6RemoteSocket) do
      if (activePeers.isEmpty && now - socket.addressTime > Period)
        Stun.sendRequest(socket.channel)
        socket.addressTime = now
      if (now - socket.addressTime > DiePeriod)
        socket.address() = None
