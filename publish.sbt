name := "sbt-web-runner"

organization := "rosrabota"

version := "0.1-SNAPSHOT"

publishTo := {
  val nexus = "http://nexus/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "content/repositories/releases")
}
publishMavenStyle := true
