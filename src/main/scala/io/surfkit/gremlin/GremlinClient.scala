package io.surfkit.gremlin

import java.util.UUID

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.stream._
import akka.stream.scaladsl._
import akka.pattern.after
import play.api.libs.json.Json

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

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

class GremlinClient(host:String = "ws://localhost:8182", maxInFlight: Int = 250, responder:Option[ActorRef] = None, onResponse:Option[Gremlin.Response => Unit] = None, implicit val system: ActorSystem = ActorSystem()) {
  import scala.concurrent.Future
  import system.dispatcher

  private[this] val decider: Supervision.Decider = {
    case _ => Supervision.Resume  // Never give up !
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  private var producerActor: Option[ActorRef] = None
  private val promiseMap = mutable.HashMap.empty[UUID, Promise[Gremlin.Response]]
  private val partialContentMap = scala.collection.concurrent.TrieMap.empty[UUID, List[Gremlin.Result]]

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
    promiseMap.get(res.requestId).map{ p =>
      def validResult = {
        val result = partialContentMap.get(res.requestId).map{ xs =>
          res.copy(result=res.result.copy(data = Some(res.result.data.get ::: xs.flatMap(_.data.get)) ))
        }.getOrElse(res)
        partialContentMap -= res.requestId
        p.complete(Try(result))
        promiseMap -= res.requestId
      }
      println(s"titan response(${res.status.code}) for request: ${res.requestId}")
      res.status match{
        case Gremlin.Status(_,200,_) => validResult
        case Gremlin.Status(_,204,_) => validResult
        case Gremlin.Status(_,206,_) =>
          partialContentMap.get(res.requestId) match{
            case Some(xs) => partialContentMap += res.requestId -> (res.result :: xs)
            case None => partialContentMap += res.requestId -> (res.result :: Nil)
          }
        case s =>
          promiseMap -= res.requestId
          println(s"[ERROR] - GremlinClient: ${s.code} ${s.message} ${s.attributes}")
          p.failure(new RuntimeException(s.message))
      }
    }
    responder.foreach(_ ! res)
    onResponse.foreach(_(res))
  }

  def connectFlow(flow:Source[Gremlin.Request, Future[IOResult]]) = {
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(host))

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          handleResponse(Json.parse(message.text).as[Gremlin.Response])
        case TextMessage.Streamed(stream) =>
          stream
            .limit(100)                   // Max frames we are willing to wait for
            .completionTimeout(5 seconds) // Max time until last frame
            .runFold("")(_ + _)           // Merges the frames
            .flatMap{ msg =>
              handleResponse(Json.parse(msg).as[Gremlin.Response])
              Future.successful(msg)
            }
        case x =>
          println(s"[WARNING] Sink case for unknown type ${x}")
      }

    val (upgradeResponse, closed) =
      flow
        .map{ r =>
          limiter ! r
          TextMessage(Json.toJson(r).toString())
        }
      .via(limitGlobal[TextMessage](limiter, 1 minutes) )
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()

    closed
  }

  def query(gremlin: String)(implicit timeout: FiniteDuration = 3 seconds):Future[Gremlin.Response] = query(GremlinClient.buildRequest(gremlin))

  def query(req: Gremlin.Request)(implicit timeout: FiniteDuration):Future[Gremlin.Response] = {
    val p = Promise[Gremlin.Response]
    val actorRef = producerActor.getOrElse(connectActor)
    promiseMap.put(req.requestId, p)
    lazy val t = after(duration = timeout, using = system.scheduler){
      promiseMap -= req.requestId
      Future.failed(new  scala.concurrent.TimeoutException("Future timed out!"))
    }
    val fWithTimeout = Future.firstCompletedOf(Seq(p.future, t) )
    actorRef ! req
    fWithTimeout
  }

  def connectActor:ActorRef = {
    val outgoing = Source.actorPublisher(GremlinActor.props(limiter))

    // flow to use (note: not re-usable!)
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(host))

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          //println(s"&&&&&: ${Json.parse(message.text).as[Gremlin.Response]}")
          handleResponse(Json.parse(message.text).as[Gremlin.Response])
        case TextMessage.Streamed(stream) =>
          stream
            .limit(100)                   // Max frames we are willing to wait for
            .completionTimeout(5 seconds) // Max time until last frame
            .runFold("")(_ + _)           // Merges the frames
            .flatMap{msg =>
              handleResponse(Json.parse(msg).as[Gremlin.Response])
              Future.successful(msg)
            }
        case x =>
          println(s"[WARNING] Sink case NON 'TextMessage.Strict' (${x})")
      }

    //http://doc.akka.io/docs/akka-stream-and-http-experimental/2.0.3/scala/stream-integrations.html
    val ref = Flow[TextMessage]
      .keepAlive(45.seconds, () => TextMessage(""))
      .via(limitGlobal[TextMessage](limiter, 1 minutes) )
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .runWith(outgoing)
    producerActor = Some(ref)
    ref
  }

}
