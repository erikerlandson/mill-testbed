package milltest.lib2

object api2 {
    import milltest.lib1.api1._

    def f2(v: Int): Int = v * 2

    def f3(v: Int): Int = f1(f2(v))
}
