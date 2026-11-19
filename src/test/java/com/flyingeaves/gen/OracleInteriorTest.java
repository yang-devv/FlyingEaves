package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 内饰 (1.2) against the frozen Python generator: rooms of several profiles, multi-room buildings and two saved designs
 * built 内饰：无 ("interior": false) and with 空房 floors (use "empty"): tools/oracle/capture.py interior.
 */
class OracleInteriorTest {
    @TestFactory
    Stream<DynamicTest> interior() {
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), null);
        return OracleCases.run(OracleCases.load("interior"));
    }
}
