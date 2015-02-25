package com.beachape.zipkin

import com.beachape.zipkin.services.ZipkinServiceLike
import com.twitter.zipkin.gen.Span

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Failure

object FutureEnrichment {

  implicit class RichFuture[A](val f: Future[A]) extends AnyVal {

    /**
     * Does a trace of a [[Future]] using a Client Sent/Receive annotation pair.
     *
     * This function expects that there is a parent [[Span]] in scope. If there is even is a [[Span]]
     * in scope, it may not be used as a Parent [[Span]] if it does not have the proper ids, namely
     * a span id and a trace id. In this case, a new scope with the given name and new ids will be used
     * and it will not have a parent id.
     */
    def trace(name: String, annotations: (String, String)*)(implicit parentSpan: Span, zipkinService: ZipkinServiceLike, ec: ExecutionContext): Future[A] = {
      val childSpan = zipkinService.generateSpan(name, parentSpan)
      val fChildSpan = zipkinService.clientSent(childSpan, annotations: _*)
      fChildSpan.foreach { maybeChildSpan =>
        maybeChildSpan.foreach { sentChildSpan =>
          f.onComplete {
            case t if t.isSuccess => zipkinService.clientReceived(sentChildSpan, annotations: _*)
            case Failure(e) => zipkinService.clientReceived(sentChildSpan, (annotations :+ ("failed" -> s"Finished with exception: ${e.getMessage}")): _*)
          }
        }
      }
      f
    }
  }

}
