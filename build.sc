import mill._, scalalib._, publish._, mill.api.PathRef


implicit val thisProjectName: projectContext.ProjectName = "milltest"
implicit val thisProjectVersion: projectContext.ProjectVersion = "0.1.0"
implicit val thisCrossVersions: projectContext.CrossVersions = List("2.12.8", "2.13.6")

object lib1 extends Cross[Lib1Module](projectContext.crossVersions:_*)

class Lib1Module(val crossScalaVersion: String) extends CrossScalaModule with PublishCommon {
    object test extends Tests {
        def testFramework = "org.scalatest.tools.Framework"
        def ivyDeps = Agg(
            ivy"org.scalactic::scalactic:3.1.1",
            ivy"org.scalatest::scalatest:3.1.1",
        )
    }
}

object lib2 extends Cross[Lib2Module](projectContext.crossVersions:_*) {
}

class Lib2Module(val crossScalaVersion: String) extends CrossScalaModule with PublishCommon {
    def moduleDeps = Seq(lib1())

    object test extends Tests {
        def testFramework = "utest.runner.Framework"
        def ivyDeps = Agg(
            ivy"com.lihaoyi::utest:0.7.10",
        )
    }
}

object projectContext {
    def projectName(implicit v: ProjectName): String = v.value
    def projectVersion(implicit v: ProjectVersion): String = v.value
    def crossVersions(implicit v: CrossVersions): Seq[String] = v.value

    case class ProjectName(val value: String)
    object ProjectName {
        implicit def lift(s: String): ProjectName = ProjectName(s)
    }

    case class ProjectVersion(val value: String)
    object ProjectVersion {
        implicit def lift(s: String): ProjectVersion = ProjectVersion(s)
    }

    case class CrossVersions(val value: Seq[String])
    object CrossVersions {
        implicit def lift(s: Seq[String]): CrossVersions = CrossVersions(s)
    }
}

// ./mill mill.scalalib.PublishModule/publishAll __.publishArtifacts '<user>:<passwd>'
// then log into oss.sonatype.org and release manually
// or add '--release true' flag for automatic sonatype staging/release sequence
trait PublishCommon extends PublishModule {
    // this is an override to a variable I defined above
    def publishVersion: T[String] = projectContext.projectVersion

    // this is an override to add common prefix name, like 'milltest-lib1', 'milltest-lib1', etc
    def artifactName: T[String] = s"${projectContext.projectName}-" + millOuterCtx.segments.parts.last

    def pomSettings = PomSettings(
        description = artifactName(),
        organization = "com.manyangled",
        url = "https://github.com/erikerlandson/mill-testbed",
        licenses = Seq(License.`Apache-2.0`),
        versionControl = VersionControl.github("erikerlandson", "mill-testbed"),
        developers = Seq(
            Developer("erikerlandson", "Erik Erlandson", "http://erikerlandson.github.io/")
        )
    )
}

// This module isn't really a ScalaModule, but we use it to generate
// consolidated documentation using the Scaladoc tool.
object docs extends Cross[DocsModule]("2.13.6")
class DocsModule(val crossScalaVersion: String) extends CrossScalaModule {
  def moduleDeps = Seq(lib1(), lib2())

  // generate the static website
  // adapted from:
  // https://github.com/com-lihaoyi/mill/discussions/1194
  def site = T {
    import mill.eval.Result

    if (!os.isDir(millSourcePath)) {
        T.log.info(s"""Mill source path for docs "${millSourcePath}" not found, ignoring ...""")
    } else {
        val sitefiles = os.walk(millSourcePath, skip = (p: os.Path) => !os.isFile(p))
        T.log.info(s"Staging ${sitefiles.length} site files from site source path ${millSourcePath} ...") 
        for {
          f <- sitefiles
        } {
          os.copy.over(f, T.dest / f.subRelativeTo(millSourcePath), createFolders = true)
        }
    }
    val files: Seq[os.Path] = T.traverse(moduleDeps)(_.allSourceFiles)().flatten.map(_.path)

    // the details of the options and zincWorker call are significantly
    // different between scala-2 scaladoc and scala-3 scaladoc
    // below is for scala-2 variant
    val options = Seq(
      "-d", T.dest.toString,
      "-classpath", compileClasspath().map(_.path).mkString(":"),
      "-rootdir", "/home/eje/git/mill-testbed",
      "-doc-title", projectContext.projectName,
      "-doc-version", projectContext.projectVersion
    )

    zincWorker.worker().docJar(
      scalaVersion(),
      scalaOrganization(),
      scalaDocClasspath().map(_.path),
      scalacPluginClasspath().map(_.path),
      options ++ files.map(_.toString)
    ) match{
      case true =>
        Result.Success(PathRef(T.dest))
      case false =>
        Result.Failure("doc generation failed")
    }
  }

  // preview the site locally on http://localhost:8000
  def serve() = T.command {
    T.log.info(s"serving on http://localhost:8000")
    os.proc("python3", "-m", "http.server", "--directory", site().path).call()
  }
}
