package com.beachape.zipkin

import com.beachape.zipkin.services.ZipkinServiceLike
import com.twitter.zipkin.gen.Span

import scala.concurrent.Future

object FutureEnrichment {

  /**
   * Sugary stuff
   */
  implicit class RichFuture[A](val f: Future[A]) extends AnyVal {

    /**
     * Does a trace of a [[Future]] using a Client Sent/Receive annotation pair.
     *
     * This function expects that there is a parent [[Span]] in scope. If there is even is a [[Span]]
     * in scope, it may not be used as a Parent [[Span]] if it does not have the proper ids, namely
     * a span id and a trace id. In this case, a new scope with the given name and new ids will be used
     * and it will not have a parent id.
     */
    def trace(name: String, annotations: (String, String)*)(implicit parentSpan: Span, zipkinService: ZipkinServiceLike): Future[A] = {
      TracedFuture(name, annotations: _*) { _ => f }
    }

  }

}
