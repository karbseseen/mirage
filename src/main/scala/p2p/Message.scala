package p2p

import byte_codec.Discriminator as Type


trait Message extends Product

object Message:

  @Type(100) case class Ping(id: P2p.Id, time: Long)
  @Type(101) case class Pong(time: Long)
