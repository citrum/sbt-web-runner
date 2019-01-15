name := "sbt-web-runner"
organization := "com.github.winmain.webby"
version := "0.9.0"

description := "An SBT plugin for fast & automatic code reloading for web applications. Based on spray/sbt-revolver"
homepage := Some(url("https://github.com/winmain/sbt-web-runner"))
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
bintrayVcsUrl := Some("https://github.com/winmain/sbt-web-runner")

bintrayRepository := "sbt-plugins"
bintrayOrganization := Some("winmain")
publishMavenStyle := false

// No Javadoc
publishArtifact in(Compile, packageDoc) := false
publishArtifact in packageDoc := false
sources in(Compile, doc) := Nil
