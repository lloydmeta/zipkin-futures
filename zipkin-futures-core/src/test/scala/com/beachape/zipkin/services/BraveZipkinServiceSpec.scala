package com.beachape.zipkin.services

import com.twitter.zipkin.gen.Span
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import scala.collection.JavaConverters._

class BraveZipkinServiceSpec extends FunSpec with Matchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  def subjects(filters: Seq[String => Boolean] = Seq.empty): (DummyCollector, BraveZipkinService) = {
    val collector = new DummyCollector
    (collector, new BraveZipkinService("localhost", 1234, "testing-only", collector, filters))
  }

  def span(spanId: Option[Long], traceId: Option[Long], name: Option[String]): Span = {
    val span = new Span()
    spanId.foreach(id => span.setId(id))
    traceId.foreach(id => span.setTrace_id(id))
    name.foreach(name => span.setName(name))
    span
  }

  describe("#serverReceived") {

    it("should return a ServerSpan with a received annotation") {
      val (_, service) = subjects()
      whenReady(service.serverReceived(span(Some(123), Some(123), Some("blah")))) { r =>
        val Some(serverSpan) = r
        val underlyingSpan = serverSpan.getSpan
        val annotations = underlyingSpan.getAnnotations.asScala
        annotations.size shouldBe 1
        annotations(0).getValue shouldBe "sr"
      }
    }

    it("should return a ServerSpan with the set binary annotations") {
      val (_, service) = subjects()
      whenReady(service.serverReceived(span(Some(123), Some(123), Some("blah")), "i_say" -> "hello", "you_say" -> "goodbye")) { r =>
        val Some(serverSpan) = r
        val underlyingSpan = serverSpan.getSpan
        val binaryAnnotations = underlyingSpan.getBinary_annotations.asScala
        binaryAnnotations.size shouldBe 2
        binaryAnnotations.map(a => new String(a.getValue)) should contain allOf ("hello", "goodbye")
        binaryAnnotations.map(_.getKey) should contain allOf ("i_say", "you_say")
      }
    }

    it("should return None if given a Span with a name that is filtered out") {
      val (_, service) = subjects(Seq({ s => s != "no" }))
      whenReady(service.serverReceived(span(Some(123), Some(123), Some("no")))) { _ shouldBe None }
    }

  }

  describe("#serverSent") {

    it("should return a ServerSpan with a sent annotation") {
      val (_, service) = subjects()
      whenReady(service.serverReceived(span(Some(123), Some(123), Some("blah")))) { r =>
        val Some(serverSpan) = r
        whenReady(service.serverSent(serverSpan)) { r =>
          val Some(serverSpan) = r
          val underlyingSpan = serverSpan.getSpan
          val annotations = underlyingSpan.getAnnotations.asScala
          annotations.size shouldBe 2
          annotations(0).getValue shouldBe "sr"
          annotations(1).getValue shouldBe "ss"
        }
      }
    }

    it("should return a ServerSpan with the set binary annotations") {
      val (_, service) = subjects()
      whenReady(service.serverReceived(span(Some(123), Some(123), Some("blah")), "i_say" -> "hello", "you_say" -> "goodbye")) { r =>
        val Some(serverSpan) = r
        whenReady(service.serverSent(serverSpan, "who" -> "what", "when" -> "where")) { r =>
          val Some(serverSpan) = r
          val underlyingSpan = serverSpan.getSpan
          val binaryAnnotations = underlyingSpan.getBinary_annotations.asScala
          binaryAnnotations.size shouldBe 4
          binaryAnnotations.map(a => new String(a.getValue)) should contain allOf ("hello", "goodbye", "what", "where")
          binaryAnnotations.map(_.getKey) should contain allOf ("i_say", "you_say", "who", "when")
        }
      }
    }

    it("should 'send' a Span to the collector") {
      val (collector, service) = subjects()
      whenReady(service.serverReceived(span(Some(123), Some(123), Some("blah")))) { r =>
        val Some(serverSpan) = r
        whenReady(service.serverSent(serverSpan)) { r =>
          val collected = collector.collected()
          collected should not be 'empty
        }
      }
    }

  }

  describe("#clientSent") {

    it("should return a Span with a sent annotation") {
      val (_, service) = subjects()
      whenReady(service.clientSent(span(Some(123), Some(123), Some("blah")))) { r =>
        val Some(clientSpan) = r
        val annotations = clientSpan.getAnnotations.asScala
        annotations.size shouldBe 1
        annotations(0).getValue shouldBe "cs"
      }
    }

    it("should return a Span with the set binary annotations") {
      val (_, service) = subjects()
      whenReady(service.clientSent(span(Some(123), Some(123), Some("blah")), "i_say" -> "hello", "you_say" -> "goodbye")) { r =>
        val Some(clientSpan) = r
        val binaryAnnotations = clientSpan.getBinary_annotations.asScala
        binaryAnnotations.size shouldBe 2
        binaryAnnotations.map(a => new String(a.getValue)) should contain allOf ("hello", "goodbye")
        binaryAnnotations.map(_.getKey) should contain allOf ("i_say", "you_say")
      }
    }

    it("should return None if given a Span with a name that is filtered out") {
      val (_, service) = subjects(Seq({ s => s != "no" }))
      whenReady(service.clientSent(span(Some(123), Some(123), Some("no")))) { _ shouldBe None }
    }

  }

  describe("#clientReceived") {

    it("should return a Span with a sent annotation") {
      val (_, service) = subjects()
      whenReady(service.clientSent(span(Some(123), Some(123), Some("blah")))) { r =>
        val Some(clientSpan) = r
        whenReady(service.clientReceived(clientSpan)) { r =>
          val Some(clientSpanReceived) = r
          val annotations = clientSpanReceived.getAnnotations.asScala
          annotations.size shouldBe 2
          annotations(0).getValue shouldBe "cs"
          annotations(1).getValue shouldBe "cr"
        }
      }
    }

    it("should return a ServerSpan with the set binary annotations") {
      val (_, service) = subjects()
      whenReady(service.clientSent(span(Some(123), Some(123), Some("blah")), "i_say" -> "hello", "you_say" -> "goodbye")) { r =>
        val Some(clientSpan) = r
        whenReady(service.clientReceived(clientSpan, "who" -> "what", "when" -> "where")) { r =>
          val Some(clientSpanReceived) = r
          val binaryAnnotations = clientSpanReceived.getBinary_annotations.asScala
          binaryAnnotations.size shouldBe 4
          binaryAnnotations.map(a => new String(a.getValue)) should contain allOf ("hello", "goodbye", "what", "where")
          binaryAnnotations.map(_.getKey) should contain allOf ("i_say", "you_say", "who", "when")
        }
      }
    }

    it("should 'send' a Span to the collector") {
      val (collector, service) = subjects()
      whenReady(service.clientSent(span(Some(123), Some(123), Some("blah")))) { r =>
        val Some(clientSpan) = r
        whenReady(service.clientReceived(clientSpan)) { r =>
          val collected = collector.collected()
          collected should not be 'empty
        }
      }
    }

  }

}
