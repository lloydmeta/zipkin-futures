# Zipkin-futures [![Build Status](https://travis-ci.org/lloydmeta/zipkin-futures.svg?branch=master)](https://travis-ci.org/lloydmeta/zipkin-futures) [![Coverage Status](https://coveralls.io/repos/lloydmeta/zipkin-futures/badge.svg)](https://coveralls.io/r/lloydmeta/zipkin-futures)

Hopefully provides a nice way to use Scala `Future`s with Zipkin tracing.

ATM mostly a wrapper around Brave, but can be extended to use other Zipkin libs by extending `ZipkinServiceLike`.

Also, mostly meant to be used with Play2.

For more information on Zipkin, checkout the [official docs](https://twitter.github.io/zipkin/). This readme and the rest
of the project assumes that you already have Zipkin infra set up, and know about CS,CR,SS,SR annotations and `Spans` in
general.

## SBT

```scala
libraryDependencies ++= Seq(
    "com.beachape" %% "zipkin-futures" % "0.0.4"  // OR
    "com.beachape" %% "zipkin-futures-play" % "0.0.4" // if you are using Play and want to use the filter w/ RequestHeader conversions
)
```

If the above does not work because it cannot be resolved, its likely because it hasn't been synced to Maven central yet.
In that case, download a SNAPSHOT release of the same version by adding this to build.sbt

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
    "com.beachape" %% "zipkin-futures" % "0.0.4-SNAPSHOT" // OR
    "com.beachape" %% "zipkin-futures-play" % "0.0.4-SNAPSHOT" // if you are using Play and want to use the filter w/ RequestHeader conversions
)
```

## Example

*Note* For more (up-to-date) details, refer to the tests.

In both of the following examples, a Zipkin `Span` will be created before the execution of the defined `Future` and
marked with a client-sent annotation (as well as the initially provided var arg annotations). Upon the completion of
the `Future`, the generated `Span` will be marked with a client-received annotation and sent to the Zipkin collector.

```scala

// Simple tracing
val myTracedFuture1 = TracedFuture("slowHttpCall") { maybeSpan =>
  val forwardHeaders = maybeSpan.fold(Seq.empty[(String,String)]){ toHttpHeaders }
  WS.url("myServer").withHeaders(forwardHeaders:_*)
}

// Tracing and setting annotations from the future before sending Client-Received ;)
val myTracedFuture2 = TracedFuture.endAnnotations("slowHttpCall") { maybeSpan =>
  val forwardHeaders = maybeSpan.fold(Seq.empty[(String,String)]){ toHttpHeaders }
  WS.url("myServer").withHeaders(forwardHeaders:_*).map { response =>
    (response.json, Seq("session id" -> response.header("session id").toString))
  }
}

```

## Example with Play

An example of how this can be used within a Play 2 app. *Note* Use without a Play app is possible, but you will need to figure
out how to provide an implicit `Span` to the trace function.

In `Global.scala` add a filter that looks for Zipkin headers in the incoming request if they're there, sends Spans to
 Zipkin with ServerReceived and ServerSent annotations once a result has come back up the set of filters, and
 injects Zipkin headers into the incoming request for other filters and controller actions.

```scala
object Global
  extends WithFilters(ZipkinHeaderFilter(new BraveZipkinService("localhost", 123, "testing-filter", collector)))
  with GlobalSettings
```

In your controllers or action filters, (e.g. in Application.scala), import the `RequestHeader` to `Span` converter and
the enrichment to `Future`.

```scala
import com.beachape.zipkin.Implicits._
import com.beachape.zipkin.FutureEnrichments._

class Application(implicit zipkinService: ZipkinServiceLike) extends Controller {

  import play.api.libs.concurrent.Execution.Implicits._

  def index = Action.async { implicit req =>
    // Syntactic sugar
    Future { Ok(expensiveResult) } trace ("expensive-process")
  }

}
```

## Licence

The MIT License (MIT)

Copyright (c) 2015 by Lloyd Chan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.