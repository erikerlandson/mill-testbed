import mill._, scalalib._

val crossVersions: Seq[String] = List("2.12.8", "2.13.6")

object lib1 extends Cross[Lib1Module](crossVersions:_*)

class Lib1Module(val crossScalaVersion: String) extends CrossScalaModule {
}

object lib2 extends Cross[Lib2Module](crossVersions:_*) {
}

class Lib2Module(val crossScalaVersion: String) extends CrossScalaModule {
    def moduleDeps = Seq(lib1())
}
