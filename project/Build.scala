import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._
import scoverage.ScoverageSbtPlugin.ScoverageKeys._

object ZipkinFutures extends Build {

  lazy val theVersion = "0.1.2-SNAPSHOT"
  lazy val theScalaVersion = "2.11.5"
  lazy val scalaVersionsToBuild = Seq("2.10.4", "2.11.5")
  lazy val braveVersion = "2.4.1"

  lazy val root = Project(id = "zipkin-futures-root", base = file("."), settings = commonWithPublishSettings)
    .settings(
      name := "zipkin-futures-root",
      publishArtifact := false,
      crossScalaVersions := scalaVersionsToBuild,
      crossVersion := CrossVersion.binary
    ).aggregate(core, zipkinFuturesPlay)

  lazy val core = Project(id = "zipkin-futures", base = file("zipkin-futures-core"), settings = commonWithPublishSettings)
    .settings(
      name := "zipkin-futures",
      crossScalaVersions := scalaVersionsToBuild,
      crossVersion := CrossVersion.binary,
      libraryDependencies ++= braveDependencies ++ Seq(
        "org.scalatest" %% "scalatest" % "2.2.1" % Test
      )
    )

  lazy val zipkinFuturesPlay = Project(id = "zipkin-futures-play", base = file("zipkin-futures-play"), settings = commonWithPublishSettings)
    .settings(
      crossScalaVersions := scalaVersionsToBuild,
      crossVersion := CrossVersion.binary,
      libraryDependencies ++= braveDependencies ++ Seq(
        "com.typesafe.play" %% "play" % "2.3.8" % Provided,
        "org.scalatestplus" %% "play" % "1.2.0" % Test,
        "org.scalatest" %% "scalatest" % "2.2.1" % Test
      )
    ).dependsOn(core % "test->test;compile->compile")

  lazy val braveDependencies =  Seq(
    "com.github.kristofa" % "brave-zipkin-spancollector" % braveVersion,
    "com.github.kristofa" % "brave-impl" % braveVersion
  )

  lazy val commonSettings = Seq(
    organization := "com.beachape",
    version := theVersion,
    scalaVersion := theScalaVersion
  ) ++
    scalariformSettings ++
    scoverageSettings ++
    formatterPrefs ++
    compilerSettings ++
    resolverSettings ++
    ideSettings ++
    testSettings

  lazy val formatterPrefs = Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, true)
      .setPreference(DoubleIndentClassDeclaration, true)
  )

  lazy val commonWithPublishSettings =
    commonSettings ++
      publishSettings

  lazy val resolverSettings = Seq(
    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )

  lazy val ideSettings = Seq(
    // Faster "sbt gen-idea"
    transitiveClassifiers in Global := Seq(Artifact.SourceClassifier)
  )

  lazy val compilerSettings = Seq(
    // the name-hashing algorithm for the incremental compiler.
    incOptions := incOptions.value.withNameHashing(nameHashing = true),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-Xlog-free-terms")
  )

  lazy val testSettings = Seq(Test).flatMap { t =>
    Seq(parallelExecution in t := false) // Avoid DB-related tests stomping on each other
  }

  lazy val scoverageSettings = Seq(
    coverageExcludedPackages := """.*Noop.*;.*HttpHeaders""",
    coverageHighlighting := true
  )

  // Settings for publishing to Maven Central
  lazy val publishSettings = Seq(
    pomExtra :=
      <url>https://github.com/lloydmeta/zipkin-futures</url>
        <licenses>
          <license>
            <name>MIT</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:lloydmeta/zipkin-futures.git</url>
          <connection>scm:git:git@github.com:lloydmeta/zipkin-futures.git</connection>
        </scm>
        <developers>
          <developer>
            <id>lloydmeta</id>
            <name>Lloyd Chan</name>
            <url>http://lloydmeta.github.io</url>
          </developer>
        </developers>,
    publishTo <<= version { v =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false }
  )


}
