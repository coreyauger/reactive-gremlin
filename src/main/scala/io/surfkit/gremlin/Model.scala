package io.surfkit.gremlin

import java.util.UUID
import play.api.libs.json._

/**
  * Created by coreyauger on 24/02/16.
  * http://tinkerpop.apache.org/docs/3.0.2-incubating/#_developing_a_driver
  */
object Gremlin {

  case class Op(op: String)
  object Op{
    val eval = Op("eval")
  }
  implicit val opWrites = new Writes[Op] {
    def writes(op: Op) = JsString(op.op)
  }
  implicit val opReads:Reads[Op] = (JsPath \ "op").read[String].asInstanceOf[Reads[Op]]

  case class Language(language: String)
  object Language{
    val `gremlin-groovy` = Language("gremlin-groovy")
  }
  implicit val langWrites = new Writes[Language] {
    def writes(lang: Language) = JsString(lang.language)
  }
  implicit val langReads:Reads[Language] = (JsPath \ "language").read[String].asInstanceOf[Reads[Language]]

  case class Args(gremlin: String,
                  bindings:Map[String, String],
                  language: Language)
  implicit val argsWrites = Json.writes[Args]
  implicit val argsReads = Json.reads[Args]

  case class Request(requestId: UUID,
                            op: Op,
                            processor: String,
                            args: Args)
  implicit val requestWrites = Json.writes[Request]
  implicit val requestReads = Json.reads[Request]


  case class Status(message: String,
                    code: Int,
                    attributes: Map[String, String])
  implicit val statusWrites = Json.writes[Status]
  implicit val statusReads = Json.reads[Status]

  case class Result(data:Option[List[JsValue]],
                    meta:Map[String, String]){
    def dataList = data.getOrElse(Nil)
  }
  implicit val resultWrites = Json.writes[Result]
  implicit val resultReads = Json.reads[Result]

  case class Response(requestId: UUID,
                      status: Status,
                      result: Result)
  implicit val responseWrites = Json.writes[Response]
  implicit val responseReads = Json.reads[Response]

  case object Cancel
  case object GetInFlight
  case class InFlight(num:Int)

}
