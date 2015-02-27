package com.beachape.zipkin

import java.math.BigInteger

import com.twitter.zipkin.gen.Span
import play.api.mvc.RequestHeader

import scala.language.implicitConversions
import scala.util.Try

object Implicits extends ReqHeaderToSpanImplicit

trait ReqHeaderToSpanImplicit {

  /**
   * Converts a [[RequestHeader]] into a Zipkin [[Span]]
   */
  implicit def req2span(implicit req: RequestHeader): Span = {
    val span = new Span
    import HttpHeaders._
    def hexStringToLong(s: String): Long = {
      new BigInteger(s, 16).longValue()
    }
    def ghettoBind(headerKey: HttpHeaders.Value): Option[Long] = for {
      idString <- req.headers.get(headerKey.toString)
      id <- Try(hexStringToLong(idString)).toOption
    } yield id
    ghettoBind(TraceIdHeaderKey).foreach(span.setTrace_id)
    ghettoBind(SpanIdHeaderKey).foreach(span.setId)
    ghettoBind(ParentIdHeaderKey).foreach(span.setParent_id)
    span
  }

}