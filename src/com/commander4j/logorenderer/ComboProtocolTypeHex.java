package com.commander4j.logorenderer;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;

/**
 * Sends a file to the client using the Logopak TypeHex protocol.
 * Adapted from b6Logopak.Labeller_Protocol_TypeHex.
 */
public class ComboProtocolTypeHex {

    private static final int BLOCK_SIZE = 255;

    private String filename = "";
    private boolean pending = false;
    private final ServerCallback callback;

    public ComboProtocolTypeHex(ServerCallback callback) {
        this.callback = callback;
    }

    public void prepare(String filename) {
        this.filename = filename;
        this.pending = true;
    }

    public boolean isPending() {
        return pending;
    }

    public void transfer(DataInputStream inp, DataOutputStream outp) {
        pending = false;
        try {
            File file = new File(filename);
            if (!file.exists()) {
                callback.appendLog("TYPEHEX error — file not found: " + filename + "\n", Color.RED);
                return;
            }

            byte[] fileData = Files.readAllBytes(file.toPath());
            int totalBytes = fileData.length;
            int address = 0;

            callback.appendLog("TYPEHEX sending: " + file.getName() + " (" + totalBytes + " bytes)\n", Color.GREEN);

            while (address < totalBytes) {
                int blockSize = Math.min(BLOCK_SIZE, totalBytes - address);
                String record = buildRecord(address, fileData, address, blockSize);
                sendRecord(record, outp);
                waitForAck(inp);
                address += blockSize;
            }

            // Zero-length record signals end of file
            sendRecord(buildRecord(0, new byte[0], 0, 0), outp);
            waitForAck(inp);

            callback.appendLog("TYPEHEX transfer complete.\n", Color.GREEN);

        } catch (Exception e) {
            callback.appendLog("TYPEHEX error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    private void sendRecord(String record, DataOutputStream outp) throws Exception {
        callback.appendLog("-> " + record + "\n", Color.BLUE);
        outp.writeBytes(record + "\r\n");
        outp.flush();
    }

    private void waitForAck(DataInputStream inp) {
        try {
            while (true) {
                int b = inp.read();
                if (b == 0x06 || b == -1) return;
            }
        } catch (Exception ignored) {}
    }

    private String buildRecord(int address, byte[] data, int offset, int length) {
        int checksum = length + ((address >> 8) & 0xFF) + (address & 0xFF);
        StringBuilder sb = new StringBuilder();
        sb.append(":");
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
