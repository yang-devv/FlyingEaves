package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The build menu's kinds (bgen.build) against the frozen Python generator: every kind of bgen.K (sites.py's included)
 * at its first and last tier, seed 1, facings in turn (tools/oracle/capture.py kinds).
 */
class OracleKindsTest {
    @TestFactory
    Stream<DynamicTest> kinds() {
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), null);
        return OracleCases.run(OracleCases.load("kinds"));
    }
}
