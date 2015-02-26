package com.beachape.zipkin.services

import java.util.Collections

import com.github.kristofa.brave
import com.github.kristofa.brave._
import com.twitter.zipkin.gen.Span

import scala.concurrent.{ ExecutionContext, Future }

/**
 * ZipkinService based on [[Brave]].
 *
 * There should be at most 1 of these instances in a given running app, shared between multiple
 * objects that need tracing.
 *
 * @param collector a [[SpanCollector]]
 * @param traceFilters a List of span name filters. List can be empty if you don't want trace filtering (sampling).
 *                     The trace filters will be executed in order. If one returns false there will not be tracing and
 *                     the next trace filters will not be executed anymore.
 */
class BraveZipkinService(hostIp: String,
                         hostPort: Int,
                         serviceName: String,
                         collector: SpanCollector,
                         traceFilters: Seq[String => Boolean] = Seq.empty)(implicit val eCtx: ExecutionContext)
    extends ZipkinServiceLike {

  type ServerSpan = brave.ServerSpan
  type ClientSpan = Span

  private val endPointSubmitter = Brave.getEndPointSubmitter
  private val clientTracer = Brave.getClientTracer(collector, Collections.emptyList[TraceFilter])
  private val clientThreadBinder = Brave.getClientSpanThreadBinder
  private val serverTracer = Brave.getServerTracer(collector, Collections.emptyList[TraceFilter])
  private val serverThreadBinder = Brave.getServerSpanThreadBinder

  if (!endPointSubmitter.endPointSubmitted()) {
    endPointSubmitter.submit(hostIp, hostPort, serviceName)
  }

  def serverReceived(span: Span, annotations: (String, String)*): Future[Option[ServerSpan]] = {
    existingServerSpan(span) match {
      case None => Future.successful(None)
      case Some(serverSpan) => Future {
        serverThreadBinder.setCurrentSpan(serverSpan)
        annotations.foreach { case (key, value) => serverTracer.submitBinaryAnnotation(key, value) }
        serverTracer.setServerReceived()
        Some(serverSpan)
      }
    }
  }

  def serverSent(span: ServerSpan, annotations: (String, String)*): Future[Option[ServerSpan]] = {
    if (shouldSend(span.getSpan)) Future {
      serverThreadBinder.setCurrentSpan(span)
      annotations.foreach { case (key, value) => serverTracer.submitBinaryAnnotation(key, value) }
      serverTracer.setServerSend()
      Some(span)
    }
    else {
      Future.successful(None)
    }
  }

  def clientSent(span: Span, annotations: (String, String)*): Future[Option[ClientSpan]] = {
    if (shouldSend(span)) Future {
      clientThreadBinder.setCurrentSpan(span)
      annotations.foreach { case (key, value) => clientTracer.submitBinaryAnnotation(key, value) }
      clientTracer.setClientSent()
      Some(span)
    }
    else {
      Future.successful(None)
    }
  }

  def clientReceived(span: ClientSpan, annotations: (String, String)*): Future[Option[ClientSpan]] = {
    if (shouldSend(span)) Future {
      clientThreadBinder.setCurrentSpan(span)
      annotations.foreach { case (key, value) => clientTracer.submitBinaryAnnotation(key, value) }
      clientTracer.setClientReceived()
      Some(span)
    }
    else {
      Future.successful(None)
    }
  }

  /*
   * Returns true if the span is not supposed to be filtered out
   */
  private[this] def shouldSend(span: Span): Boolean = {
    sendableSpan(span) && traceFilters.forall(_.apply(span.getName))
  }

  /*
   * Returns a shareable [[ServerSpan]]
   *
   * Returns None if the passed in [[Span]] should not be sent or is otherwise not clean.
   */
  private[this] def existingServerSpan(span: Span): Option[ServerSpan] = {
    if (shouldSend(span)) {
      serverTracer.setStateCurrentTrace(span.getTrace_id, span.getId, span.getParent_id, span.getName)
      Some(serverThreadBinder.getCurrentServerSpan)
    } else {
      None
    }
  }

  def serverSpanToSpan(serverSpan: ServerSpan): ClientSpan = serverSpan.getSpan

  def clientSpanToSpan(clientSpan: ClientSpan): ClientSpan = clientSpan
}