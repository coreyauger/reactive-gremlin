package io.surfkit.gremlin

import java.math.BigInteger
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.{ActorPublisher, MaxInFlightRequestStrategy}
import io.surfkit.gremlin.Gremlin.{GetInFlight, InFlight, Response}
import play.api.libs.json.Json

import scala.collection.mutable

/**
  * Created by suroot on 24/02/16.
  */
object GremlinActor{
  def props(limiter: ActorRef): Props = Props(classOf[GremlinActor], limiter)
}

class GremlinActor(limiter: ActorRef) extends ActorPublisher[TextMessage] with ActorLogging {
  var prev = BigInteger.ZERO
  var curr = BigInteger.ZERO

  val requestQueue = mutable.Queue[Gremlin.Request]()

  val inFlight = scala.collection.mutable.Set.empty[UUID]

  def receive = {
    case g:Gremlin.Request =>
      //log.debug("[GremlinActor] Received Request ({}) from Subscriber", g)
      //println("[GremlinActor] Received Request ({}) from Subscriber", g)
      limiter ! g
      requestQueue.enqueue(g)
      sendReq()
    case Gremlin.Cancel =>
      log.info("[GremlinActor] Cancel Message Received -- Stopping")
      context.stop(self)
    case OnComplete =>
      onComplete()
      context.stop(self)
    case Request(cnt) =>
      sendReq()
    case r:Response =>
      log.debug(s"[GremlinActor] resp ${r.requestId}")
      inFlight -= r.requestId
    case GetInFlight =>
      log.debug(s"inFlight.size: ${inFlight.size}")
      context.sender() ! InFlight(inFlight.size)
    case _ =>
  }

  def sendReq() {
    while(isActive && totalDemand > 0 && !requestQueue.isEmpty) {
      val r = requestQueue.dequeue()
      try {
        inFlight += r.requestId
        val json = TextMessage(Json.toJson(r).toString())
        onNext(json)
      }catch{
        case t: Throwable =>
          t.printStackTrace()
          inFlight -= r.requestId
      }
    }
  }

  val requestStrategy = new MaxInFlightRequestStrategy(500) {
    override def inFlightInternally: Int = requestQueue.size
  }
}
