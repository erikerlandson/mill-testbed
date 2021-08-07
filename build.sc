import mill._, scalalib._, publish._

val crossVersions: Seq[String] = List("2.12.8", "2.13.6")

val pubVer = "0.1.0"

object lib1 extends Cross[Lib1Module](crossVersions:_*)

class Lib1Module(val crossScalaVersion: String) extends CrossScalaModule with PublishCommon {
    object test extends Tests {
        def testFramework = "org.scalatest.tools.Framework"
        def ivyDeps = Agg(
            ivy"org.scalactic::scalactic:3.1.1",
            ivy"org.scalatest::scalatest:3.1.1",
        )
    }
}

object lib2 extends Cross[Lib2Module](crossVersions:_*) {
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

// ./mill mill.scalalib.PublishModule/publishAll __.publishArtifacts '<user>:<passwd>'
// then log into oss.sonatype.org and release manually
// or add '--release true' flag for automatic sonatype staging/release sequence
trait PublishCommon extends PublishModule {
    // this is an override to a variable I defined above
    def publishVersion: T[String] = pubVer

    // this is an override to add common prefix name, like 'milltest-lib1', 'milltest-lib1', etc
    def artifactName: T[String] = "milltest-" + millOuterCtx.segments.parts.last

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
