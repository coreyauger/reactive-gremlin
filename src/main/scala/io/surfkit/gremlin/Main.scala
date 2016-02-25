package io.surfkit.gremlin

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import play.api.libs.json.Json

/**
  * Created by suroot on 23/02/16.
  * http://doc.akka.io/docs/akka/snapshot/scala/http/client-side/websocket-support.html
  */
object Main extends App{

  override def main(args: Array[String]) {

    import scala.concurrent.Future

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    // Future[Done] is the materialized value of Sink.foreach,
    // emitted when the stream completes
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          val json = Json.parse(message.text)
          println(json)
          val res = Json.parse(message.text).as[Gremlin.Response]
          println(res)
      }


    // ...
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

    val r = buildRequest("g.V(323600).both().valueMap()")
    println(s"JSON: ${Json.toJson(r).toString()}")
    //val outgoing = Source.single(TextMessage(Json.toJson(r).toString()))
    val outgoing = Source.actorPublisher(GremlinActor.props)

    // flow to use (note: not re-usable!)
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:8182"))

  //http://doc.akka.io/docs/akka-stream-and-http-experimental/2.0.3/scala/stream-integrations.html
    val ref = Flow[TextMessage]
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .runWith(outgoing)

    ref ! buildRequest("g.V(323600).both().valueMap()")
    ref ! buildRequest("g.V(323600).valueMap()")

    // the materialized value is a tuple with
    // upgradeResponse is a Future[WebSocketUpgradeResponse] that
    // completes or fails when the connection succeeds or fails
    // and closed is a Future[Done] with the stream completion from the incoming sink
    /*val (upgradeResponse, closed) =
      outgoing
        .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(incoming)(Keep.both) // also keep the Future[Done]
        .run()
*/
    // just like a regular http request we can get 404 NotFound etc.
    // that will be available from upgrade.response
   /* val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.OK) {
        Future.successful(Done)
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    // in a real application you would not side effect here
    connected.onComplete(println)
    closed.foreach(_ => println("closed"))*/
  }

}
