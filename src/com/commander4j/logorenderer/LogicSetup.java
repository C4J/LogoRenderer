package com.commander4j.logorenderer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the LOGOSTEP "logic" setup entries exposed via &LOGICSETUP.
 * Names, order, and defaults follow the PowerLeap III LSP command reference.
 * Values are unsigned bytes (0..255) and persist for the lifetime of the
 * process.
 */
public final class LogicSetup {

    private static final LinkedHashMap<String, Integer> ENTRIES = new LinkedHashMap<>();
    static {
        ENTRIES.put("CARBON_CONTROL",        3);
        ENTRIES.put("CARBON_REQUEST",        255);
        ENTRIES.put("RESET_DATA_ABORT",      0);
        ENTRIES.put("RESET_DATA_AFTER",      0);
        ENTRIES.put("RESET_DATA_INIT",       0);
        ENTRIES.put("SEND_FEEDBACK_AFTER",   0);
        ENTRIES.put("PRINT_X_LABELS",        1);
        ENTRIES.put("PRINT_WITH_DATA_READY", 0);
        ENTRIES.put("CARBON_END_WARNING",    0);
    }

    private LogicSetup() {}

    public static synchronized Map<String, Integer> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ENTRIES));
    }

    public static synchronized boolean has(String name) {
        return ENTRIES.containsKey(name);
    }

    /** Set one entry. Returns false if the name is unknown or the value is out of range. */
    public static synchronized boolean set(String name, int value) {
        if (!ENTRIES.containsKey(name)) return false;
        if (value < 0 || value > 255)   return false;
        ENTRIES.put(name, value);
        return true;
    }
}
