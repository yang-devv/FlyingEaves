package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The Java generator against the frozen Python one, without Python: single rooms of every oracle profile, multi-room
 * buildings, every design of the library, 随机一院 plans (house, palace, free), the 宫城, a 随机城镇 and a 随机宗门
 * (their designs planned by Python, checked in). The 1:1 紫禁城 is {@link OracleSlowTest} (gradlew slowTest).
 */
class OracleTest {
    @TestFactory
    Stream<DynamicTest> defaultSet() {
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), null);
        return OracleCases.run(OracleCases.load("default"));
    }
}
