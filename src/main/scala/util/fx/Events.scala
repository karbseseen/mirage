package util.fx

import javafx.event.{Event, EventHandler}
import scalafx.event.EventType
import scalafx.scene.{Node, Scene}
import util.also


extension (node: Node)
  inline def addEventFilter[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    handler.also(node.delegate.addEventFilter(eventType, _))
  inline def addEventHandler[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    handler.also(node.delegate.addEventHandler(eventType, _))

extension (scene: Scene)
  inline def addEventFilter[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    handler.also(scene.delegate.addEventFilter(eventType, _))
  inline def addEventHandler[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    handler.also(scene.delegate.addEventHandler(eventType, _))
