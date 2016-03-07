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
object GremlinClient {

  import scala.concurrent.Future

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private var producerActor: Option[ActorRef] = None

  val limiter = system.actorOf(LimiterActor.props(250))

  private[this] def limitGlobal[T](limiter: ActorRef, maxAllowedWait: FiniteDuration): Flow[T, T, akka.NotUsed] = {
    import akka.pattern.ask
    import akka.util.Timeout
    Flow[T].mapAsync(4)((element: T) => {
      implicit val triggerTimeout = Timeout(maxAllowedWait)
      val limiterTriggerFuture = limiter ? LimiterActor.WantToPass
      limiterTriggerFuture.map((_) => element)
    })
  }

  // Future[Done] is the materialized value of Sink.foreach,
  // emitted when the stream completes

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

  def connectFlow(flow:Source[TextMessage, Future[IOResult]], url:String = "ws://localhost:8182") = {
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          val res = Json.parse(message.text).as[Gremlin.Response]
          //producerActor.map(_ ! res )
          //println(res)
          limiter ! res
          print("#")
      }

    val (upgradeResponse, closed) =
      flow
      .via(limitGlobal[TextMessage](limiter, 10 minutes) )
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()

    closed.foreach(_ => println("closed"))

  }

  def connectActor(url:String = "ws://localhost:8182"):ActorRef = {
    val outgoing = Source.actorPublisher(GremlinActor.props)

    // flow to use (note: not re-usable!)
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          //println(json)
          val res = Json.parse(message.text).as[Gremlin.Response]
          producerActor.map(_ ! res )
          //println(res)
          print(s".")
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
