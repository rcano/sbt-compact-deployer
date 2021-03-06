package nao.compactdeployer

import com.lightbend.sbt.SbtProguard
import java.io.{File, FileOutputStream, FileInputStream}
import java.util.jar._
import sbt._, Keys._
import scala.collection.JavaConverters._

object DeployerPlugin extends AutoPlugin {
  override def requires = SbtProguard
  
  object autoImport {
    val compactDeploy = taskKey[Seq[File]]("generates a pack200 version of the result of proguard")
  }
  import autoImport._, SbtProguard.autoImport._
  override lazy val projectSettings = Seq(
    exportJars := true,
    proguardVersion in Proguard := "6.0.3",
    mappings in compactDeploy := Seq(),
    compactDeploy := {
      val extraMappings = (mappings in compactDeploy).value
      val proguarded = (proguard in Proguard).value.head
      
      val packer = Pack200.newPacker()
      packer.properties.asScala ++= Seq(
        Pack200.Packer.EFFORT -> "7",
        Pack200.Packer.SEGMENT_LIMIT -> "-1",
        Pack200.Packer.KEEP_FILE_ORDER -> "false")
      
      val packedFile = proguarded.getParentFile / "app.pack"
      
      io.Using.jarFile(false)(proguarded)(jarFile => 
        io.Using.fileOutputStream(false)(packedFile)(out => packer.pack(jarFile, out)))


      val classes = IO.readLinesURL(getClass.getResource("/launcher/classes")).map(c => 
        c.stripSuffix(".dump") -> io.Using.urlInputStream(getClass.getResource("/launcher/" + c))(IO.readBytes))
      
      val compactedJar = proguarded.getParentFile / ("compacted-" + proguarded.name)

      val manifest = new Manifest()
      manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
      manifest.getMainAttributes.put(Attributes.Name.MAIN_CLASS, "Launcher")
      io.Using.resource(identity[JarOutputStream] _)(new JarOutputStream(new FileOutputStream(compactedJar), manifest)) { jarOut =>
        for ((c, data) <- classes) {
          jarOut putNextEntry new JarEntry(c)
          jarOut write data
        }
        
        val appEntry = new JarEntry("app")
        appEntry.setMethod(java.util.zip.ZipEntry.DEFLATED)
        jarOut putNextEntry appEntry
        IO.transferAndClose(new FileInputStream(packedFile), jarOut)
        
        jarOut putNextEntry new JarEntry("app.size")
        new java.io.DataOutputStream(jarOut).writeLong(proguarded.length)
        
        for ((file, path) <- extraMappings) {
          val mappingEntry = new JarEntry(path)
          mappingEntry.setMethod(java.util.zip.ZipEntry.DEFLATED)
          jarOut putNextEntry mappingEntry
          IO.transferAndClose(new FileInputStream(file), jarOut)
        }
        
        jarOut.finish()
      }
      
      Seq(packedFile, compactedJar)
    }
  )
}
