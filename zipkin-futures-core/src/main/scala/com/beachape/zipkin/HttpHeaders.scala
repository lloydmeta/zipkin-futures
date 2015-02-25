package com.beachape.zipkin

/**
 * Holds the Http header key names for Zipkin headers
 */
object HttpHeaders extends Enumeration {

  val TraceIdHeaderKey = Value("X-B3-TraceId")
  val SpanIdHeaderKey = Value("X-B3-SpanId")
  val ParentIdHeaderKey = Value("X-B3-ParentSpanId")

  val stringValues = values.map(_.toString)

}