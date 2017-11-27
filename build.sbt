lazy val root = Project("sbt-deployer", file(".")).
  settings(
    name := "sbt-compact-deployer",
    organization := "nao",
    version := "0.1",
    sbtPlugin := true,
    resourceGenerators in Compile += copyLauncherClasses.taskValue,
    addSbtPlugin("com.lightbend.sbt" % "sbt-proguard" % "0.3.0")
  )

lazy val launcher = project


lazy val copyLauncherClasses = Def.task {
  import java.nio.file._
  import scala.collection.JavaConverters._
  val sourceDir = (products in (launcher, Compile)).value.head.toPath
  val targetDir = ((resourceManaged in Compile).value / "launcher").toPath
  Files.createDirectories(targetDir)
  println("copying launcher files")
  val res = Files.list(sourceDir).iterator.asScala.map(f => Files.copy(f, targetDir.resolve(f.getFileName + ".dump"), StandardCopyOption.REPLACE_EXISTING).toFile).toVector
  val listing = Files.write(targetDir.resolve("classes"), res.map(f => targetDir.relativize(f.toPath).toString).mkString("\n").getBytes("utf-8"))
  println("generated " + res.mkString(", "))
  res :+ listing.toFile
}