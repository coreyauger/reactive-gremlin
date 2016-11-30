package io.surfkit.gremlin

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

/**
  * Created by suroot on 23/02/16.
  * http://doc.akka.io/docs/akka/snapshot/scala/http/client-side/websocket-support.html
  */
object Main extends App{

  override def main(args: Array[String]) {

    val client = new GremlinClient()

    val q = GremlinClient.buildRequest(s"""
      |g.V().has('uid','31a08059-44ef-45d8-b264-843dd4af51ed').out('has_provider').out('member_of').has(label,'collaboration').valueMap('collaborationId');
      """.stripMargin)
    println(s"running query: ${q}")
    client.query(q).foreach{ res =>
      println(s"xx: ${res}")
    }
  }

}
