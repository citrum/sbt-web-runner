sbtPlugin := true

sources in doc in Compile := List()
scalacOptions := Seq("-deprecation", "-encoding", "utf8", "-unchecked", "-deprecation", "-feature", "-language:existentials")
scalaVersion := "2.10.6"

sourceDirectory in Compile <<= baseDirectory(_ / "src")
scalaSource in Compile <<= baseDirectory(_ / "src")
javaSource in Compile <<= baseDirectory(_ / "src")
