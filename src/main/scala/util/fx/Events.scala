package util.fx

import javafx.beans.value.ObservableValue
import javafx.event.{Event, EventHandler, EventTarget}
import scalafx.event.EventType
import util.also


extension (target: EventTarget)
  inline def addEventFilter[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    handler.also(target.addEventFilter(eventType, _))

  inline def addEventHandler[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    handler.also(target.addEventHandler(eventType, _))


extension (observable: ObservableValue[? <: EventTarget])
  def registerEventFilter[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    observable.addListener: (_, oldNode, newNode) =>
      Option(oldNode).foreach(_.removeEventFilter(eventType, handler))
      Option(newNode).foreach(_.addEventFilter(   eventType, handler))
    handler

  def registerEventHandler[T <: Event, U >: T <: Event](eventType: EventType[T])(handler: EventHandler[U]): EventHandler[U] =
    observable.addListener: (_, oldNode, newNode) =>
      Option(oldNode).foreach(_.removeEventHandler(eventType, handler))
      Option(newNode).foreach(_.addEventHandler(   eventType, handler))
    handler
