package com.commander4j.logorenderer;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;
import java.util.regex.Pattern;

/**
 * Utility methods for the TCP server — control character encoding, path
 * resolution, IP discovery, etc.
 */
public class ComboServerUtils {

    // -----------------------------------------------------------------------
    // IP discovery
    // -----------------------------------------------------------------------

    public Vector<String> getHostIPAddresses() {
        Vector<String> ips = new Vector<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                if (netint.isUp()) {
                    Enumeration<InetAddress> addresses = netint.getInetAddresses();
                    for (InetAddress addr : Collections.list(addresses)) {
                        String ip = addr.getHostAddress();
                        if (ip.contains(".")) {   // IPv4 only
                            ips.add(ip);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return ips;
    }

    // -----------------------------------------------------------------------
    // File paths
    // -----------------------------------------------------------------------

    /**
     * Resolve a bare filename (e.g. "test.llf" or "/c0/test.llf") to a full
     * path under virtual_disk/c0/ in the working directory.
     */
    public String getFullPath(String filename) {
        filename = filename.replace("/c0/", "").replace("\\c0\\", "");
        if (!filename.startsWith(String.valueOf(File.separatorChar))) {
            return System.getProperty("user.dir")
                    + File.separator + "virtual_disk"
                    + File.separator + "c0"
                    + File.separator + filename;
        }
        return filename;
    }

    /**
     * Returns the virtual_disk subfolder name for a given filename, following
     * the default paths table from the LAMA manual.
     */
    public String getSubfolderForFile(String filename) {
        String lower = filename.toLowerCase();
        if (lower.equals("mcp.bin") || lower.equals("settings.plpro")) return "f0";
        if (lower.equals("logfile.txt") || lower.equals("logbak.txt"))  return "c0";
        if (lower.equals("account.txt") || lower.equals("servicemessage.text")) return "c9";
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot) : "";
        return switch (ext) {
            case ".gui" -> "f0";
            case ".ini", ".sup", ".lsp", ".errtext", ".msgtext", ".lsptext" -> "c9";
            default -> "c0";
        };
    }

    // -----------------------------------------------------------------------
    // String helpers
    // -----------------------------------------------------------------------

    public String[] parseParam(String command) {
        return command.split(",");
    }

    public String replaceNullStringwithBlank(String value) {
        return value == null ? "" : value;
    }

    public String formatPortID(String port) {
        return port.toUpperCase();
    }

    public void pause(int milliseconds) {
        try { Thread.sleep(milliseconds); } catch (InterruptedException ignored) {}
    }

    // -----------------------------------------------------------------------
    // Control character encoding
    // -----------------------------------------------------------------------

    public String decodeControlChars(String input) {
        String r = input;
        r = r.replaceAll(Pattern.quote("\u0000"), "<NUL>");
        r = r.replaceAll(Pattern.quote("\u0001"), "<SOH>");
        r = r.replaceAll(Pattern.quote("\u0002"), "<STX>");
        r = r.replaceAll(Pattern.quote("\u0003"), "<ETX>");
        r = r.replaceAll(Pattern.quote("\u0004"), "<EOT>");
        r = r.replaceAll(Pattern.quote("\u0005"), "<ENQ>");
        r = r.replaceAll(Pattern.quote("\u0006"), "<ACK>");
        r = r.replaceAll(Pattern.quote("\u0007"), "<BEL>");
        r = r.replaceAll(Pattern.quote("\u0008"), "<BS>");
        r = r.replaceAll(Pattern.quote("\t"),     "<HT>");
        r = r.replaceAll(Pattern.quote("\n"),     "<LF>");
        r = r.replaceAll(Pattern.quote("\u000B"), "<VT>");
        r = r.replaceAll(Pattern.quote("\u000C"), "<FF>");
        r = r.replaceAll(Pattern.quote("\r"),     "<CR>");
        r = r.replaceAll(Pattern.quote("\u000E"), "<SO>");
        r = r.replaceAll(Pattern.quote("\u000F"), "<SI>");
        r = r.replaceAll(Pattern.quote("\u0010"), "<DLE>");
        r = r.replaceAll(Pattern.quote("\u0011"), "<DC1>");
        r = r.replaceAll(Pattern.quote("\u0012"), "<DC2>");
        r = r.replaceAll(Pattern.quote("\u0013"), "<DC3>");
        r = r.replaceAll(Pattern.quote("\u0014"), "<DC4>");
        r = r.replaceAll(Pattern.quote("\u0015"), "<NAK>");
        r = r.replaceAll(Pattern.quote("\u0016"), "<SYN>");
        r = r.replaceAll(Pattern.quote("\u0017"), "<ETB>");
        r = r.replaceAll(Pattern.quote("\u0018"), "<CAN>");
        r = r.replaceAll(Pattern.quote("\u0019"), "<EM>");
        r = r.replaceAll(Pattern.quote("\u001A"), "<SUB>");
        r = r.replaceAll(Pattern.quote("\u001B"), "<ESC>");
        r = r.replaceAll(Pattern.quote("\u001C"), "<FS>");
        r = r.replaceAll(Pattern.quote("\u001D"), "<GS>");
        r = r.replaceAll(Pattern.quote("\u001E"), "<RS>");
        r = r.replaceAll(Pattern.quote("\u001F"), "<US>");
        return r;
    }

    public String encodeControlChars(String input) {
        String r = input;
        r = r.replaceAll(Pattern.quote("<NUL>"), "\u0000");
        r = r.replaceAll(Pattern.quote("<SOH>"), "\u0001");
        r = r.replaceAll(Pattern.quote("<STX>"), "\u0002");
        r = r.replaceAll(Pattern.quote("<ETX>"), "\u0003");
        r = r.replaceAll(Pattern.quote("<EOT>"), "\u0004");
        r = r.replaceAll(Pattern.quote("<ENQ>"), "\u0005");
        r = r.replaceAll(Pattern.quote("<ACK>"), "\u0006");
        r = r.replaceAll(Pattern.quote("<BEL>"), "\u0007");
        r = r.replaceAll(Pattern.quote("<BS>"),  "\u0008");
        r = r.replaceAll(Pattern.quote("<HT>"),  "\u0009");
        r = r.replaceAll(Pattern.quote("<LF>"),  "\n");
        r = r.replaceAll(Pattern.quote("<VT>"),  "\u000B");
        r = r.replaceAll(Pattern.quote("<FF>"),  "\u000C");
        r = r.replaceAll(Pattern.quote("<CR>"),  "\r");
        r = r.replaceAll(Pattern.quote("<SO>"),  "\u000E");
        r = r.replaceAll(Pattern.quote("<SI>"),  "\u000F");
        r = r.replaceAll(Pattern.quote("<DLE>"), "\u0010");
        r = r.replaceAll(Pattern.quote("<DC1>"), "\u0011");
        r = r.replaceAll(Pattern.quote("<DC2>"), "\u0012");
        r = r.replaceAll(Pattern.quote("<DC3>"), "\u0013");
        r = r.replaceAll(Pattern.quote("<DC4>"), "\u0014");
        r = r.replaceAll(Pattern.quote("<NAK>"), "\u0015");
        r = r.replaceAll(Pattern.quote("<SYN>"), "\u0016");
        r = r.replaceAll(Pattern.quote("<ETB>"), "\u0017");
        r = r.replaceAll(Pattern.quote("<CAN>"), "\u0018");
        r = r.replaceAll(Pattern.quote("<EM>"),  "\u0019");
        r = r.replaceAll(Pattern.quote("<SUB>"), "\u001A");
        r = r.replaceAll(Pattern.quote("<ESC>"), "\u001B");
        r = r.replaceAll(Pattern.quote("<FS>"),  "\u001C");
        r = r.replaceAll(Pattern.quote("<GS>"),  "\u001D");
        r = r.replaceAll(Pattern.quote("<RS>"),  "\u001E");
        r = r.replaceAll(Pattern.quote("<US>"),  "\u001F");
        return r;
    }
}
