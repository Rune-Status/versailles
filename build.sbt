val commonSettings = Seq(
  version := "0.1",
  organization := "versailles",
  scalaVersion := "2.12.8"
)

val twitterUtilVersion = "19.1.0"

val akkaVersion = "2.5.20"

val nettyVersion = "4.1.33.Final"
val scalaTestVersion = "3.0.5"

val scalaAsyncVersion = "0.9.7"

val redisClientVersion = "1.8.0"
val bcryptLibVersion = "0.4"

lazy val domain = project("domain")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= {
      val main = Seq(
        "io.netty" % "netty-common" % nettyVersion,
        "com.twitter" %% "util-collection" % twitterUtilVersion
      )
      
      val test = Seq(
        "org.scalatest" %% "scalatest" % scalaTestVersion % Test
      )
      
      main ++ test
    }
  )

lazy val application = project("application")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= {
      val main = Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion,

        "org.scala-lang.modules" %% "scala-async" % scalaAsyncVersion,

        "com.github.etaty" %% "rediscala" % redisClientVersion,

        "io.netty" % "netty-buffer" % nettyVersion,
        
        "com.twitter" %% "util-collection" % twitterUtilVersion,

        "org.mindrot" % "jbcrypt" % bcryptLibVersion
      )
      
      val test = Seq(
        "org.scalatest" %% "scalatest" % scalaTestVersion % Test
      )
      
      main ++ test
    }
  )
  .dependsOn(domain)

def project(id: String) = Project(id, base = file(id))