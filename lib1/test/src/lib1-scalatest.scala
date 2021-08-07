package milltest.lib1

import org.scalatest.funsuite.AnyFunSuite

class Lib1Suite extends AnyFunSuite {
    import milltest.lib1.api1._

    test("test f1") {
        assert(f1(2) == 3)
    }
}
