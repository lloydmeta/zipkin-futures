package com.beachape.zipkin

import com.beachape.zipkin.services.ZipkinServiceLike
import com.twitter.zipkin.gen.Span
import play.api.mvc.{ Headers, Result, Filter, RequestHeader }

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.Failure

/**
 * A Global filter that should be inserted at the beginning of the filter list for monitoring purposes.
 *
 * Most of the work is delegated, but on a high-level, this is a Filter that intercepts [[RequestHeader]]s and:
 *
 *   1. Checks to see if there are Zipkin-related Http headers in the request
 *     - If there are headers that represent a parent [[Span]], creates a new child [[Span]]
 *     - If there aren't headers that represent a parent [[Span]], creates a new [[Span]]
 *   2. Adds a ServerReceived annotation to the [[Span]]
 *   3. Adds Zipkin header key-value pairs to the [[RequestHeader]]'s header so that the they are available inside
 *      the app for further processing by in controllers or further filters
 *   4. Upon completion of the request, adds a ServerSent annotation to the [[Span]] and sends it to the Zipkin Collector
 *
 */
class ZipkinHeaderFilter(zipkinServiceFactory: => ZipkinServiceLike, reqHeaderToSpanName: RequestHeader => String) extends Filter {

  import play.api.libs.concurrent.Execution.Implicits._
  import Implicits._

  private implicit lazy val zipkinService = zipkinServiceFactory

  def apply(nextFilter: (RequestHeader) => Future[Result])(req: RequestHeader): Future[Result] = {
    val parentSpan = zipkinService.generateSpan(reqHeaderToSpanName(req), req2span(req))
    val fMaybeServerSpan = zipkinService.serverReceived(parentSpan)
    fMaybeServerSpan flatMap {
      case None => nextFilter(req)
      case Some(serverSpan) => {
        val fResult = nextFilter(addHeadersToReq(req, zipkinService.serverSpanToSpan(serverSpan)))
        fResult.onComplete {
          case Failure(e) => zipkinService.serverSent(serverSpan, "failed" -> s"Finished with exception: ${e.getMessage}")
          case _ => zipkinService.serverSent(serverSpan)
        }
        fResult
      }
    } recoverWith {
      case NonFatal(e) => nextFilter(req)
    }
  }

  private def addHeadersToReq(req: RequestHeader, span: Span): RequestHeader = {
    val originalHeaderData = req.headers.toMap
    val withSpanData = originalHeaderData ++ zipkinService.spanToIdsMap(span).map { case (key, value) => key -> Seq(value) }
    val newHeaders = new Headers {
      protected val data: Seq[(String, Seq[String])] = withSpanData.toSeq
    }
    req.copy(headers = newHeaders)
  }

}

object ZipkinHeaderFilter {

  /**
   * Provides sugar for creating a new [[ZipkinHeaderFilter]]
   *
   * @param zipkinServiceFactory this will be used to instantiate a [[ZipkinServiceLike]] to be used with this filter.
   *                             It is called a "factory" to emphasise the fact that it will be lazily instantiated
   *                             so that it is safe depend on a running app.
   * @param reqHeaderToSpanName a method for turning a [[RequestHeader]] into a [[Span]] name. By default just uses the
   *                            [[RequestHeader]]#path
   */
  def apply(zipkinServiceFactory: => ZipkinServiceLike, reqHeaderToSpanName: RequestHeader => String = _.path): ZipkinHeaderFilter =
    new ZipkinHeaderFilter(zipkinServiceFactory, reqHeaderToSpanName)
}