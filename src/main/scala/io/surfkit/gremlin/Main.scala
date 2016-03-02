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

    /*val client = GremlinClient.connect()

    client ! GremlinClient.buildRequest("g.V(323600).both().valueMap()")
    client ! GremlinClient.buildRequest("g.V(323600).valueMap()")*/
  }

}
