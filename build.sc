import mill._, scalalib._, scalajslib._, publish._, mill.api.PathRef

object projectContext {
    def projectName: String = "milltest"
    def projectVersion: String = "0.1.1"
    def crossVersions: Seq[String] = List("2.12.13", "2.13.4")
    // patch versions matter here in terms of what scala and scalajs pairs exist
    def crossVersionsJS: Seq[(String, String)] = List(("2.12.13", "1.4.0"), ("2.13.4", "1.4.0"))
}

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

object lib2 extends Cross[Lib2Module](projectContext.crossVersions:_*)

class Lib2Module(val crossScalaVersion: String) extends CrossScalaModule with PublishCommon {
    def moduleDeps = Seq(lib1())

    object test extends Tests {
        def testFramework = "utest.runner.Framework"
        def ivyDeps = Agg(
            ivy"com.lihaoyi::utest:0.7.10",
        )
    }
}

// using as a cross-platform design pattern:
// https://github.com/com-lihaoyi/fastparse/blob/master/build.sc
object lib3 extends Module {
    object jvm extends Cross[JvmModule](projectContext.crossVersions:_*)

    class JvmModule(val crossScalaVersion: String) extends CrossPlatformCommon {
        def scalaPlatform = "jvm"
        def moduleDeps = List(lib1(), lib2())
    }

    object js extends Cross[JsModule](projectContext.crossVersionsJS:_*)

    class JsModule(val crossScalaVersion: String, crossScalaVersionJS: String) extends CrossPlatformCommon with ScalaJSModule {
        def scalaPlatform = "js"
        def scalaJSVersion = crossScalaVersionJS
        def moduleDeps = List(lib1(), lib2())
    }
}

trait CrossPlatformCommon extends CrossScalaModule with PublishCommon {
    // scala compile platform, such as "jvm" or "js" or "native"
    def scalaPlatform: String

    def sources = T.sources(
        millSourcePath / "src",
        millSourcePath / s"src-${scalaPlatform}"
    )
}

// ./mill mill.scalalib.PublishModule/publishAll __.publishArtifacts '<user>:<passwd>'
// then log into oss.sonatype.org and release manually
// or add '--release true' flag for automatic sonatype staging/release sequence
trait PublishCommon extends PublishModule {
    // this is an override to a variable I defined above
    def publishVersion: T[String] = projectContext.projectVersion

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

    // prepend project name to target names
    def artifactName: T[String] = T {
        millOuterCtx.segments.parts match {
           case Seq(name) => s"${projectContext.projectName}-${name}"
           case Seq(name, p) if platformVals.contains(p) => s"${projectContext.projectName}-${name}"
           case u => {
               val estr = s"unrecognized segments pattern: $u"
               T.log.error(estr)
               throw new Exception(estr)
               estr
           }
        }
    }

    private val platformVals = Set("jvm", "js")
}

object site extends ScaladocSiteModule {
    // standard scalaVersion method is a task, which only works inside other tasks
    def scaladocScalaVersion = "2.13.4"

    // specify subdirectory for scaladoc
    override def scaladocSubPath: os.SubPath = os.sub / "api" / "latest"

    // example of bridging non-cross ScalaSiteModule to cross-compiled modules
    override def scaladocModules = Seq(lib1(scaladocScalaVersion), lib2(scaladocScalaVersion))

    def scaladocPushGitURI = "git@github.com:erikerlandson/mill-testbed.git"
}

// This module isn't really a ScalaModule, but we use it to generate
// consolidated documentation using the Scaladoc tool.
trait ScaladocSiteModule extends ScalaModule {
    // would be preferable to just use standard scalaVersion here to be more DRY, but
    // scalaVersion is a Task and I haven't figured out how to invoke a Task in a non-Task method
    def scaladocScalaVersion: String

    def scaladocModules: Seq[JavaModule] = List.empty[JavaModule]

