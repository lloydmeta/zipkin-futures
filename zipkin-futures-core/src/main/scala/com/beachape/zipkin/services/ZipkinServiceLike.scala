package com.beachape.zipkin.services

import com.beachape.zipkin.HttpHeaders
import com.twitter.zipkin.gen.Span

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Basic interface for what a ZipkinService might look like.
 *
 * The ServerSpan and ClientSpan types are to allow you to use types that are richer than the normal
 * Zipkin [[Span]] type if needed (this is useful when using with say, Brave)
 */
trait ZipkinServiceLike {

  /**
   * Customisable server span type
   */
  type ServerSpan

  /**
   * Customisable client span type
   */
  type ClientSpan

  /**
   * The execution context provided by and used within this service for tracing purposes
   */
  implicit def eCtx: ExecutionContext

  /**
   * Returns a [[ServerSpan]] with a ServerReceived annotation that will later be used to signal to the Zipkin collector
   * that a server received event has occurred at the current time.
   *
   * If annotations are provided, they will also be added.
   */
  def serverReceived(span: Span, annotations: (String, String)*): Future[Option[ServerSpan]]

  /**
   * Returns a [[ServerSpan]] with a ServerSent annotation that will be used to signal to the Zipkin collector that a server
   * Sent event has occurred at the current time. This method *should* be used to also actually send the underlying [[Span]]
   * to the Zipkin server.
   *
   * If annotations are provided, they will also be added.
   */
  def serverSent(span: ServerSpan, annotations: (String, String)*): Future[Option[ServerSpan]]

  /**
   * Creates a [[ClientSpan]] with a ClientSent annotation that will later be used to signal to the Zipkin collector that a Client
   * Sent event has occurred at the current time.
   *
   * If annotations are provided, they will also be added.
   */
  def clientSent(span: Span, annotations: (String, String)*): Future[Option[ClientSpan]]

  /**
   * Creates a [[ClientSpan]] with a ClientReceived annotation that will be used to signal to the Zipkin collector that a Client
   * Received event has occurred at the current time.This method *should* be used to also actually send the underlying [[Span]]
   * to the Zipkin server.
   *
   * If annotations are provided, they will also be added.
   */
  def clientReceived(span: ClientSpan, annotations: (String, String)*): Future[Option[ClientSpan]]

  /**
   * Returns a [[Span]] based on a name and a parent [[Span]], which may or may not be used
   *
   * In essence, checks for a trace id on the parent [[Span]], if it exists, uses the parent
   * [[Span]]'s trace id as the returned [[Span]]'s trace id and the parent id as the returned
   * [[Span]]'s parentId.
   *
   * The returned [[Span]] is guaranteed to have a new id and the name passed.
   */
  def generateSpan(name: String, parent: Span): Span = {
    val newId = nextId
    val newSpan = new Span()
    newSpan.setId(newId)
    newSpan.setName(name)
    if (parent.isSetTrace_id && parent.isSetId) {
      newSpan.setTrace_id(parent.getTrace_id)
      newSpan.setParent_id(parent.getId)
    } else {
      newSpan.setTrace_id(newId)
    }
    newSpan
  }

  /**
   * Returns an id
   */
  def nextId: Long = scala.util.Random.nextLong.abs

  /**
   * Provides a way of turning a custom [[ServerSpan]] back into a normal [[Span]]
   */
  def serverSpanToSpan(serverSpan: ServerSpan): Span

  /**
   * Provides a way of turning a custom [[ClientSpan]] back into a normal [[Span]]
   */
  def clientSpanToSpan(clientSpan: ClientSpan): Span

  /**
   * Turns a [[Span]] into a Map[String, String].
   *
   * The keys of the map are the official Zipkin id Header strings (e.g. X-B3-TraceId), and the
   * values are the hexadecimal string versions of those ids.
   *
   * Useful turning a [[Span]] into a data structure that can be more easily serialised in
   * order to be passed onto other systems via some kind of transport protocol.
   */
  def spanToIdsMap(span: Span): Map[String, String] = {
    import HttpHeaders._
    Seq(
      (if (span.isSetTrace_id) Some(span.getTrace_id) else None, TraceIdHeaderKey),
      (if (span.isSetId) Some(span.getId) else None, SpanIdHeaderKey),
      (if (span.isSetParent_id) Some(span.getParent_id) else None, ParentIdHeaderKey)
    ).foldLeft(Map.empty[String, String]) {
        case (acc, (Some(id), headerKey)) => acc + (headerKey.toString -> longToHexString(id))
        case (acc, _) => acc
      }
  }

  private def longToHexString(id: Long): String = java.lang.Long.toHexString(id)

  /**
   * Determines if the [[Span]] is minimally sendable to Zipkin (has an id, has a trace id, and has a
   * non-empty name)
   */
  protected[services] def sendableSpan(span: Span): Boolean = {
    span.isSetId && span.isSetTrace_id && span.isSetName && span.getName.nonEmpty
  }

}
