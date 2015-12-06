
scalaVersion := "2.11.7"

resolvers ++= Seq(
  "xuggle repo" at "http://xuggle.googlecode.com/svn/trunk/repo/share/java/"
)

libraryDependencies ++= Seq(
  "xuggle" % "xuggle-xuggler" % "5.4"
)

