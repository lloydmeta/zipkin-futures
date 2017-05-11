package com.beachape.zipkin.services

import com.twitter.zipkin.gen.{ Annotation, Span }

import scala.concurrent.Future

/**
 * Dummy ZipkinServiceLike that just returns true or false based on whether the [[Span]]s passed to it are
 * sendable to Zipkin
 */
object NoopZipkinService extends ZipkinServiceLike {

  type ServerSpan = Span
  type ClientSpan = Span

  implicit val eCtx = scala.concurrent.ExecutionContext.global

  def serverReceived(span: Span, annotations: (String, String)*): Future[Option[ServerSpan]] = {
    Future.successful {
      if (sendableSpan(span)) {
        span.addToAnnotations(new Annotation(System.currentTimeMillis(), "sr"))
        Some(span)
      } else None
    }
  }

  def clientSent(span: Span, annotations: (String, String)*): Future[Option[ClientSpan]] = {
    Future.successful {
      if (sendableSpan(span)) {
        span.addToAnnotations(new Annotation(System.currentTimeMillis(), "cs"))
        Some(span)
      } else None
    }
  }

  def serverSent(span: ServerSpan, annotations: (String, String)*): Future[ServerSpan] = {
    Future.successful {
      span.addToAnnotations(new Annotation(System.currentTimeMillis(), "ss"))
      span
    }
  }

  def clientReceived(span: ClientSpan, annotations: (String, String)*): Future[ClientSpan] = {
    Future.successful {
      span.addToAnnotations(new Annotation(System.currentTimeMillis(), "cr"))
      span
    }
  }

  def clientSpanToSpan(clientSpan: ClientSpan): Span = clientSpan

  def serverSpanToSpan(serverSpan: ClientSpan): Span = serverSpan
}
