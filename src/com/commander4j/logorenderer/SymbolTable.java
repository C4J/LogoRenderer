package com.commander4j.logorenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Registry of symbolic I/O names exposed to LSP clients via &SYMBOLTABLE.
 * Entries carry a type (E=input, A=output, M=memory flag), a byte.bit
 * address, a name, and a current value (0 or 1). A single listener may be
 * registered to observe value changes.
 */
public final class SymbolTable {

    public enum Type { E, A, M }

    public static final class Entry {
        private final Type type;
        private final int byteAddr;
        private final int bit;
        private final String name;
        private final AtomicInteger value = new AtomicInteger(0);

        Entry(Type type, int byteAddr, int bit, String name) {
            this.type = type;
            this.byteAddr = byteAddr;
            this.bit = bit;
            this.name = name;
        }

        public Type   type()     { return type; }
        public int    byteAddr() { return byteAddr; }
        public int    bit()      { return bit; }
        public String name()     { return name; }
        public int    value()    { return value.get(); }
        public String address()  { return type.name() + byteAddr + "." + bit; }
    }

    private static final List<Entry> ENTRIES;
    private static volatile Consumer<Entry> listener = null;

    static {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(Type.E,  0, 4, "inpTrigManu"));
        list.add(new Entry(Type.E, 20, 0, "inpPrintDone"));
        list.add(new Entry(Type.E, 20, 1, "inpPrintError"));
        list.add(new Entry(Type.E, 20, 2, "inpDataReady"));
        list.add(new Entry(Type.E, 20, 3, "inpUciOnline"));
        list.add(new Entry(Type.E, 20, 6, "inpStepMotActive"));
        list.add(new Entry(Type.A,  0, 0, "outpLabelRewind"));
        list.add(new Entry(Type.A, 20, 6, "outpStartPrint"));
        list.add(new Entry(Type.A, 21, 0, "outpDisplayLspErrorMsg"));
        list.add(new Entry(Type.A, 21, 2, "outpLspStopStatus"));
        list.add(new Entry(Type.A, 21, 3, "outpLspReadyStatus"));
        list.add(new Entry(Type.A, 21, 4, "outpLspBusyStatus"));
        list.add(new Entry(Type.A, 21, 5, "outpLspResetStatus"));
        list.add(new Entry(Type.A, 21, 6, "outpLspErrorStatus"));
        list.add(new Entry(Type.A, 21, 7, "outpLabelPress"));
        // Initial output-state defaults (labeller idle/ready).
        for (Entry e : list) {
            if ("outpLspReadyStatus".equals(e.name())) e.value.set(1);
        }
        ENTRIES = Collections.unmodifiableList(list);
    }

    private SymbolTable() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static List<Entry> filter(boolean includeE, boolean includeA, boolean includeM) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if ((includeE && e.type == Type.E)
             || (includeA && e.type == Type.A)
             || (includeM && e.type == Type.M)) {
                out.add(e);
            }
        }
        return out;
    }

    public static Entry find(String name) {
        for (Entry e : ENTRIES) {
            if (e.name.equals(name)) return e;
        }
        return null;
    }

    /** Register a single listener. Replaces any previous listener. */
    public static void setListener(Consumer<Entry> l) {
        listener = l;
    }

    /**
     * Set the value of a symbol by name. Fires the listener when the value
     * actually changes. Returns false if the name is unknown.
     */
    public static boolean set(String name, int value) {
        Entry e = find(name);
        if (e == null) return false;
        int clamped = value == 0 ? 0 : 1;
        int previous = e.value.getAndSet(clamped);
        if (previous != clamped) {
            Consumer<Entry> l = listener;
            if (l != null) l.accept(e);
        }
        return true;
    }
}
