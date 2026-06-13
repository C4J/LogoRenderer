package com.commander4j.logorenderer;

/**
 * Scripted bit-flip sequence that mimics the I/O transitions a real labeller
 * would produce during a print cycle. Runs on a background thread so the UI
 * stays responsive. Each SymbolTable.set() call fires the change listener,
 * which pushes a STATE line to any subscribed client.
 *
 * If no client is subscribed (&REPORT has not been sent), the listener is a
 * no-op and the cycle simply updates the internal bit state.
 */
public final class PrintCycle {

    /** Delay between bit flips, in milliseconds. */
    private static final int STEP_MS = 40;

    private PrintCycle() {}

    public static void run() {
        Thread t = new Thread(PrintCycle::execute, "print-cycle");
        t.setDaemon(true);
        t.start();
    }

    private static void execute() {
        LogoStepState.set(LogoStepState.State.BUSY);

        flip("outpLspReadyStatus", 0);
        flip("outpLspBusyStatus",  1);
        flip("outpStartPrint",     1);
        flip("inpDataReady",       1);
        flip("inpStepMotActive",   1);

        // Actual print completes — stepper stops, print-done pulses.
        flip("inpStepMotActive",   0);
        flip("inpPrintDone",       1);
        flip("outpLabelPress",     1);
        flip("outpLabelPress",     0);

        // Reset back to idle.
        flip("outpStartPrint",     0);
        flip("inpPrintDone",       0);
        flip("inpDataReady",       0);
        flip("outpLspBusyStatus",  0);
        flip("outpLspReadyStatus", 1);

        LogoStepState.set(LogoStepState.State.READY);
    }

    private static void flip(String name, int value) {
        SymbolTable.set(name, value);
        try { Thread.sleep(STEP_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
