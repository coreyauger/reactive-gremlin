package io.surfkit.gremlin

import java.util.UUID

import akka.stream.{Attributes, Outlet, Inlet, FlowShape}
import akka.stream.stage.{OutHandler, InHandler, GraphStageLogic, GraphStage}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * Created by suroot on 01/03/16.
  */

class ThrottleGraphStage[A](switch: Future[Unit]) extends GraphStage[FlowShape[A, A]] {

  val in = Inlet[A]("ThrottleGraphStage.in")
  val out = Outlet[A]("ThrottleGraphStage.out")

  val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        val callback = getAsyncCallback[UUID] { (uuid) =>
          completeStage()
        }
        switch.foreach(_ => callback.invoke(UUID.randomUUID()))
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = { push(out, grab(in)) }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = { pull(in) }
      })
    }
}