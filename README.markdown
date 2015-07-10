# Zipkin-futures [![Build Status](https://travis-ci.org/lloydmeta/zipkin-futures.svg?branch=master)](https://travis-ci.org/lloydmeta/zipkin-futures) [![Coverage Status](https://coveralls.io/repos/lloydmeta/zipkin-futures/badge.svg)](https://coveralls.io/r/lloydmeta/zipkin-futures) [![Codacy Badge](https://www.codacy.com/project/badge/35ba8799c5a1487ca0a7f659a861c944)](https://www.codacy.com/public/lloydmeta/zipkin-futures)

Hopefully provides a nice way to use Scala `Future`s (also supports synchronous operation tracing via `TracedOp`) with Zipkin tracing.

ATM mostly a wrapper around Brave, but can be extended to use other Zipkin libs by extending `ZipkinServiceLike`.

For more information on Zipkin, checkout the [official docs](https://twitter.github.io/zipkin/). This readme and the rest
of the project assume that you already have Zipkin infra set up, and know about CS, CR, SS, SR annotations and `Span`s in
general.

## SBT

```scala
libraryDependencies ++= Seq(
    "com.beachape" %% "zipkin-futures" % "0.1.1"  // OR
    "com.beachape" %% "zipkin-futures-play" % "0.1.1" // if you are using Play and want to use the filter w/ RequestHeader conversions
)
```

If the above does not work because it cannot be resolved, it's likely because it hasn't been synced to Maven central yet.
In that case, download a SNAPSHOT release of the same version by adding this to build.sbt

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
    "com.beachape" %% "zipkin-futures" % "0.1.1-SNAPSHOT" // OR
    "com.beachape" %% "zipkin-futures-play" % "0.1.1-SNAPSHOT" // if you are using Play and want to use the filter w/ RequestHeader conversions
)
```

## Example usage

### Simple

*Note*: for more (up-to-date) details, refer to the tests.

In general, an existing `Span` and a `ZipkinServiceLike` is expected to be in scope. The dependence on an existing scope
is because Zipkin traces form a hierarchy; if you don't want to attach the trace of your `Future` to a parent `Span`, then
simply create an empty `Span`. The `ZipkinServiceLike` is needed to properly handle sending `Spans` to your Zipkin
collector, but in general there should just be one for a running application. `Zipkin-futures` comes out of the box w/ a
`BraveZipkinService` that implements `ZipkinServiceLike`.

```scala
/*
 A simple new Span that tells us we have no parent here. This means that any Traces here will be client sent/retrieved
 Spans that have no parent. If the in-scope Span has a Trace Id and a Span Id, the in-scope Span's Trace Id will be
 propagated to the Spans generated when tracing and the in-scope Span's id will be set as parent span id as well.
*/
implicit val span = new Span()
// Using a simple LoggingSpanCollectorImpl here as an example, but it can be a ZipkinSpanCollector that actually sends spans
implicit val zipkinService = new BraveZipkinService("localhost", 9000, "testing", new LoggingSpanCollectorImpl("application"))

val myTracedFuture1 = TracedFuture("slowHttpCall") //etc
```

In both of the following examples, a new Zipkin `Span` will be created before the execution of the defined `Future` and
marked with a client-sent annotation (as well as the initially provided var arg annotations). Upon the completion of
the `Future`, the generated `Span` will be marked with a client-received annotation and sent to the Zipkin collector.

```scala
// Synchronous op tracing
val traced = TracedOp.simple("slow") { calculate }

// Simple tracing w/ a block that takes the newly generated Option[Span]
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

import com.beachape.zipkin.FutureEnrichments._ // Sugar

// If you don't need access to the newly generated Span, syntactic sugar can be nice.
Future { Ok(expensiveResult) } trace ("expensive-process")

```

### With Play

`"com.beachape" %% "zipkin-futures-play"` defines a `zipkin-futures-play` dependency that helps trace Futures within
the context of an HTTP Play server.

The main addition is that there is a Global Filter that takes into account existing HTTP Zipkin headers on incoming
requests (if they exist and define a `Span`, it will be used as a parent Span for the server trace), sends Server
Received/Sent `Span`s, and propagates new Zipkin header values into your app (by putting them in the Request headers).

In addition, by extending `ReqHeaderToSpanImplicit` or importing the implicit, wherever you have an implicit `RequestHeader`,
you can easily pull a parent `Span` into scope if one exists. This means that if you use put the included Filter in your app
and your Actions have implicit requests in scope, this project will help automate making a "server" `Span`, propagating
it to your controllers, and making the included Future tracing functions use `Span`s that are children of the "server" `Span`.
For more info, please refer to the tests.

#### Example

In `Global.scala` add a filter that looks for Zipkin headers in the incoming request if they're there, sends Spans to
Zipkin with ServerReceived and ServerSent annotations once a result has come back up the set of filters, and injects
Zipkin headers into the incoming request for other filters and controller actions.

```scala
object Global
  extends WithFilters(ZipkinHeaderFilter(new BraveZipkinService("localhost", 123, "testing-filter", collector)))
  with GlobalSettings
```

In your controllers or action filters, (e.g. in Application.scala), import the `RequestHeader` to `Span` converter and
trace your `Future`s!

```scala
class Application(implicit zipkinService: ZipkinServiceLike) extends Controller with ReqHeaderToSpanImplicit {

  import com.beachape.zipkin.FutureEnrichments._

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
