package com.beachape.zipkin

import com.beachape.zipkin.services.{ BraveZipkinService, DummyCollector }
import com.twitter.zipkin.gen.Span
import org.scalatest.concurrent.{ Eventually, PatienceConfiguration, IntegrationPatience, ScalaFutures }
import org.scalatest.{ Matchers, FunSpec }

import scala.concurrent.duration._
import scala.collection.JavaConverters._

/**
 * Created by Lloyd on 3/3/15.
 */
class TracedOpSpec extends FunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with PatienceConfiguration
    with Eventually {

  import scala.concurrent.ExecutionContext.Implicits.global

  describe("TracedFuture w/ a BraveZipkinService in scope") {

    def subjects(filters: Seq[Span => Boolean] = Seq.empty): (DummyCollector, BraveZipkinService) = {
      val collector = new DummyCollector
      (collector, new BraveZipkinService("localhost", 1234, "testing-only", collector, filters))
    }

    describe("simple") {
      it("should send a span with client sent and client received annotations and any binary annotations to the ZipkinCollector") {
        implicit val (collector, zipkin) = subjects()
        implicit val span = new Span()
        val i = TracedOp.simple("sleepy", "boom" -> "shakalaka") {
          Thread.sleep(2000.millis.toMillis)
          3
        }
        eventually { collector.collected().size shouldBe 1 }
        val collected = collector.collected()
        collected.size shouldBe 1
        val collectedSpan = collected.head
        val annotations = collectedSpan.getAnnotations.asScala
        annotations.map(_.getValue) should contain allOf ("cs", "cr")
        val csTime = annotations.find(_.getValue == "cs").head
        val crTime = annotations.find(_.getValue == "cr").head
        val diff = crTime.getTimestamp - csTime.getTimestamp
        diff.microseconds.toMicros should be(2000.millis.toMicros +- 300.millis.toMicros)
        val binaryAnnotations = collectedSpan.getBinary_annotations.asScala
        binaryAnnotations.size shouldBe 1
        binaryAnnotations.map(_.getKey) should contain("boom")
        binaryAnnotations.map(a => new String(a.getValue)) should contain("shakalaka")

      }

    }

    describe(".apply") {

      it("should send a span with client sent and client received annotations and any binary annotations to the ZipkinCollector") {
        implicit val (collector, zipkin) = subjects()
        implicit val span = new Span()
        val name = TracedOp("sleepy", "boom" -> "shakalaka") { maybeSpan =>
          Thread.sleep(2000.millis.toMillis)
          maybeSpan.map(_.getName).getOrElse("")
        }

        eventually { collector.collected().size shouldBe 1 }
        val collected = collector.collected()
        collected.size shouldBe 1
        val collectedSpan = collected.head
        val annotations = collectedSpan.getAnnotations.asScala
        annotations.map(_.getValue) should contain allOf ("cs", "cr")
        val csTime = annotations.find(_.getValue == "cs").head
        val crTime = annotations.find(_.getValue == "cr").head
        val diff = crTime.getTimestamp - csTime.getTimestamp
        diff.microseconds.toMicros should be(2000.millis.toMicros +- 300.millis.toMicros)

        val binaryAnnotations = collectedSpan.getBinary_annotations.asScala
        binaryAnnotations.size shouldBe 1
        binaryAnnotations.map(_.getKey) should contain("boom")
        binaryAnnotations.map(a => new String(a.getValue)) should contain("shakalaka")

      }

    }

    describe(".endAnnotations") {

      it("should send a span with client sent and client received annotations and any binary annotations to the ZipkinCollector") {
        implicit val (collector, zipkin) = subjects()
        implicit val span = new Span()
        val name = TracedOp.endAnnotations("sleepy", "Toronto" -> "Kinshicho") { maybeSpan =>
          Thread.sleep(2000.millis.toMillis)
          (maybeSpan.map(_.getName).getOrElse(""), Seq("Shibuya" -> "Mitaka"))
        }
        eventually { collector.collected().size shouldBe 1 }
        val collected = collector.collected()
        collected.size shouldBe 1
        val collectedSpan = collected.head
        val annotations = collectedSpan.getAnnotations.asScala
        annotations.map(_.getValue) should contain allOf ("cs", "cr")
        val csTime = annotations.find(_.getValue == "cs").head
        val crTime = annotations.find(_.getValue == "cr").head
        val diff = crTime.getTimestamp - csTime.getTimestamp
        diff.microseconds.toMicros should be(2000.millis.toMicros +- 300.millis.toMicros)
        val binaryAnnotations = collectedSpan.getBinary_annotations.asScala
        binaryAnnotations.size shouldBe 2
        binaryAnnotations.map(_.getKey) should contain allOf ("Toronto", "Shibuya")
        binaryAnnotations.map(a => new String(a.getValue)) should contain allOf ("Kinshicho", "Mitaka")

      }

    }

  }

}