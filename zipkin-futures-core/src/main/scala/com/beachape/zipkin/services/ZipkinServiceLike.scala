package com.beachape.zipkin.services

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
   * Returns a [[ServerSpan]] with a ServerReceived annotation that will later be used to signal to the Zipkin collector
   * that a server received event has occurred at the current time.
   *
   * If annotations are provided, they will also be added.
   */
  def serverReceived(span: Span, annotations: (String, String)*)(implicit eCtx: ExecutionContext): Future[Option[ServerSpan]]

  /**
   * Returns a [[ServerSpan]] with a ServerSent annotation that will be used to signal to the Zipkin collector that a server
   * Sent event has occurred at the current time. This method *should* be used to also actually send the underlying [[Span]]
   * to the Zipkin server.
   *
   * If annotations are provided, they will also be added.
   */
  def serverSent(span: ServerSpan, annotations: (String, String)*)(implicit eCtx: ExecutionContext): Future[Option[ServerSpan]]

  /**
   * Creates a [[ClientSpan]] with a ClientSent annotation that will later be used to signal to the Zipkin collector that a Client
   * Sent event has occurred at the current time.
   *
   * If annotations are provided, they will also be added.
   */
  def clientSent(span: Span, annotations: (String, String)*)(implicit eCtx: ExecutionContext): Future[Option[ClientSpan]]

  /**
   * Creates a [[ClientSpan]] with a ClientReceived annotation that will be used to signal to the Zipkin collector that a Client
   * Received event has occurred at the current time.This method *should* be used to also actually send the underlying [[Span]]
   * to the Zipkin server.
   *
   * If annotations are provided, they will also be added.
   */
  def clientReceived(span: ClientSpan, annotations: (String, String)*)(implicit eCtx: ExecutionContext): Future[Option[ClientSpan]]

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
    if (Option(parent.getTrace_id).filterNot(_ == 0).isDefined && Option(parent.getId).filterNot(_ == 0).isDefined) {
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
   * Determines if the [[Span]] is minimally sendable to Zipkin (has an id, has a trace id, and has a
   * non-empty name)
   */
  protected[services] def sendableSpan(span: Span): Boolean = {
    !(Option(span.getId).filterNot(_ == 0).isEmpty ||
      Option(span.getTrace_id).filterNot(_ == 0).isEmpty ||
      Option(span.getName).filterNot(_.isEmpty).isEmpty)
  }

}
