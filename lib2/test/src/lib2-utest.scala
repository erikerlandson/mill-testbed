package milltest.lib2

import utest._

object Lib2Tests extends TestSuite {
    import milltest.lib2.api2._

    val tests = Tests {
        test("test f3") {
            assert(f3(1) == 3)
        }
    }
}
