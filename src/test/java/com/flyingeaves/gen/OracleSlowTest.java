package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.stream.Stream;

/** The 1:1 紫禁城 (575 rooms, 2.2 M blocks: about 30 s and 1.5 GB of heap): not in the default run, gradlew slowTest. */
@Tag("slow")
class OracleSlowTest {
    @TestFactory
    Stream<DynamicTest> cityReal() {
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), null);
        return OracleCases.run(OracleCases.load("slow"));
    }
}
