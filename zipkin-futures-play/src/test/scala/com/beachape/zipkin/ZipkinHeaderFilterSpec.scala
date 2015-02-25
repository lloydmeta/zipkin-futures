package com.beachape.zipkin

import com.beachape.zipkin.services.{ BraveZipkinService, DummyCollector }
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, IntegrationPatience, PatienceConfiguration, ScalaFutures }
import play.api.mvc.{ Results, RequestHeader }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import scala.collection.JavaConverters._
import scala.concurrent.duration._

import scala.concurrent.Future

class ZipkinHeaderFilterSpec
    extends FunSpec
    with Matchers
    with ScalaFutures
    with Results
    with Eventually
    with IntegrationPatience
    with PatienceConfiguration {

  import scala.concurrent.ExecutionContext.Implicits.global

  def collectorAndFilter = {
    val collector = new DummyCollector
    (collector, ZipkinHeaderFilter(new BraveZipkinService("localhost", 123, "testing-filter", collector)))
  }
  val headersToString = { r: RequestHeader => Future.successful(Ok(r.headers.toMap)) }

  describe("#apply") {

    import HttpHeaders._

    it("should provide the inner filters w/ Zipkin headers") {
      val (_, subject) = collectorAndFilter
      val fResult = subject.apply(headersToString)(FakeRequest())
      contentAsString(fResult) should (include(ParentIdHeaderKey.toString) and include(TraceIdHeaderKey.toString) and include(SpanIdHeaderKey.toString))
    }

    it("should use a Span defined via request headers as a parent") {
      val (_, subject) = collectorAndFilter
      val fResult = subject.apply(headersToString)(FakeRequest().withHeaders(TraceIdHeaderKey.toString -> "123", SpanIdHeaderKey.toString -> "456"))
      contentAsString(fResult) should (include(s"${ParentIdHeaderKey.toString}=456") and include(s"${TraceIdHeaderKey.toString}=123"))
    }

    it("should preserve any other random request headers by default") {
      val (_, subject) = collectorAndFilter
      val fResult = subject.apply(headersToString)(FakeRequest().withHeaders("X-Forwarded-For" -> "1.2.3.4"))
      contentAsString(fResult) should (include("X-Forwarded-For=1.2.3.4"))
    }

    it("should log the processing time to Zipkin") {
      val (collector, subject) = collectorAndFilter
      val slowProcessing = { r: RequestHeader =>
        Future {
          Thread.sleep(150.millis.toMillis)
          Ok("boom")
        }
      }
      val fResult = subject.apply(slowProcessing)(FakeRequest())
      whenReady(fResult) { _ =>
        eventually { collector.collected().size shouldBe 1 }
        val collectedSpans = collector.collected()
        collectedSpans.size shouldBe 1
        val span = collectedSpans.head
        val annotations = span.getAnnotations.asScala
        annotations.size shouldBe 2
        annotations(0).getValue should be("sr")
        annotations(1).getValue should be("ss")
        val diff = annotations(1).getTimestamp - annotations(0).getTimestamp
        diff.microseconds.toMicros should be(150.millis.toMicros +- 100.millis.toMicros)
      }
    }

  }

}
