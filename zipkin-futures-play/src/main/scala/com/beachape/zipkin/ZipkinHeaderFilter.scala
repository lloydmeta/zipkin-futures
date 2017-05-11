package com.beachape.zipkin

import akka.stream.Materializer
import com.beachape.zipkin.services.ZipkinServiceLike
import com.twitter.zipkin.gen.Span
import play.api.mvc.{ Filter, Headers, RequestHeader, Result }
import play.api.routing.Router

import scala.concurrent.{ ExecutionContext, Future }
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
class ZipkinHeaderFilter(zipkinServiceFactory: => ZipkinServiceLike, reqHeaderToSpanName: RequestHeader => String)(implicit val mat: Materializer, eCtx: ExecutionContext) extends Filter {

  import Implicits._

  private implicit lazy val zipkinService = zipkinServiceFactory

  def apply(nextFilter: (RequestHeader) => Future[Result])(req: RequestHeader): Future[Result] = {
    val parentSpan = zipkinService.generateSpan(reqHeaderToSpanName(req), req2span(req))
    val fMaybeServerSpan = zipkinService.serverReceived(parentSpan).recover { case NonFatal(e) => None }
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
    }
  }

  private def addHeadersToReq(req: RequestHeader, span: Span): RequestHeader = {
    val originalHeaderData = req.headers.toMap
    val withSpanData = originalHeaderData ++ zipkinService.spanToIdsMap(span).map { case (key, value) => key -> Seq(value) }
    val newHeaders = new Headers(withSpanData.mapValues(_.headOption.getOrElse("")).toSeq)
    req.withHeaders(newHeaders)
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
  def apply(zipkinServiceFactory: => ZipkinServiceLike, reqHeaderToSpanName: RequestHeader => String = ParamAwareRequestNamer)(implicit mat: Materializer, eCtx: ExecutionContext): ZipkinHeaderFilter =
    new ZipkinHeaderFilter(zipkinServiceFactory, reqHeaderToSpanName)

  /**
   * A convenient default RequestHeader => String transformer that removes unique
   * param values in the request uri, instead replacing them with their param names
   * so that all requests to a route get the same Span name.
   */
  val ParamAwareRequestNamer: RequestHeader => String = { reqHeader =>
    import org.apache.commons.lang3.StringUtils
    val rawPathPattern = reqHeader.attrs.get(Router.Attrs.HandlerDef).map(_.path).getOrElse(reqHeader.path)
    val pathPattern = StringUtils.replace(rawPathPattern, "<[^/]+>", "")
    s"${reqHeader.method} - $pathPattern"
  }

}