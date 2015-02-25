package com.beachape.zipkin

import com.beachape.zipkin.services.{ BraveZipkinService, DummyCollector }
import org.scalatest._
import org.scalatest.concurrent.{ IntegrationPatience, PatienceConfiguration, ScalaFutures }
import play.api.mvc.{ Results, RequestHeader }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import scala.collection.JavaConverters._
import scala.concurrent.duration._

import scala.concurrent.Future

class ZipkinHeaderFilterSpec extends FunSpec with Matchers with ScalaFutures with Results with IntegrationPatience with PatienceConfiguration {

  import scala.concurrent.ExecutionContext.Implicits.global

  def collectorAndFilter = {
    val collector = new DummyCollector
    (collector, ZipkinHeaderFilter(new BraveZipkinService("localhost", 123, "testing-filter", collector)))
  }
  val headersToString = { r: RequestHeader => Future.successful(Ok(r.headers.toMap)) }

  describe("#apply") {

    it("should provide the inner filters w/ Zipkin headers") {
      import HttpHeaders._
      val (_, subject) = collectorAndFilter
      val fResult = subject.apply(headersToString)(FakeRequest())
      contentAsString(fResult) should (include(ParentIdHeaderKey.toString) and include(TraceIdHeaderKey.toString) and include(SpanIdHeaderKey.toString))
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
        val collectedSpans = collector.collected()
        collectedSpans.size shouldBe 1
        val span = collectedSpans.head
        val annotations = span.getAnnotations.asScala
        annotations.size shouldBe 2
        annotations(0).getValue should be("sr")
        annotations(1).getValue should be("ss")
        val diff = annotations(1).getTimestamp - annotations(0).getTimestamp
        diff.microseconds should be >= 150.millis.toMicros.microseconds
      }
    }

  }

}
