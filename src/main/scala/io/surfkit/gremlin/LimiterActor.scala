package io.surfkit.gremlin

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Actor, Props}
import io.surfkit.gremlin.Gremlin.Response
import scala.collection.mutable
/**
  * Created by suroot on 01/03/16.
  */

object LimiterActor {
  case object WantToPass
  case object MayPass

  def props(maxAvailableTokens: Int): Props =
    Props(new LimiterActor(maxAvailableTokens))
}

class LimiterActor(val maxAvailableTokens: Int) extends Actor with ActorLogging {
  import LimiterActor._
  private var waitQueue = scala.collection.immutable.Queue.empty[ActorRef]
  private val requestSet = mutable.Set[UUID]()

  override def receive: Receive = open

  val open: Receive = {
    case g:Gremlin.Request =>
      print(".")
      //log.debug("[GremlinActor] Received Request ({}) from Subscriber", g)
      requestSet += g.requestId
    case r:Response =>
      requestSet -= r.requestId
    case WantToPass =>
      sender() ! MayPass
      if (requestSet.size > maxAvailableTokens) {
        context.become(closed)
      }
  }

  val closed: Receive = {
    case g:Gremlin.Request =>
      //log.debug("[GremlinActor] Received Request ({}) from Subscriber", g)
      requestSet += g.requestId
    case r:Response =>
      requestSet -= r.requestId
      if(requestSet.size < maxAvailableTokens){
        waitQueue.iterator.foreach(_ ! MayPass)
        waitQueue = scala.collection.immutable.Queue.empty[ActorRef]
        context.become(open)
      }
    case WantToPass =>
      waitQueue = waitQueue.enqueue(sender())
  }
}
