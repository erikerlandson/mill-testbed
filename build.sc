import mill._, scalalib._

val crossVersions: Seq[String] = List("2.12.8", "2.13.6")

object lib1 extends Cross[Lib1Module](crossVersions:_*)

class Lib1Module(val crossScalaVersion: String) extends CrossScalaModule {
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

class Lib2Module(val crossScalaVersion: String) extends CrossScalaModule {
    def moduleDeps = Seq(lib1())

    object test extends Tests {
        def testFramework = "utest.runner.Framework"
        def ivyDeps = Agg(
            ivy"com.lihaoyi::utest:0.7.10",
        )
    }
}
