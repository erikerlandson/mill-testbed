package milltest.lib2

object api2:
    import milltest.lib1.api1.*

    def f2(v: Int) = v * 2

    def f3(v: Int) = f1(f2(v))
