package com.beachape.zipkin

import org.scalatest._
import play.api.test.FakeRequest

class ImplicitsSpec extends FunSpec with Matchers {

  import Implicits._

  describe("#req2span") {

    def longToHexString(l: Long): String = java.lang.Long.toHexString(l)

    it(s"should grab ${HttpHeaders.values.mkString(", ")} from the request if present") {
      val span1 = req2span(FakeRequest().withHeaders("X-B3-TraceId" -> longToHexString(4214124)))
      val span2 = req2span(FakeRequest().withHeaders("X-B3-SpanId" -> longToHexString(912412487)))
      val span3 = req2span(FakeRequest().withHeaders("X-B3-ParentSpanId" -> longToHexString(4124212)))
      val span4 = req2span(FakeRequest().withHeaders("X-B3-ParentSpanId" -> longToHexString(1235), "X-B3-TraceId" -> longToHexString(63461)))
      val span5 = req2span(FakeRequest().withHeaders("X-B3-SpanId" -> longToHexString(123253), "X-B3-TraceId" -> longToHexString(43643), "X-B3-ParentSpanId" -> longToHexString(87897)))
      span1.getTrace_id shouldBe 4214124
      span2.getId shouldBe 912412487
      span3.getParent_id shouldBe 4124212
      span4.getTrace_id shouldBe 63461
      span4.getParent_id shouldBe 1235
      span5.getId shouldBe 123253
      span5.getTrace_id shouldBe 43643
      span5.getParent_id shouldBe 87897
    }

  }

}
