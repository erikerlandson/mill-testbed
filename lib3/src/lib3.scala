package milltest.lib3

object api3 {
    import milltest.lib1.api1._
    import milltest.lib2.api2._
    import milltest.lib3.compat._

    /** scaladoc for function 'f' */
    def f(v: Int): Int = vc + f1(v) + f2(v) + f3(v)
}
