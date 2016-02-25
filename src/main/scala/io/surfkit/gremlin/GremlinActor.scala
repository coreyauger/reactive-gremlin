package io.surfkit.gremlin

import java.math.BigInteger

import akka.actor.{Props, ActorLogging}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.{MaxInFlightRequestStrategy, ActorPublisher}
import play.api.libs.json.Json

import scala.collection.mutable

/**
  * Created by suroot on 24/02/16.
  */
object GremlinActor{
  def props(): Props = Props(classOf[GremlinActor])
}

class GremlinActor extends ActorPublisher[TextMessage] with ActorLogging {
  var prev = BigInteger.ZERO
  var curr = BigInteger.ZERO

  val requestQueue = mutable.Queue[Gremlin.Request]()

  def receive = {
    case g:Gremlin.Request =>
      log.debug("[GremlinActor] Received Request ({}) from Subscriber", g)
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
    case _ =>
  }

  def sendReq() {
    while(isActive && totalDemand > 0 && !requestQueue.isEmpty) {
      val json = TextMessage(Json.toJson(requestQueue.dequeue()).toString())
      onNext(json)
    }
  }

  val requestStrategy = new MaxInFlightRequestStrategy(50) {
    override def inFlightInternally: Int = requestQueue.size
  }
}
