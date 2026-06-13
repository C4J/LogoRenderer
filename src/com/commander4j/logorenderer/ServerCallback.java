package com.commander4j.logorenderer;

import java.awt.Color;

/**
 * Callback interface implemented by ViewerFrame so that server components
 * (ComboServer, ComboResponder) can drive UI updates without depending on the
 * frame class directly.
 */
public interface ServerCallback {

    /** Append a coloured message to the comms log (called from any thread). */
    void appendLog(String msg, Color c);

    /**
     * The client sent a LOAD command. The server has already parsed the filename;
     * the callback is responsible for loading the file, rendering the canvas, and
     * populating ServerMemory.Fields.
     *
     * @param filename bare filename as received from client (e.g. "test.llf")
     */
    void onLoadCommand(String filename);

    /**
     * The client sent an FD command to set a field value.
     * The callback updates ServerMemory.Fields, the current LlfLayout, and
     * refreshes the canvas.
     *
     * @param fieldId field number as a string (e.g. "100")
     * @param value   new field value
     */
    void onFieldCommand(String fieldId, String value);

    /** Repopulate the field table from ServerMemory.Fields (called from any thread). */
    void populateFieldTable();

    /** Clear the field table (called when LK command is received). */
    void clearFieldTable();

    /**
     * Update the start/stop button state.
     *
     * @param running true = server is running (show "Stop Server"), false = stopped
     */
    void setServerRunning(boolean running);

    /**
     * Refresh the buffer-count display after a log buffer is consumed (*DELLOG).
     * Called from the server thread.
     */
    void updateBufferCount();
}
