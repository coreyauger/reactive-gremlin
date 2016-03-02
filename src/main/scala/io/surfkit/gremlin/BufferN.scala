package io.surfkit.gremlin

import java.util.UUID

import akka.stream.stage._

/**
  * Created by suroot on 29/02/16.
  */
class BufferN[T](set: scala.collection.mutable.Set[UUID], maxsize: Int) extends DetachedStage[T, T] {
  private var buf = Vector.empty[T]
  private var capacity = maxsize

  private def isFull = set.size >= maxsize
  private def isEmpty = set.isEmpty

  private def dequeue(): T = {
    capacity += 1
    val next = buf.head
    buf = buf.tail
    next
  }

  private def enqueue(elem: T) = {
    capacity -= 1
    buf = buf :+ elem
  }

  override def onPull(ctx: DetachedContext[T]): DownstreamDirective = {
    println(s"onPull ${set.size}")
    if (buf.isEmpty) {
      println(s"empty ")
      if (ctx.isFinishing) ctx.finish() // No more elements will arrive
      else ctx.holdDownstream() // waiting until new elements
    } else {
      val next = dequeue()
      if (ctx.isHoldingUpstream && !isFull) {
        println(" release upstream")
        ctx.pushAndPull(next) // release upstream
      }
      else ctx.push(next)
    }
  }

  override def onPush(elem: T, ctx: DetachedContext[T]): UpstreamDirective = {
    println(s"onPush ${set.size}")
    enqueue(elem)
    if (isFull) {
      println("holdUpstream")
      ctx.holdUpstream() // Queue is now full, wait until new empty slot
    }
    else {
      if (ctx.isHoldingDownstream) ctx.pushAndPull(dequeue()) // Release downstream
      else ctx.pull()
    }
  }

  override def onUpstreamFinish(ctx: DetachedContext[T]): TerminationDirective = {
    if (!isEmpty) ctx.absorbTermination() // still need to flush from buffer
    else ctx.finish() // already empty, finishing
  }
}
