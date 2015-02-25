package com.beachape.zipkin.services

import com.github.kristofa.brave.SpanCollector
import com.twitter.zipkin.gen.Span

/**
 * A none-threadsafe collector
 */
class DummyCollector extends SpanCollector {

  private var collectedStuff = Seq.empty[Span]

  /**
   * Returns the collected sequence
   */
  def collected(): Seq[Span] = synchronized(collectedStuff)

  /**
   * Clears the collected sequence
   */
  def clear(): Unit = collectedStuff = synchronized(Seq.empty)

  def collect(span: Span): Unit = synchronized {
    collectedStuff = collectedStuff :+ span
  }

  def close(): Unit = ()

  def addDefaultAnnotation(key: String, value: String): Unit = ()
}
