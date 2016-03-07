package io.surfkit.gremlin

import java.util.UUID

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.stream.{IOResult, ActorMaterializer}
import akka.stream.scaladsl._
import play.api.libs.json.Json
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

/**
  * Created by coreyauger on 23/02/16.
  * http://doc.akka.io/docs/akka/snapshot/scala/http/client-side/websocket-support.html
  */
object GremlinClient{
  def buildRequest(gremlin: String, bindings: Map[String,String] = Map.empty[String, String] ) = {
    Gremlin.Request(
      requestId = UUID.randomUUID,
      op = Gremlin.Op.eval,
      processor = "",
      args = Gremlin.Args(
        gremlin = gremlin,
        bindings = bindings,
        language = Gremlin.Language.`gremlin-groovy`
      )
    )
  }
}

class GremlinClient(host:String = "ws://localhost:8182", maxInFlight: Int = 250, responder:Option[ActorRef] = None, onResponse:Option[Gremlin.Response => Unit] = None) {
  import scala.concurrent.Future

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private var producerActor: Option[ActorRef] = None

  private val limiter = system.actorOf(LimiterActor.props(maxInFlight))

  private def limitGlobal[T](limiter: ActorRef, maxAllowedWait: FiniteDuration): Flow[T, T, akka.NotUsed] = {
    import akka.pattern.ask
    import akka.util.Timeout
    Flow[T].mapAsync(4)((element: T) => {
      implicit val triggerTimeout = Timeout(maxAllowedWait)
      val limiterTriggerFuture = limiter ? LimiterActor.WantToPass
      limiterTriggerFuture.map((_) => element)
    })
  }

  private def handleResponse(res: Gremlin.Response) = {
    limiter ! res
    responder.foreach(_ ! res)
    onResponse.foreach(_(res))
    print("#")
  }

  def connectFlow(flow:Source[Gremlin.Request, Future[IOResult]]) = {
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(host))

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          handleResponse(Json.parse(message.text).as[Gremlin.Response])
        case x =>
          println(s"[WARNING] Sink case for unknown type ${x}")
      }

    val (upgradeResponse, closed) =
      flow
        .map{ r =>
          limiter ! r
          TextMessage(Json.toJson(r).toString())
        }
      .via(limitGlobal[TextMessage](limiter, 10 minutes) )
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()

    closed.foreach(_ => println("closed"))
  }

  def connectActor:ActorRef = {
    val outgoing = Source.actorPublisher(GremlinActor.props)

    // flow to use (note: not re-usable!)
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(host))

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          handleResponse(Json.parse(message.text).as[Gremlin.Response])
        case x =>
          println(s"[WARNING] Sink case for unknown type ${x}")
      }

    //http://doc.akka.io/docs/akka-stream-and-http-experimental/2.0.3/scala/stream-integrations.html
    val ref = Flow[TextMessage]
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .runWith(outgoing)
    producerActor = Some(ref)
    ref
  }

}
