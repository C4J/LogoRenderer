package com.commander4j.logorenderer;

/**
 * Current LOGOSTEP run state, as reported by &LOGOSTEPSTATE.
 * One of STOP / READY / BUSY / RESET / ERROR.
 */
public final class LogoStepState {

    public enum State { STOP, READY, BUSY, RESET, ERROR }

    private static volatile State current = State.READY;

    private LogoStepState() {}

    public static State get() {
        return current;
    }

    public static void set(State s) {
        if (s != null) current = s;
    }
}
