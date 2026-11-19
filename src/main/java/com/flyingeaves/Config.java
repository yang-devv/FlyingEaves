package com.flyingeaves;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/flyingeaves-client.toml. A release build ({@link Build#PYTHON} false) has only {@code generatorDir}: the
 * generator is the Java one inside the game. The Python keys (pythonPath, autoStart, backend) exist only in a build
 * made with -PwithPython=true (development); they are null otherwise and nothing reads them.
 */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> PYTHON_PATH;
    public static final ForgeConfigSpec.ConfigValue<String> GENERATOR_DIR;
    public static final ForgeConfigSpec.BooleanValue AUTO_START;
    public static final ForgeConfigSpec.ConfigValue<String> BACKEND;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        if (Build.PYTHON) {
            b.comment("The building generator. Dev build: the Java one in the game, with the Python one (3.10+) as a fallback.").push("generator");
            PYTHON_PATH = b.comment("Python interpreter (python.exe, or a folder that has one). Empty: a bundled python/ folder in",
                            "<game dir>/flyingeaves/python or <game dir>/config/flyingeaves/python if there, else 'python' on PATH",
                            "(on Windows also 'py -3'), else 'python3'.")
                    .define("pythonPath", "");
            GENERATOR_DIR = b.comment("Folder with bgen_daemon.py and data/. Empty: the copy the mod unpacks from its jar into",
                            "<game dir>/flyingeaves/generator (re-unpacked when the mod changes).")
                    .define("generatorDir", "");
            AUTO_START = b.comment("Start the Python generator when the game starts, or with backend = java when a request needs it",
                            "(and stop it when the game quits). false: never.")
                    .define("autoStart", true);
            BACKEND = b.comment("Who builds: 'java' - the generator inside the game; what it cannot do (a request too big for the",
                            "game's free memory) goes to the Python generator (started only then), with a line in the log.",
                            "'python' - everything through the Python generator (started with the game).")
                    .define("backend", "java", v -> v instanceof String s && (s.trim().equalsIgnoreCase("java") || s.trim().equalsIgnoreCase("python")));
        } else {
            b.comment("The building generator (Java, inside the game: no Python needed).").push("generator");
            GENERATOR_DIR = b.comment("A folder whose data/ (materials, parts, presets, library) replaces the data inside the mod jar.",
                            "Empty (the default): the jar's own.")
                    .define("generatorDir", "");
            PYTHON_PATH = null;
            AUTO_START = null;
            BACKEND = null;
        }
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }
}
