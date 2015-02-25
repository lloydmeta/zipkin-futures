package com.beachape.zipkin

import com.beachape.zipkin.services.{ BraveZipkinService, DummyCollector }
import com.twitter.zipkin.gen.Span
import org.scalatest._
import scala.collection.JavaConverters._
import org.scalatest.concurrent.{ Eventually, PatienceConfiguration, IntegrationPatience, ScalaFutures }

import scala.concurrent.Future
import scala.concurrent.duration._

class FutureEnrichmentSpec
    extends FunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with PatienceConfiguration
    with Eventually {

  import scala.concurrent.ExecutionContext.Implicits.global

  describe("Future#trace w/ a BraveZipkinService in scope") {

    import FutureEnrichment._

    def subjects(filters: Seq[String => Boolean] = Seq.empty): (DummyCollector, BraveZipkinService) = {
      val collector = new DummyCollector
      (collector, new BraveZipkinService("localhost", 1234, "testing-only", collector, filters))
    }

    it("should send a span with client sent and client received annotations to the ZipkinCollector") {
      implicit val (collector, zipkin) = subjects()
      implicit val span = new Span()
      val f = Future {
        Thread.sleep(2000.millis.toMillis)
        1
      }.trace("sleepy")
      whenReady(f) { _ =>
        eventually { collector.collected().size shouldBe 1 }
        val collected = collector.collected()
        collected.size shouldBe (1)
        val span = collected.head
        val annotations = span.getAnnotations.asScala
        annotations.map(_.getValue) should contain allOf ("cs", "cr")
        val csTime = annotations.find(_.getValue == "cs").head
        val crTime = annotations.find(_.getValue == "cr").head
        val diff = crTime.getTimestamp - csTime.getTimestamp
        diff.microseconds.toMicros should be(2000.millis.toMicros +- 200.millis.toMicros)
      }
    }

  }

}