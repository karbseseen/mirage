package p2p.loop_handler

import p2p.Peer


private[p2p] trait LoopHandler:
  def loop(now: Long, activePeers: List[Peer]): Unit
