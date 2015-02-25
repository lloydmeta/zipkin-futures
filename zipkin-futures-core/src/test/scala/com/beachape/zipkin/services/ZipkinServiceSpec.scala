package com.beachape.zipkin.services

import com.twitter.zipkin.gen.Span
import org.scalatest._

class ZipkinServiceSpec extends FunSpec with Matchers {

  val subject = NoopZipkinService

  describe("#sendableSpan") {

    it("should return false if a span has no set trace id") {
      val span = new Span()
      span.setId(123)
      span.setName("hello")
      subject.sendableSpan(span) shouldBe false
    }

    it("should return false if a span has no set id") {
      val span = new Span()
      span.setTrace_id(123)
      span.setName("hello")
      subject.sendableSpan(span) shouldBe false
    }

    it("should return false if a span has no name set or the name is empty") {
      val span1 = new Span()
      val span2 = new Span()
      Seq(span1, span2).foreach { s =>
        s.setId(123)
        s.setTrace_id(123)
      }
      span2.setName("")
      subject.sendableSpan(span1) shouldBe false
      subject.sendableSpan(span2) shouldBe false
    }

    it("should return true if a span has id, trace id, and a non-empty name") {
      val span = new Span()
      span.setId(123)
      span.setTrace_id(123)
      span.setName("hello")
      subject.sendableSpan(span) shouldBe true
    }
  }

  describe("#generateSpan") {

    it("should return a new Span with no Parent Id if there is no set id or traceId in the provided span") {
      val span1 = new Span()
      span1.setId(123)
      val notChild1 = subject.generateSpan("hello", span1)
      notChild1.isSetParent_id shouldBe false
      val span2 = new Span()
      span2.setTrace_id(123)
      val notChild2 = subject.generateSpan("helloagain", span2)
      notChild2.isSetParent_id shouldBe false
    }

    it("should return a new Span with a Parent Id if there is id and trace id provided in the span") {
      val span = new Span()
      span.setId(123)
      span.setTrace_id(123)
      val notChild = subject.generateSpan("hello", span)
      notChild.isSetParent_id shouldBe true
    }

  }

}
