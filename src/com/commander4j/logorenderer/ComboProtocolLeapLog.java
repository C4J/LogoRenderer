package com.commander4j.logorenderer;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Implements the Logopak *TRXLOG stop-and-wait Intel HEX transfer protocol.
 * Adapted from b6Logopak.Labeller_Protocol_LeapLog.
 */
public class ComboProtocolLeapLog {

    private static final int BLOCK_SIZE = 32;

    private boolean pending = false;
    private List<String> logLines = null;
    private final ServerCallback callback;

    public ComboProtocolLeapLog(ServerCallback callback) {
        this.callback = callback;
    }

    public void prepare(List<String> lines) {
        this.logLines = lines;
        this.pending = true;
    }

    public boolean isPending() {
        return pending;
    }

    public void transfer(DataInputStream inp, DataOutputStream outp) {
        pending = false;
        try {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append('\r');
            }
            byte[] data = sb.toString().getBytes(StandardCharsets.US_ASCII);
            int totalBytes = data.length;

            callback.appendLog("TRXLOG: sending " + logLines.size() + " line(s), " + totalBytes + " bytes\n", Color.GREEN);

            int address = 0;
            while (address < totalBytes) {
                int blockSize = Math.min(BLOCK_SIZE, totalBytes - address);
                String record = buildRecord(address, data, address, blockSize);
                sendRecord(record, outp);
                waitForAck(inp);
                address += blockSize;
            }

            sendRecord(":0000000000", outp);
            waitForAck(inp);

            callback.appendLog("TRXLOG: transfer complete.\n", Color.GREEN);

        } catch (Exception e) {
            callback.appendLog("TRXLOG error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    private void sendRecord(String record, DataOutputStream outp) throws Exception {
        callback.appendLog(record + "\n", Color.BLUE);
        outp.writeBytes(record + "\r");
        outp.flush();
    }

    private void waitForAck(DataInputStream inp) {
        try {
            while (true) {
                int b = inp.read();
                if (b == '\r' || b == -1) return;
            }
        } catch (Exception ignored) {}
    }

    private String buildRecord(int address, byte[] data, int offset, int length) {
        int checksum = length + ((address >> 8) & 0xFF) + (address & 0xFF);
        StringBuilder sb = new StringBuilder();
        sb.append(':');
        sb.append(String.format("%02X", length));
        sb.append(String.format("%04X", address));
        sb.append("00");
        for (int i = 0; i < length; i++) {
            int b = data[offset + i] & 0xFF;
            sb.append(String.format("%02X", b));
            checksum += b;
        }
        sb.append(String.format("%02X", (0x100 - (checksum & 0xFF)) & 0xFF));
        return sb.toString();
    }
}
