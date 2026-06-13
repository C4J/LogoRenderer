package com.commander4j.logorenderer;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Receives an Intel HEX upload from the client and writes it to a file.
 * Adapted from b6Logopak.Labeller_Protocol_IntelHex — uses no external
 * codec dependency.
 */
public class ComboProtocolIntelHex {

    private String filename = "";
    private File file;
    private final ServerCallback callback;

    public ComboProtocolIntelHex(ServerCallback callback) {
        this.callback = callback;
    }

    public void init(String filename) {
        this.filename = filename;
        file = new File(this.filename);
        // Ensure parent directory exists
        if (file.getParentFile() != null)
            file.getParentFile().mkdirs();
        // Truncate/create the file
        try (var _ = new FileOutputStream(file, false)) {
            // empty — just create/truncate
        } catch (IOException e) {
            callback.appendLog("HEXFILE init error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    /**
     * Parse one Intel HEX line.
     *
     * @return "ok", "finished", or "error"
     */
    public String parseLine(String hexdatastring) {
        if (hexdatastring == null || hexdatastring.length() < 9)
            return "ok";

        int recordLength = Integer.parseInt(hexdatastring.substring(1, 3), 16);
        String recordType = hexdatastring.substring(7, 9);

        if (recordLength == 0 || recordType.equals("01")) {
            finish();
            return "finished";
        }

        if (recordType.equals("00")) {
            String recordData = hexdatastring.substring(9, (recordLength * 2) + 9);
            try {
                byte[] bytes = hexStringToBytes(recordData);
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    fos.write(bytes);
                }
            } catch (Exception e) {
                callback.appendLog("HEXFILE write error: " + e.getMessage() + "\n", Color.RED);
                return "error";
            }
        }
        return "ok";
    }

    private void finish() {
        file = null;
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
