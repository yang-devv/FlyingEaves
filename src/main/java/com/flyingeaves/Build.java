package com.flyingeaves;

import java.io.InputStream;
import java.util.Properties;

/**
 * How this jar was built (com/flyingeaves/build.properties, written by gradle's processResources).
 *
 * <p>{@link #PYTHON}: the Python generator (bgen_daemon.py) is in this build as a fallback - only with
 * {@code gradlew ... -PwithPython=true} (development: the oracle tooling, comparing the two). A release build has no
 * Python files and never starts, looks for, or writes requests for a Python process: everything is the Java generator.
 */
public final class Build {
    public static final boolean PYTHON;

    static {
        boolean py = false;
        try (InputStream in = Build.class.getResourceAsStream("build.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                py = Boolean.parseBoolean(p.getProperty("python", "false").trim());
            }
        } catch (Exception ignored) {
        }
        PYTHON = py;
    }

    private Build() {
    }
}
