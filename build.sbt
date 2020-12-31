
enablePlugins(JavaAppPackaging)

scalaVersion := "2.12.12"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
scalacOptions += "-target:jvm-1.8"

libraryDependencies ++= Seq(
  "com.1stleg" % "jnativehook" % "2.1.0",
  "org.slf4j" % "jul-to-slf4j" % "1.7.30",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

run / fork := true
