import mill._, scalalib._

object lib1 extends ScalaModule {
    def scalaVersion = "3.0.1"
}

object lib2 extends ScalaModule {
    def scalaVersion = "3.0.1"

    def moduleDeps = Seq(lib1)
}
