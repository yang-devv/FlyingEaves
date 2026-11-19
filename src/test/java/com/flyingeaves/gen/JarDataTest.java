package com.flyingeaves.gen;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The generator's data read from inside a jar, as the game reads it from the mod's (flyingeaves/generator/data through
 * the jar's file system, not unpacked): the first cases of the default set (the same order from a fresh JVM, so the
 * same answers), and the player's run folder empty.
 */
class JarDataTest {
    private static FileSystem fs;
    private static Path zip;

    @TestFactory
    Stream<DynamicTest> fromJar() throws IOException {
        Path data = Path.of(System.getProperty("flyingeaves.genData", "generator/data"));
        zip = Files.createTempFile("fe_jar_data", ".jar");
        try (OutputStream o = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(o);
             Stream<Path> all = Files.walk(data)) {
            for (Path p : all.sorted().toList()) {
                String name = "flyingeaves/generator/data/" + data.relativize(p).toString().replace('\\', '/');
                if (Files.isDirectory(p)) {
                    if (!name.endsWith("/")) name += "/";
                    z.putNextEntry(new ZipEntry(name));
                } else {
                    z.putNextEntry(new ZipEntry(name));
                    z.write(Files.readAllBytes(p));
                }
                z.closeEntry();
            }
        }
        fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Map.of());
        Path run = Files.createTempDirectory("fe_jar_run");
        Env.init(fs.getPath("/flyingeaves/generator/data"), run);
        List<Map<String, Object>> cases = OracleCases.load("default");
        return OracleCases.run(cases.subList(0, 24));
    }

    @AfterAll
    static void close() throws IOException {
        if (fs != null) fs.close();
        if (zip != null) Files.deleteIfExists(zip);
    }
}
