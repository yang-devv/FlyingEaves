package com.flyingeaves.gen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where the generator reads its data (paths.py, materials.REGISTRY): the built-in data folder (generator/data: materials,
 * parts, presets, library) and the run folder (the player's own materials and parts, the game's block list), which may
 * be absent. Set once before generating ({@link #init}); defaults: system property flyingeaves.genData, else
 * ./generator/data; no run folder.
 */
public final class Env {
    private Env() {
    }

    public static Path DATA = Path.of(System.getProperty("flyingeaves.genData", "generator/data"));
    public static Path RUN = System.getProperty("flyingeaves.genRun") == null ? null : Path.of(System.getProperty("flyingeaves.genRun"));
    private static Set<String> registry;
    private static boolean registryRead;

    public static void init(Path data, Path run) {
        DATA = data;
        RUN = run;
        registry = null;
        registryRead = false;
    }

    /** materials.registry(): the block ids the game has, or null when it has not written its list. */
    static Set<String> registry() {
        if (!registryRead) {
            registryRead = true;
            if (RUN != null && Files.exists(RUN.resolve("registry_blocks.txt"))) {
                try {
                    registry = new HashSet<>(List.of(Files.readString(RUN.resolve("registry_blocks.txt"), StandardCharsets.UTF_8).trim().split("\\s+")));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return registry;
    }

    public static void forgetRegistry() {
        registry = null;
        registryRead = false;
    }

    /** materials.present. */
    public static boolean present(String state) {
        String bid = Blocks.name(state);
        if (!bid.contains(":")) bid = "minecraft:" + bid;
        Set<String> reg = registry();
        if (reg == null) return bid.startsWith("minecraft:");
        return reg.contains(bid);
    }

    /** The folders a kind of data is read from, built-in first: DATA/sub, RUN/sub. */
    static List<Path> dirs(String sub) {
        List<Path> out = new ArrayList<>();
        out.add(DATA.resolve(sub));
        if (RUN != null) out.add(RUN.resolve(sub));
        return out;
    }

    /** sorted(d.glob(prefix + "*.json")). */
    static List<Path> glob(Path d, String prefix) {
        if (!Files.isDirectory(d)) return List.of();
        try (Stream<Path> s = Files.list(d)) {
            List<Path> out = new ArrayList<>(s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".json") && Files.isRegularFile(p);
            }).toList());
            out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