    // stage the static website and/or doc into the 'stage' task destination directory
    // adapted from:
    // https://github.com/com-lihaoyi/mill/discussions/1194
    def stage = T {
        import mill.eval.Result

        if (!os.isDir(millSourcePath)) {
            T.log.error(s"""Source path "${millSourcePath}" not found, ignoring""")
            T.log.error(s"Staging index.html from method defaultSiteIndex")
            os.write.over(T.dest / "index.html", defaultSiteIndex)
        } else {
            val sitefiles = os.walk(millSourcePath, skip = (p: os.Path) => !os.isFile(p))
            T.log.info(s"Staging ${sitefiles.length} site files from site source path ${millSourcePath}") 
            for {
                f <- sitefiles
            } {
                os.copy.over(f, T.dest / f.subRelativeTo(millSourcePath), createFolders = true)
            }
        }

        val scaladocFiles: Seq[os.Path] =
            T.traverse(scaladocModules)(_.allSourceFiles)().flatten.map(_.path)

        T.log.info(s"Staging scaladoc for ${scaladocFiles.length} files")

        val scaladocPath: os.Path = T.dest / scaladocSubPath
        os.makeDir.all(scaladocPath)

        // the details of the options and zincWorker call are significantly
        // different between scala-2 scaladoc and scala-3 scaladoc
        // below is for scala-2 variant
        val options: Seq[String] = Seq(
            "-doc-title", projectContext.projectName,
            "-doc-version", projectContext.projectVersion,
            "-d", scaladocPath.toString,
            "-classpath", compileClasspath().map(_.path).mkString(":"),
        )

        val docReturn = zincWorker.worker().docJar(
            scalaVersion(),
            scalaOrganization(),
            scalaDocClasspath().map(_.path),
            scalacPluginClasspath().map(_.path),
            options ++ scaladocFiles.map(_.toString)
        ) match{
            case true =>  Result.Success(PathRef(T.dest))
            case false => Result.Failure("doc generation failed")
        }

        docReturn
    }

    // preview the site locally
    def serve() = T.command {
        val port = scaladocServePort
        require((port > 0) && (port < 65536))

        // this also runs 'stage' target as a dependency
        val stageDir = (stage().path).toString

        try {
            T.log.info(s"serving on http://localhost:${port}")
            //os.proc("python3", "-m", "http.server", s"${port}", "--directory", stageDir).call()
            // invoking via runSubprocess prevents zombie http server on exit
            mill.modules.Jvm.runSubprocess(List("python3", "-m", "http.server", s"${port}", "--directory", stageDir), T.env(T.ctx()), os.pwd)
        } catch {
            case _: Throwable =>
                T.log.error("Server startup failed - is python3 installed?")
        }
    }

    def push() = T.command {
        // this also runs 'stage' target as a dependency
        val stageDir = (stage().path).toString

        val workBranch = scaladocPushWorkingBranch
        val remoteBranch = scaladocPushRemoteBranch
        val gitURI = scaladocPushGitURI
        val username = scaladocPushUserName
        val useremail = scaladocPushUserEmail

        T.log.info(s"pushing site to branch $remoteBranch of $gitURI")

        os.proc("git", "-C", stageDir, "init", "--quiet", s"--initial-branch=${workBranch}").call()
        os.proc("git", "-C", stageDir, "config", "user.name", username).call()
        os.proc("git", "-C", stageDir, "config", "user.email", useremail).call()
        os.proc("git", "-C", stageDir, "add", ".").call()
        os.proc("git", "-C", stageDir, "commit", "-m", "push from mill").call()
        os.proc("git", "-C", stageDir, "push", "-f", gitURI, s"${workBranch}:${remoteBranch}").call()

        T.log.info("cleaning up git working directory")
        os.proc("rm", "-rf", s"${stageDir}/.git").call()
    }

    // currently no default for this
    def scaladocPushGitURI: String

    def scaladocPushWorkingBranch: String = "main"

    def scaladocPushRemoteBranch: String = "gh-pages"

    def scaladocPushUserName: String = os.proc("git", "config", "user.name").call().out.string

    def scaladocPushUserEmail: String = os.proc("git", "config", "user.email").call().out.string

    def scalaVersion = scaladocScalaVersion

    // scaladoc goes in this subdirectory
    // TODO: add javadoc support, similar to sbt unidoc?
    def scaladocSubPath: os.SubPath = os.sub / "api" / "latest"

    def scaladocServePort: Int = 8000

    def defaultSiteIndex: String =
        s"""|<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |    <meta charset="UTF-8">
            |    <title>Project Documentation</title>
            |    <script language="JavaScript">
            |    <!--
            |    function doRedirect()
            |    {
            |        window.location.replace("${scaladocSubPath}");
            |    }
            |    doRedirect();
            |    //-->
            |    </script>
            |</head>
            |<body>
            |<a href="${scaladocSubPath}">Go to the project documentation
            |</a>
            |</body>
            |</html>        
            |""".stripMargin
}
