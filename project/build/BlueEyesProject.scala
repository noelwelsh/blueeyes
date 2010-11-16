import sbt._
import scala.xml._
import de.element34.sbteclipsify._
import com.rossabaker.sbt.gpg._

trait OneJar { this: DefaultProject =>
  lazy val oneJar = oneJarAction
  
  def oneJarAction = oneJarTask.dependsOn(`package`) describedAs("Creates a single JAR containing all dependencies that runs the project's mainClass")
  
  def oneJarTask: Task = task { 
    import FileUtilities._
    import java.io.{ByteArrayInputStream, File}
    import java.util.jar.Manifest
    
    val manifest = new Manifest(new ByteArrayInputStream((
      "Manifest-Version: 1.0\n" +
      "Main-Class: " + mainClass.get + "\n").getBytes))

    val versionString = version match {
      case BasicVersion(major, _, _, _) => "-v" + major.toString

      case _ => version.toString
    }

    val allDependencies = jarPath +++ runClasspath +++ mainDependencies.scalaJars 
    
    log.info("All dependencies of " + name + ": " + allDependencies)

    val destJar = (normalizedName + versionString + ".jar"): Path

    FileUtilities.withTemporaryDirectory(log) { tmpDir =>
      val tmpPath = Path.fromFile(tmpDir)

      allDependencies.get.foreach { dependency => 
        log.info("Unzipping " + dependency + " to " + tmpPath)

        if (dependency.ext.toLowerCase == "jar") {
          unzip(dependency, tmpPath, log) 
        }
        else if (dependency.asFile.isDirectory) {
          copy(List(dependency), tmpPath, true, true, log)
        }
        else {
          copyFile(dependency.asFile, tmpDir, log)
        }
      } 
      
      new File(tmpDir, "META-INF/MANIFEST.MF").delete

      log.info("Creating single jar out of all dependencies: " + destJar)

      jar(tmpDir.listFiles.map(Path.fromFile), destJar, manifest, true, log)

      None
    }
  }
}

class BlueEyesProject(info: ProjectInfo) extends DefaultProject(info) with Repositories with Eclipsify with IdeaProject with GpgPlugin with ChecksumPlugin {
  val scalatest   = "org.scalatest"               % "scalatest"         % "1.2"    % "test"
  val scalaspec   = "org.scala-tools.testing"     % "specs_2.8.0"       % "1.6.6-SNAPSHOT"       % "test"
  val scalacheck  = "org.scala-tools.testing"     % "scalacheck_2.8.0"  % "1.7"         % "test"
  val mockito     = "org.mockito"                 % "mockito-all"       % "1.8.4"       % "test"
  val paranamer   = "com.thoughtworks.paranamer"  % "paranamer"         % "2.0"
  val junit       = "junit"                       % "junit"             % "4.7"         % "test"
  val netty       = "org.jboss.netty"             % "netty"             % "3.2.3.Final" % "compile"
  val async       = "com.ning"                    % "async-http-client" % "1.3.3"       % "compile"
  val mongo       = "org.mongodb"                 % "mongo-java-driver" % "2.1"         % "compile"
  val jodatime    = "joda-time"                   % "joda-time"         % "1.6.2"       % "compile"
  val configgy    = "net.lag"                     % "configgy"          % "2.0.0"       % "compile"
  val guice       = "com.google.inject"           % "guice"             % "2.0"         % "compile"
  val rhino       = "rhino"                       % "js"                % "1.7R2"       % "compile"
  val xlightweb   = "org.xlightweb"               % "xlightweb"         % "2.13"        % "compile"

  override def mainClass = Some("blueeyes.BlueEyesDemo")

  override def managedStyle = ManagedStyle.Maven

  override def packageDocsJar = defaultJarPath("-javadoc.jar")
  override def packageSrcJar= defaultJarPath("-sources.jar")

  val sourceArtifact = Artifact.sources(artifactID)
  val docsArtifact = Artifact.javadoc(artifactID)

  // Can't publish to snapshots
//  val publishTo = "OSS Nexus" at "https://oss.sonatype.org/content/repositories/snapshots/"
  // Staging seems to publish though
  val publishTo = if (version.toString.endsWith("-SNAPSHOT")) "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
                  else "Sonatype Nexus Release Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"

    Credentials(Path.userHome / ".ivy2" / "credentials" / "oss.sonatype.org", log)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)

  override def pomExtra =
    <parent>
      <groupId>org.sonatype.oss</groupId>
      <artifactId>oss-parent</artifactId>
      <version>5</version>
    </parent> ++
    <name>BlueEyes</name> ++
    <description>A lightweight Web 3.0 framework for Scala</description> ++
    <url>http://github.com/jdegoes/blueeyes</url> ++
    <licenses>
      <license>
	<name>Apache 2</name>
	<url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
	 <distribution>repo</distribution>
      </license>
    </licenses> ++
    <scm>
      <connection>scm:git:git@github.com:jdegoes/blueeyes.git</connection>
      <developerConnection>scm:git:git@github.com:jdegoes/blueeyes.git</developerConnection>
      <url>git@github.com:jdegoes/blueeyes.git</url>
    </scm> ++
    <developers></developers>

  override def pomPostProcess(pom: Node) =
    super.pomPostProcess(pom) match {
      case Elem(prefix, label, attr, scope, c @ _*) =>
        val children = c flatMap {
          case Elem(_, "repositories", _, _, repos @ _*) =>
            <profiles>
              <!-- poms deployed to maven central CANNOT have a repositories
                   section defined.  This download profile lets you
                   download dependencies other repos during development time. -->
              <profile>
                <id>download</id>
                <repositories>
                  {repos}
                </repositories>
              </profile>
            </profiles>
          case Elem(_, "dependencies", _, _, _ @ _*) =>
            // In SBT, parent projects depend on their children.  They should
            // not in Maven.
            None
          case x => x
        }
        Elem(prefix, label, attr, scope, children : _*)
    }
    override def deliverProjectDependencies = Nil
}

trait Repositories {
  val scalareleases   = MavenRepository("Scala Repo Releases",        "http://scala-tools.org/repo-releases/")
  val scalasnapshots  = MavenRepository("Scala-tools.org Repository", "http://scala-tools.org/repo-snapshots/")
  val jbossreleases   = MavenRepository("JBoss Releases",             "http://repository.jboss.org/nexus/content/groups/public/")
  val sonatyperelease = MavenRepository("Sonatype Releases",          "http://oss.sonatype.org/content/repositories/releases")
  val nexusscalatools = MavenRepository("Nexus Scala Tools",          "http://nexus.scala-tools.org/content/repositories/releases")
  val mavenrepo1      = MavenRepository("Maven Repo 1",               "http://repo1.maven.org/maven2/")
}
