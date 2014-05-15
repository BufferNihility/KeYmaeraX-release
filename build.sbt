scalaVersion := "2.10.4"

libraryDependencies += "org.scala-lang" % "scala-swing" % "2.10.4"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.0" % "test"

////////////////////////////////////////////////////////////////////////////////
// Mathematica Interop
////////////////////////////////////////////////////////////////////////////////

unmanagedJars in Compile += file("/usr/local/Wolfram/Mathematica/9.0/SystemFiles/Links/JLink/JLink.jar")

unmanagedJars in Compile += file("/Applications/Mathematica.app/SystemFiles/Links/JLink/JLink.jar")

////////////////////////////////////////////////////////////////////////////////
// Hyrda Settings
// Taken from https://www.assembla.com/wiki/show/liftweb/using_sbt
////////////////////////////////////////////////////////////////////////////////
scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.1.4"
  val sprayV = "1.1.1"
  Seq(
    "io.spray"            %   "spray-can"     % sprayV,
    "io.spray"            %   "spray-routing" % sprayV,
    "io.spray"            %   "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2"        % "2.2.3" % "test"
  )
}

Revolver.settings
