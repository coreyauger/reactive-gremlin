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

    val q =s"""
      |g.V().has('uid','31a08059-44ef-45d8-b264-843dd4af51ed').out('has_provider').as('p').inE('team','guest').as('e').select('p','e').map{['type':[it.get()['p'].label()],'providerId':[it.get()['p'].value('providerId')],'providerName':[it.get()['p'].value('providerName')],'orgId':[it.get()['p'].value('orgId')],'sourceId':[it.get()['p'].value('sourceId')],'fullName':[it.get()['p'].value('fullName')],'role':[it.get()['e'].label()]]};
      """.stripMargin
    println(s"running query: ${q}")
    client.query(q).foreach{ res =>
      println(s"xx: ${res}")
    }

    val q2 = "g.V().has('uid','049d8ab8-6541-4cc7-9b49-9662cfca8bdc').out('has_provider').out('member_of').has(label,'role').out('member_of').has(label,'collaboration').valueMap('collaborationId');"
    client.query(q2).foreach{ res =>
      println(s"yy: ${res}")
    }
  }

}
