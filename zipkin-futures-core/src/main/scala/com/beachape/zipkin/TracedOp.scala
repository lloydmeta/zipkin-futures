package com.beachape.zipkin

import com.beachape.zipkin.services.ZipkinServiceLike
import com.twitter.zipkin.gen.Span

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try
import scala.util.control.NonFatal

/**
 * Created by Lloyd on 3/3/15.
 */

/**
 * Helper methods for tracing synchronous operations
 */
trait TracedOp {

  /**
   * Duration of timeout to wait for the in-scope [[ZipkinServiceLike]] to produce a [[Span]] for sync tracing purposes
   */
  def timeout: FiniteDuration

  /**
   * The simplest kind synchronous tracing; traces the provided operation `f` using the provided traceName and annotations.
   *
   * This function expects that there is a parent [[Span]] in scope. Even if there is a [[Span]]
   * in scope, it may not be used as a Parent [[Span]] if it does not have the proper ids, namely
   * a span id and a trace id. In this case, a new scope with the given name and new ids will be used
   * and it will not have a parent id.
   *
   * If you do not need to have access to the generated [[Span]] used for tracing this operation, this is the best
   * synchronous tracing method to use, as it does not block the current thread in waiting for the [[Span]] to be
   * produced by the in-scope [[ZipkinServiceLike]]
   *
   * @param traceName the name for the span used for tracing
   * @param annotations variable list of annotations to send for tracing in the beginning (after the client sent
   *                    annotation)
   * @param f the function that will take an [A]
   */
  def simple[A](traceName: String, annotations: (String, String)*)(f: => A)(implicit parentSpan: Span, zipkinService: ZipkinServiceLike): A = {
    import zipkinService.eCtx
    val childSpan = zipkinService.generateSpan(traceName, parentSpan)
    val fMaybeSentCustomSpan = zipkinService.clientSent(childSpan, annotations: _*).recover { case NonFatal(e) => None }
    val result = f
    fMaybeSentCustomSpan.foreach { maybeSent =>
      maybeSent foreach { span =>
        zipkinService.clientReceived(span)
      }
    }
    result
  }

  /**
   * Traces the provided synchronous operation Option[Span] => A op, using the provided traceName and annotations.
   *
   * This function expects that there is a parent [[Span]] in scope. If there is even is a [[Span]]
   * in scope, it may not be used as a Parent [[Span]] if it does not have the proper ids, namely
   * a span id and a trace id. In this case, a new scope with the given name and new ids will be used
   * and it will not have a parent id.
   *
   * If the in scope [[ZipkinServiceLike]] does not provide a [[Span]] for the in scope parentSpan [[Span]] (or
   * if it fails) your function will be handed a None. Also, note that if the [[ZipkinServiceLike]] does not provide
   * a [[Span]] in the timeout duration [[timeout]], then your function will be handed a None.
   *
   * Note, the [[Span]] given to your function should not be mutated to affect tracing. It is a deep copy anyways, so
   * there will be no effect.
   *
   * @param traceName the name for the span used for tracing
   * @param annotations variable list of annotations to send for tracing in the beginning (after the client sent
   *                    annotation)
   * @param f the function that will take a Option[Span] and produce an [A]
   */
  def apply[A](traceName: String, annotations: (String, String)*)(f: Option[Span] => A)(implicit parentSpan: Span, zipkinService: ZipkinServiceLike): A = {
    endAnnotations(traceName, annotations: _*)(a => (f(a), Seq.empty))
  }

  /**
   * Traces the provided synchronous operation Option[Span] => A op, using the provided traceName and annotations.
   * The Option[Span] parameter is so that you can pass the span details toother systems in your A-producing function.
   * Note that this function expects the a [[Tuple2]] result, where the second element in the pair is a Seq[(String, String)]
   * set of annotations that you might want to use in order to set final annotations on the span.
   *
   * This function expects that there is a parent [[Span]] in scope. If there is even is a [[Span]]
   * in scope, it may not be used as a Parent [[Span]] if it does not have the proper ids, namely
   * a span id and a trace id. In this case, a new scope with the given name and new ids will be used
   * and it will not have a parent id.
   *
   * If the in scope [[ZipkinServiceLike]] does not provide a [[Span]] for the in scope parentSpan [[Span]] (or
   * if it fails) your function will be handed a None. Also, note that if the [[ZipkinServiceLike]] does not provide
   * a [[Span]] in the timeout duration [[timeout]], then your function will be handed a None.
   *
   * Note, the [[Span]] given to your function should not be mutated to affect tracing. It is a deep copy anyways, so
   * there will be no effect.
   *
   * @param traceName the name for the span used for tracing
   * @param annotations variable list of annotations to send for tracing in the beginning (after the client sent
   *                    annotation)
   * @param f the function that will take a Option[Span] and produce an [A]
   */
  def endAnnotations[A](traceName: String, annotations: (String, String)*)(f: Option[Span] => (A, Seq[(String, String)]))(implicit parentSpan: Span, zipkinService: ZipkinServiceLike): A = {
    import zipkinService.eCtx
    val childSpan = zipkinService.generateSpan(traceName, parentSpan)
    val fMaybeSentCustomSpan = zipkinService.clientSent(childSpan, annotations: _*).recover { case NonFatal(e) => None }
    //Tries to wait for a certain amount of time for the ZipkinService to provide a span to to use
    val maybeSentProvided = Try(Await.result(fMaybeSentCustomSpan, timeout)).getOrElse(None)
    val result = f(maybeSentProvided.map(zipkinService.clientSpanToSpan(_).deepCopy()))
    fMaybeSentCustomSpan.foreach { maybeActuallySent =>
      maybeActuallySent orElse maybeSentProvided foreach { span =>
        zipkinService.clientReceived(span, result._2: _*)
      }
    }
    result._1
  }

}

/**
 * TraceOp companion object that sets a 50 millisecond timeout
 */
object TracedOp extends TracedOp {

  val timeout: FiniteDuration = 50.millis
}