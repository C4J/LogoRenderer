package com.commander4j.logorenderer;

import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared state for the TCP server — field values received from the client.
 */
public class ServerMemory {

    public static final Font font_list  = new Font("Monospaced", Font.PLAIN, 11);
    public static final Font font_table = new Font("Monospaced", Font.PLAIN, 11);

    public static final Color color_listFontStandard = Color.BLUE;
    public static final Color color_listFontSelected = Color.BLACK;
    public static final Color color_listHighlighted  = new Color(184, 207, 229);
    public static final Color color_tablerow1        = new Color(248, 226, 226);
    public static final Color color_tablerow2        = new Color(240, 255, 240);
    public static final Color color_dark_green       = new Color(38, 106, 46);

    /** Field data received from the client. Key = field ID string (e.g. "1", "100"). */
    public static LinkedHashMap<String, LabelField> Fields = new LinkedHashMap<>();

    // -----------------------------------------------------------------------
    // Pallet simulation
    // -----------------------------------------------------------------------

    /** SSCC-18 counter, incremented after each simulated pallet print. */
    public static AtomicLong ssccCounter = new AtomicLong(1L);

    /**
     * PL3 global counters addressed by *GETCOUNT / *SETCOUNT. Only the ids
     * present in this map are considered "defined" — requests for other ids
     * produce the 979 "not defined" NAK. On a real printer the defined set
     * comes from system.sup; here we seed counter 1 to match the typical
     * case-labeller configuration captured from a physical PL3.
     */
    public static final Map<Integer, Integer> globalCounters = new ConcurrentHashMap<>();
    static { globalCounters.put(1, 0); }

    /**
     * Log buffers — up to 2 entries (double-buffer model).
     * Each entry is a list of semicolon-delimited log lines for one pallet.
     * Guard all access with {@code synchronized (logBuffers)}.
     */
    public static final LinkedList<List<String>> logBuffers = new LinkedList<>();

    // -----------------------------------------------------------------------
    // STATE push / &REPORT subscription
    // -----------------------------------------------------------------------

    /** True after the client has sent &REPORT; cleared by &NOREPORT. */
    public static volatile boolean reportingEnabled = false;

    /**
     * Data-ready flag. Toggled by *SDR (set) / *RDR (reset). Queried by
     * ?dataready. Cleared on startup.
     */
    public static volatile boolean dataReady = false;

    /**
     * Symbols subscribed via &TIMESTAMP,<symbol>. &TIMESTAMPLIST iterates
     * this set; &UNTIMESTAMP removes; &NOTIMESTAMPS clears. Insertion order
     * preserved so list output matches the order entries were added.
     */
    public static final Set<String> timestampSubscriptions = new CopyOnWriteArraySet<>();

    /** Reference to the running server; used by the SymbolTable listener to push STATE lines. */
    public static volatile ComboServer activeServer = null;
}
