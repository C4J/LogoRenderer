package com.commander4j.logorenderer;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

/**
 * Processes Logopak protocol commands received from the TCP client.
 * Independent compatibility implementation for local testing — not a Logopak
 * product; see README.txt.
 * Adapted from b6Logopak.Responder — IO-port commands are kept for protocol
 * compatibility but are no-ops; LOAD and FD drive label rendering via the
 * ServerCallback.
 */
public class ComboResponder {

    private final ComboServerUtils utils = new ComboServerUtils();
    private final ServerCallback callback;

    final ComboProtocolIntelHex intelHex;
    final ComboProtocolTypeHex  typeHex;
    final ComboProtocolLeapLog  leapLog;

    public ComboResponder(ServerCallback callback) {
        this.callback = callback;
        this.intelHex = new ComboProtocolIntelHex(callback);
        this.typeHex  = new ComboProtocolTypeHex(callback);
        this.leapLog  = new ComboProtocolLeapLog(callback);
    }

    // -----------------------------------------------------------------------
    // Static query responses
    // -----------------------------------------------------------------------

    private ComboResult command_ONLINE()     { return ok("<CR>>ONLINE<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_VERSIONS()   { return ok("<CR>AGENT 7.403 Versions<CR>LACE 7.4121<CR>GUI 7.411<CR>PRINT 7.404<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_VERSION()    { return ok("7.403<CR><CR>0 No error(s).<CR>CMD->"); }
    private ComboResult command_LINE()       { return ok("<CR>>Line A<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_PRINTERTYPE(){ return ok("<CR>>VLP 220III F Softwarehaus<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_CUSTOMER()   { return ok("<CR>>Simulator<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_AK()         { return ok("<CR>>88200<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_DATAREADY()  { return ok("<CR>>DataReady=" + (ServerMemory.dataReady ? 1 : 0) + "<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_CPUBOARD()   { return ok("<CR>>0000<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_SERIALNUMBER(){ return ok("<CR>>COMBO-001<CR>0 No Error(s)<CR>CMD->"); }
    private ComboResult command_P()          { return new ComboResult(); }
    private ComboResult command_VERBOSE()    { return ok("<CR>0 No error(s).<CR>CMD->"); }
    private ComboResult command_RDR() {
        ServerMemory.dataReady = false;
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_SDR() {
        ServerMemory.dataReady = true;
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    // -----------------------------------------------------------------------
    // AGENT diagnostic queries — formats captured from a physical PL3
    // (515 III TB, AGENT 7.401). Bodies are minimal; only the response
    // shape is replicated so clients that probe these commands see the
    // expected footer/header pattern rather than a 1200 unknown NAK.
    // -----------------------------------------------------------------------

    private ComboResult command_ALLOCLIST() {
        // Real printer returns header + CMD-> with no success footer.
        return ok("<CR>AGENT 7.403 Allocated Memory List Dump<CR>CMD->");
    }

    private ComboResult command_DUMPSIZES() {
        return ok("<CR>AGENT 7.403 Internal Sizes<CR>"
                + ">LRTK System    = 0<CR>"
                + ">LRTK Task Home = 0<CR>"
                + ">LRTK Task Ctrl = 0<CR>"
                + ">CMOS Data      = 0<CR>"
                + ">CMOS Counter   = 0<CR>"
                + ">LAMA Home      = 0<CR>"
                + ">LAMA Point     = 0<CR>"
                + ">LAMA Message   = 0<CR>"
                + "0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_ERRORS() {
        return ok("<CR>AGENT 7.403 Global Error history<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_ERRTASK(String[] parts) {
        // Arg-required command; returns 1201 when the task name is unknown
        // or missing. We don't simulate tasks, so everything is unknown.
        String arg = (parts.length > 1 && !parts[1].isEmpty()) ? parts[1] : "-";
        return ok("<CR>1201 Task \"" + arg + "\" unknown!<CR>CMD->");
    }

    private ComboResult command_LAMAQUEUE() {
        return ok("<CR>AGENT 7.403 Lama Queues<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_LANGUAGE() {
        return ok("<CR>AGENT 7.403 Language Use (ENG)<CR>"
                + "   Error table entries: 0<CR>"
                + " Message table entries: 0<CR>"
                + "0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_MEMINFO() {
        // Real printer emits `No Error(s)` WITHOUT the leading `0 ` for this
        // one command. Preserving that quirk for fidelity.
        return ok("<CR>c0:2985984,2998016<CR>"
                + "c9:959488,1048320<CR>"
                + "f0:19660800,33554432<CR>"
                + "dram:239866644,258112936<CR>"
                + "No Error(s)<CR>CMD->");
    }

    private ComboResult command_STOPS() {
        return ok("<CR>AGENT 7.403 Applicator Stops History<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_SYSINFO() {
        long uptimeSec = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        long hh = uptimeSec / 3600;
        long mm = (uptimeSec % 3600) / 60;
        long ss = uptimeSec % 60;
        String uptime = String.format("%d:%02d:%02d", hh, mm, ss);
        return ok("<CR>AGENT 7.403 System Information<CR>"
                + "Resolution=11.8 dots/mm<CR>"
                + "Uptime=" + uptime + "<CR>"
                + "C0_size=2998016<CR>"
                + "C0_free=2985984<CR>"
                + "0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_TASKS() {
        // Real printer returns one line per RTOS task, no header or footer.
        // The separator after `TASK N:` is a literal tab byte (0x09); we
        // emit it as the <HT> control token which encodeControlChars maps
        // back to 0x09 on the wire.
        return ok("<CR> TASK 1:<HT>Simulator      Stack[@00000000:032000 Bytes, MaxUsed 0 Bytes (00%)] Pri[10,10] :RUN<CR>CMD->");
    }

    private ComboResult command_UNTRANSLATED() {
        return ok("<CR>AGENT 7.403 Untranslated Strings, ENG<CR>"
                + "<CR>Errors:<CR>"
                + "<CR>Messages:<CR>"
                + "0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_UNUSEDMEM() {
        return ok("<CR>>245052 of 252063 KB (at Start) free<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_SERVICECOUNTER() {
        // 31 groups of three lines each (ServiceCnt / ControlVal / TotalCnt),
        // all zero. Column alignment matches real printer output — colon at
        // character position 14 on every line.
        StringBuilder sb = new StringBuilder("<CR>CMOS service counter v0.973<CR>");
        for (int i = 1; i <= 31; i++) {
            String n = String.format("%02d", i);
            sb.append("ServiceCnt_").append(n).append(" :0<CR>");
            sb.append("ControlVal_").append(n).append(" :0<CR>");
            sb.append("TotalCnt_").append(n).append("   :0<CR>");
        }
        sb.append("<CR>0 No Error(s)<CR>CMD->");
        return ok(sb.toString());
    }

    private ComboResult command_SERVICEDONE() {
        // No-op on real printer — clears a pending service flag. We just ACK.
        return ok("<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_SERVICESTATUS() {
        // Body is a single digit; `0` = no services due.
        return ok("<CR>0<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_TIMESTAMPS() {
        // Populated by &TIMESTAMP,<symbol>. Empty buffer → just header+footer.
        return ok("<CR>AGENT 7.403 Time Stamp Buffer<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_TRONGET() {
        // Trace-on mask; empty mask = no tracing enabled.
        return ok("<CR>AGENT 7.403 Tron settings.<CR>mask=<CR>0 No Error(s)<CR>CMD->");
    }

    // -----------------------------------------------------------------------
    // Log / pallet-simulation commands
    // -----------------------------------------------------------------------

    private ComboResult command_LOGINFO() {
        String status;
        synchronized (ServerMemory.logBuffers) {
            int count = ServerMemory.logBuffers.size();
            status = count == 0 ? "00" : count == 1 ? "01" : "11";
        }
        return ok(status + "<CR><CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_TRXLOG() {
        ComboResult result = new ComboResult();
        List<String> buffer;
        synchronized (ServerMemory.logBuffers) {
            if (ServerMemory.logBuffers.isEmpty()) {
                result.setResponse("<CR>992 No log data available<CR>CMD->");
                result.setResult(false);
                return result;
            }
            buffer = ServerMemory.logBuffers.peek();
        }
        leapLog.prepare(new ArrayList<>(buffer));
        result.setResponse("");
        return result;
    }

    private ComboResult command_UNKNOWN(String commandName) {
        // Three different unknown-command formats captured from real PL3:
        //   - AGENT (?...) : `1200 Request "<name>" unknown!`  (from ?batterylow)
        //   - LACE  (*...) : `1601 Unknown command: <name>!`   (from *ERROR)
        //   - LSP   (&...) : `714 LSP: Unknown command !`      (from &CAN — note:
        //                     no name echoed, trailing space before the `!`)
        if (commandName.isEmpty()) {
            return ok("<CR>1601 Unknown command: !<CR>CMD->");
        }
        char sigil = commandName.charAt(0);
        String name = ("?*&".indexOf(sigil) >= 0) ? commandName.substring(1) : commandName;
        String body;
        switch (sigil) {
            case '?'  -> body = "1200 Request \"" + name + "\" unknown!";
            case '&'  -> body = "714 LSP: Unknown command !";
            default   -> body = "1601 Unknown command: " + name + "!";
        }
        return ok("<CR>" + body + "<CR>CMD->");
    }

    private ComboResult command_GETTIME() {
        // Real PL3 *GETTIME response has NO leading CR — the timestamp body
        // starts immediately after the previous CMD-> prompt. Verified with
        // back-to-back *SETTIME/*GETTIME capture.
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return ok(ts + "<CR><CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_SETTIME(String[] parts) {
        // Accept and acknowledge; we do not modify the host OS clock.
        // File mtimes in virtual_disk continue to come from the host clock,
        // and *GETTIME returns the same host clock, so reported times and
        // directory timestamps remain internally consistent.
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_MEM() {
        return ok("2916 of 2927 Kb (99%) free<CR><CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_FONTS() {
        // Minimal representative font list; a real PL3 returns 30+ .fnt/.spd
        // files from CMOS. We emit a handful so callers that iterate the
        // list see the expected shape (one filename per line, LAMA footer
        // with no blank line preceding it).
        return ok("sw030rsn.fnt<CR>sw050rsn.fnt<CR>sw050bsn.fnt<CR>sw060bsn.fnt<CR>"
                + "ean.fnt<CR>upc.fnt<CR>"
                + "0 No error(s).<CR>CMD->");
    }

    private ComboResult command_YMODEMERROR() {
        // Quirk: when there is no YMODEM error, the body is literally
        // `0 No error(s).` — the same text as the footer. Captured verbatim.
        return ok("0 No error(s).<CR><CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_GETCOUNT(String[] parts) {
        // Real PL3 behaviour (valid id range per docs is 0..255):
        //   in-range + defined   → `C<id>,<value>` body + blank line + LAMA footer
        //   in-range + undefined → `<CR>979 Global counter <id> not defined!<CR>CMD->`
        //   out-of-range         → `<CR>978 Global counter id <id> out of range!<CR>CMD->`
        // The defined set lives in ServerMemory.globalCounters so that
        // *SETCOUNT followed by *GETCOUNT round-trips correctly.
        String idStr = parts.length > 1 ? parts[1] : "";
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException nfe) {
            return ok("<CR>979 Global counter " + idStr + " not defined!<CR>CMD->");
        }
        if (id < 0 || id > 255) {
            return ok("<CR>978 Global counter id " + id + " out of range!<CR>CMD->");
        }
        Integer value = ServerMemory.globalCounters.get(id);
        if (value == null) {
            return ok("<CR>979 Global counter " + id + " not defined!<CR>CMD->");
        }
        return ok("C" + id + "," + value + "<CR><CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_SETCOUNT(String[] parts) {
        // Mirrors *GETCOUNT error paths; writing to an undefined counter
        // returns 979 (real PL3 won't implicitly define a counter via SET).
        // The success path ACKs with a standard LAMA footer.
        String idStr  = parts.length > 1 ? parts[1] : "";
        String valStr = parts.length > 2 ? parts[2] : "";
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException nfe) {
            return ok("<CR>979 Global counter " + idStr + " not defined!<CR>CMD->");
        }
        if (id < 0 || id > 255) {
            return ok("<CR>978 Global counter id " + id + " out of range!<CR>CMD->");
        }
        if (!ServerMemory.globalCounters.containsKey(id)) {
            return ok("<CR>979 Global counter " + id + " not defined!<CR>CMD->");
        }
        int value;
        try {
            value = Integer.parseInt(valStr);
        } catch (NumberFormatException nfe) {
            return ok("<CR>995 Receive format error! (NULL-pointer)<CR>CMD->");
        }
        ServerMemory.globalCounters.put(id, value);
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_DELLOG() {
        synchronized (ServerMemory.logBuffers) {
            if (!ServerMemory.logBuffers.isEmpty()) {
                ServerMemory.logBuffers.poll();
                callback.updateBufferCount();
            }
        }
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    // -----------------------------------------------------------------------
    // IO port commands — no-op stubs kept for protocol compatibility
    // -----------------------------------------------------------------------

    private ComboResult command_REPORT(String[] parts) {
        ServerMemory.reportingEnabled = true;
        return ok("<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_NOREPORT(String[] parts) {
        ServerMemory.reportingEnabled = false;
        return ok("<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_LOGOSTEPSTATE() {
        StringBuilder sb = new StringBuilder("<CR>");
        sb.append(">").append(LogoStepState.get().name()).append("<CR>");
        sb.append("0 No Error(s)<CR>CMD->");
        return ok(sb.toString());
    }

    private ComboResult command_LOGICSETUP(String[] parts) {
        // Setter form: &LOGICSETUP,NAME=nnn;NAME=nnn;...
        if (parts.length > 1 && !parts[1].isEmpty()) {
            String payload = parts[1];
            for (int i = 2; i < parts.length; i++) payload += "," + parts[i];

            for (String assignment : payload.split("[;,]")) {
                assignment = assignment.trim();
                if (assignment.isEmpty()) continue;
                int eq = assignment.indexOf('=');
                if (eq <= 0) {
                    ComboResult r = new ComboResult();
                    r.setResponse("<CR>Invalid argument " + assignment + "<CR>CMD->");
                    r.setResult(false);
                    return r;
                }
                String name = assignment.substring(0, eq).trim();
                String valStr = assignment.substring(eq + 1).trim();
                int value;
                try {
                    value = Integer.parseInt(valStr);
                } catch (NumberFormatException nfe) {
                    ComboResult r = new ComboResult();
                    r.setResponse("<CR>Invalid value " + valStr + "<CR>CMD->");
                    r.setResult(false);
                    return r;
                }
                if (!LogicSetup.set(name, value)) {
                    ComboResult r = new ComboResult();
                    r.setResponse("<CR>Invalid setup entry " + name + "=" + value + "<CR>CMD->");
                    r.setResult(false);
                    return r;
                }
            }
            return ok("<CR>0 No Error(s)<CR>CMD->");
        }

        // Getter form: return all entries.
        StringBuilder sb = new StringBuilder("<CR>");
        for (var e : LogicSetup.entries().entrySet()) {
            sb.append(">").append(e.getKey()).append("=").append(e.getValue()).append("<CR>");
        }
        sb.append("0 No Error(s)<CR>CMD->");
        return ok(sb.toString());
    }

    private ComboResult command_SYMBOLTABLE(String[] parts) {
        String flags = (parts.length > 1 ? parts[1] : "").toUpperCase();
        boolean includeE, includeA, includeM;
        if (flags.isEmpty()) {
            includeE = true;
            includeA = true;
            includeM = false;
        } else {
            includeE = flags.indexOf('E') >= 0;
            includeA = flags.indexOf('A') >= 0;
            includeM = flags.indexOf('M') >= 0;
        }

        StringBuilder sb = new StringBuilder("<CR>");
        for (SymbolTable.Entry e : SymbolTable.filter(includeE, includeA, includeM)) {
            sb.append(">").append(e.address()).append(" ").append(e.name()).append("<CR>");
        }
        sb.append("0 No Error(s)<CR>CMD->");
        return ok(sb.toString());
    }

    private ComboResult command_SET(String[] parts)     { return new ComboResult(); }
    private ComboResult command_RESET(String[] parts)   { return new ComboResult(); }

    private ComboResult command_CHECK(String[] parts) {
        // Real PL3 rejects symbolic names here with 713. Old V1.0 syntax
        // probably expects a different format; mirroring the NAK.
        return ok("<CR>713 LSP: Format error !<CR>CMD->");
    }

    private ComboResult command_UNCHECK(String p1, String p2) {
        // Same as &CHECK — symbolic names rejected with 713 on real PL3.
        return ok("<CR>713 LSP: Format error !<CR>CMD->");
    }

    private ComboResult command_OK() {
        // Old V1.0 alias — real printer returns identical body to ?online.
        return ok("<CR>>ONLINE<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_NOTIMESTAMPS() {
        // Clears every symbol subscribed via &TIMESTAMP.
        ServerMemory.timestampSubscriptions.clear();
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_TIMESTAMP(String[] parts) {
        // Subscribes a LOGOSTEP symbol to the timestamp ring buffer.
        // Unknown symbol → 713 Format error (matches &TEST behaviour).
        String name = parts.length > 1 ? parts[1] : "";
        if (SymbolTable.find(name) == null) {
            return ok("<CR>713 LSP: Format error !<CR>CMD->");
        }
        ServerMemory.timestampSubscriptions.add(name);
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_UNTIMESTAMP(String[] parts) {
        String name = parts.length > 1 ? parts[1] : "";
        if (SymbolTable.find(name) == null) {
            return ok("<CR>713 LSP: Format error !<CR>CMD->");
        }
        ServerMemory.timestampSubscriptions.remove(name);
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_NODESYMBOLS() {
        // CAN-node symbol map. Real PL3 returns one line per node with 16
        // symbol slots (8 inputs + 8 outputs). Our simulator doesn't model
        // CAN, so we emit a single plausible-looking stub line.
        return ok("<CR>>CANIO_00,0,00,00,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.>,<N.C.><CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_GETSTAT(String[] parts) {
        // Real PL3 rejected logoclient's IN001/OUT001 port-address format
        // with `713 LSP: Format error !`. Our simulator mirrors that —
        // GETSTAT expects a port address format we don't model, so we
        // always NAK rather than fake a value.
        return ok("<CR>713 LSP: Format error !<CR>CMD->");
    }

    private ComboResult command_TEST(String[] parts) {
        // Symbol lookup: if the name is in our SymbolTable, report its
        // current value via a STATE push glued to the ACK (matches the
        // real byte stream — no CR between CMD-> and STATE). Unknown
        // symbols get the same 713 Format error as GETSTAT.
        String name = parts.length > 1 ? parts[1] : "";
        SymbolTable.Entry entry = SymbolTable.find(name);
        if (entry == null) {
            return ok("<CR>713 LSP: Format error !<CR>CMD->");
        }
        return ok("<CR>0 No error(s).<CR>CMD->STATE," + name + "=" + entry.value() + "<CR>");
    }

    private ComboResult command_RESTABLE() {
        // Old V1.0 LSP syntax — uses LAMA-style footer (lowercase + period).
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_LISTTABLE() {
        // Old V1.0 LSP syntax — uses LAMA-style footer (lowercase + period).
        return ok("<CR>0 No error(s).<CR>CMD->");
    }

    private ComboResult command_LABELLERTYPE() {
        return ok("<CR>>MACHINE<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_TIMESSETUP(String[] parts) {
        // Getter form only — returns the timing/timeout table. Values are
        // plausible defaults; change if a client needs specific values.
        return ok("<CR>>BLOW_OFF_TIME=50<CR>"
                + ">ROLL_ON_TIME=0<CR>"
                + ">MAIN_AIR_ON_TOUT=1500<CR>"
                + ">VACU_DEBOUNC_TOUT=200<CR>"
                + ">VACUUM_ON_TOUT=3000<CR>"
                + ">TELE1_OUT_TIME=250<CR>"
                + ">TELE1_IN_TIME=50<CR>"
                + ">TELE1_ACTION_TOUT=4000<CR>"
                + ">PAD_SWING_ON_TIME=1500<CR>"
                + ">PAD_SWING_OFF_TIME=750<CR>"
                + ">TRIG2_DELAY_TIME=0<CR>"
                + ">LABEL_UNWIND_TOUT=4000<CR>"
                + ">CARBON_ACTION_TOUT=5000<CR>"
                + ">REW_AFTER_PRINT_RDY=0<CR>"
                + ">LABEL_REWIND_TOUT=4000<CR>"
                + ">SCANNER_TOUT=5000<CR>"
                + ">FLAP_ACTION_TOUT=4000<CR>"
                + ">CLOSE_FLAP_AFTER=0<CR>"
                + ">PRINTER_TOUT=25000<CR>"
                + ">DATA_RESET_TOUT=8000<CR>"
                + ">FLAP_TOGGLE_TIME=50<CR>"
                + ">APPLY_DONE_MIN_TIME=0<CR>"
                + ">SCAN_BAD_READ_SIGNAL=300<CR>"
                + ">HOST_ANSWER_TOUT=3000<CR>"
                + "0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_NODELIST() {
        // CAN-node list — `<name>,<input_state_hex>,<output_state_hex>`.
        return ok("<CR>>CANIO_00,14,00<CR>"
                + ">CANIO_01,00,0e<CR>"
                + ">CANIO_02,49,09<CR>"
                + ">CANRC_00,00,00<CR>"
                + "0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_TIMESTAMPLIST() {
        // One line per subscribed symbol: `><address> <name>`. Empty list
        // → just the success footer. AGENT-style footer (capital E).
        StringBuilder sb = new StringBuilder("<CR>");
        for (String name : ServerMemory.timestampSubscriptions) {
            SymbolTable.Entry e = SymbolTable.find(name);
            if (e != null) {
                sb.append(">").append(e.address()).append(" ").append(e.name()).append("<CR>");
            }
        }
        sb.append("0 No Error(s)<CR>CMD->");
        return ok(sb.toString());
    }

    private ComboResult command_INFOLINES() {
        // 5 LOGOSTEP display lines — empty with one padded line at row 3.
        return ok("<CR>><CR>><CR>>       <CR>><CR>><CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_MESSAGELINES() {
        // 6 display lines; line 1 reflects the LOGOSTEP state (e.g. STOP).
        String state = LogoStepState.get().name();
        return ok("<CR>>" + state + "<CR>><CR>><CR>>       <CR>><CR>><CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_ERRORLINES() {
        // 2 error display lines — empty when no errors active.
        return ok("<CR>><CR>><CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_CANRCLIST() {
        // Live CAN RC-node sensor readings. Columns appear to be temps/
        // voltages/fan values per channel; exact semantics unknown.
        // Single-node stub with plausible values captured from real printer.
        return ok("<CR>>CANRC_00,12.0,0,23.5,0,7.0,35<CR>0 No Error(s)<CR>CMD->");
    }

    // -----------------------------------------------------------------------
    // Field commands
    // -----------------------------------------------------------------------

    private ComboResult command_LK(String[] parts) {
        callback.clearFieldTable();
        return ok("<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_LOAD(String[] parts) {
        if (parts.length < 2 || parts[1].isEmpty()) {
            ComboResult result = new ComboResult();
            result.setResponse("<CR>63 Couldn't open file  !<CR>CMD->");
            result.setResult(false);
            return result;
        }
        callback.onLoadCommand(parts[1]);
        return ok("<CR>0 No Error(s)<CR>CMD->");
    }

    private ComboResult command_FD(String[] parts) {
        String fieldId = parts.length > 1 ? utils.replaceNullStringwithBlank(parts[1]) : "";
        String value   = parts.length > 2 ? utils.replaceNullStringwithBlank(parts[2]) : "";

        if (fieldId.isEmpty()) {
            ComboResult result = new ComboResult();
            result.setResponse("<CR>Invalid field number<CR>CMD->");
            result.setResult(false);
            return result;
        }

        if (ServerMemory.Fields.containsKey(fieldId)) {
            callback.onFieldCommand(fieldId, value);
            return ok("<CR>0 No Error(s)<CR>CMD->");
        }
        // Real printer: `31 Illegal character [<ch>] in received Id !`
        // where <ch> is the offending digit from the parser. We approximate
        // by using the last character of the id — sufficient for matching
        // the NAK format in test harnesses.
        char offender = fieldId.isEmpty() ? ' ' : fieldId.charAt(fieldId.length() - 1);
        ComboResult result = new ComboResult();
        result.setResponse("<CR>31 Illegal character [" + offender + "] in received Id !<CR>CMD->");
        result.setResult(false);
        return result;
    }

    // -----------------------------------------------------------------------
    // File commands
    // -----------------------------------------------------------------------

    private ComboResult command_FILES() {
        return command_DIR(new String[]{ "*DIR", "*.*" });
    }

    private ComboResult command_DIR(String[] parts) {
        // `*DIR,` with an explicit-empty trailing arg triggers 995 on the real
        // printer (`Receive format error! (NULL-pointer)`). We mirror that
        // rather than defaulting to `*.*`.
        if (parts.length == 2 && parts[1].isEmpty()) {
            return ok("<CR>995 Receive format error! (NULL-pointer)<CR>CMD->");
        }

        // Parse args: any element may be the filespec (bare filename / wildcard
        // / path-prefixed) or the "SDT" flag (enable size/date/time columns).
        String spec = "*.*";
        boolean showDetails = false;
        for (int i = 1; i < parts.length; i++) {
            if ("SDT".equalsIgnoreCase(parts[i])) {
                showDetails = true;
            } else if (!parts[i].isEmpty()) {
                spec = parts[i];
            }
        }

        String subfolder = "c0";
        String wildcard  = spec;
        if (spec.startsWith("/")) {
            int secondSlash = spec.indexOf('/', 1);
            if (secondSlash >= 0) {
                subfolder = spec.substring(1, secondSlash);
                wildcard  = spec.substring(secondSlash + 1);
            } else {
                subfolder = spec.substring(1);
                wildcard  = "*.*";
            }
            if (wildcard.isEmpty()) wildcard = "*.*";
        }

        ComboResult result = new ComboResult();
        StringBuilder body = new StringBuilder();

        File dir = new File(System.getProperty("user.dir") + File.separator + "virtual_disk" + File.separator + subfolder);
        FileFilter fileFilter = WildcardFileFilter.builder()
                .setWildcards(wildcard).setIoCase(IOCase.INSENSITIVE).get();
        File[] files = dir.listFiles(fileFilter);
        if (files != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
            SimpleDateFormat sdt = new SimpleDateFormat("HHmmss");
            for (File f : files) {
                body.append(f.getName());
                if (showDetails) {
                    body.append(",").append(f.length())
                        .append(",").append(sdf.format(f.lastModified()))
                        .append(",").append(sdt.format(f.lastModified()));
                }
                body.append("<CR>");
            }
        }
        body.append("<CR>0 No error(s).<CR>CMD->");
        result.setResponse(body.toString());
        return result;
    }

    private ComboResult command_DELETE(String[] parts) {
        ComboResult result = new ComboResult();
        String param = parts[1];
        String fullPath;

        if (param.startsWith("/")) {
            int secondSlash = param.indexOf('/', 1);
            String subfolder, filename;
            if (secondSlash >= 0) {
                subfolder = param.substring(1, secondSlash);
                filename  = param.substring(secondSlash + 1);
            } else {
                subfolder = param.substring(1);
                filename  = "";
            }
            fullPath = System.getProperty("user.dir") + File.separator + "virtual_disk"
                       + File.separator + subfolder + File.separator + filename;
        } else {
            fullPath = utils.getFullPath(param);
        }

        File todelete = new File(fullPath);
        if (!todelete.delete()) {
            result.setResponse("<CR>992 File access error!<CR>CMD->");
            result.setResult(false);
        } else {
            result.setResponse("<CR>0 No Error(s).<CR>CMD->");
        }
        return result;
    }

    private ComboResult command_LAD(String[] parts) {
        ComboResult result = new ComboResult();
        try {
            java.util.List<String> lines = Files.readAllLines(
                    Paths.get(utils.getFullPath(parts[1])), StandardCharsets.ISO_8859_1);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append("<CR>");
            }
            result.setResponse(sb.toString());
        } catch (IOException e) {
            result.setResponse("<CR>63 Couldn't open file " + parts[1] + " !<CR>CMD->");
            result.setResult(false);
        }
        return result;
    }

    private ComboResult command_HEXFILE(String[] parts) {
        String filename  = parts[1];
        String subfolder = utils.getSubfolderForFile(filename);
        String fullPath  = System.getProperty("user.dir") + File.separator + "virtual_disk"
                           + File.separator + subfolder + File.separator + filename;
        FileUtils.deleteQuietly(new File(fullPath));
        intelHex.init(fullPath);
        // Per VCOMSVR spec, the labeller replies 000<ACK> to the *HEXFILE
        // command before the host sends the first Intel HEX record.
        return ok("0000<ACK>");
    }

    private ComboResult command_TYPEHEX(String[] parts) {
        String fullPath = System.getProperty("user.dir") + File.separator + "virtual_disk"
                          + File.separator + "c0" + File.separator + parts[1];
        typeHex.prepare(fullPath);
        return new ComboResult();
    }

    private ComboResult command_HEXFILEDATA(String[] parts) {
        intelHex.parseLine(parts[1]);
        // Each Intel HEX record (including the :0000000000 End Block) is
        // acknowledged with 000<ACK> before the host sends the next record.
        return ok("0000<ACK>");
    }

    // -----------------------------------------------------------------------
    // Dispatch
    // -----------------------------------------------------------------------

    private ComboResult execute(String commandLine) {
        commandLine = utils.decodeControlChars(commandLine);
        commandLine = commandLine.replace("<CR>", "").replace("<LF>", "")
                                 .replace("<STX>", "").replace("<ETX>", "");

        if (commandLine.isEmpty()) return new ComboResult();

        callback.appendLog("~~ " + commandLine + "\n", ServerMemory.color_dark_green);

        String[] parts = utils.parseParam(commandLine);

        if (parts[0].startsWith(":") || parts[0].startsWith("<LF>:")) {
            parts = new String[]{ "*HEXFILEDATA", parts[0] };
        }

        return switch (parts[0]) {
            case "P"            -> command_P();
            case "?online"      -> command_ONLINE();
            case "?printertype" -> command_PRINTERTYPE();
            case "?customer"    -> command_CUSTOMER();
            case "?ak"          -> command_AK();
            case "?dataready"   -> command_DATAREADY();
            case "?line"        -> command_LINE();
            case "?cpuboard"    -> command_CPUBOARD();
            case "?serialnumber"-> command_SERIALNUMBER();
            case "?versions"    -> command_VERSIONS();
            case "?alloclist"   -> command_ALLOCLIST();
            case "?dumpsizes"   -> command_DUMPSIZES();
            case "?errors"      -> command_ERRORS();
            case "?errtask"     -> command_ERRTASK(parts);
            case "?lamaqueue"   -> command_LAMAQUEUE();
            case "?language"    -> command_LANGUAGE();
            case "?meminfo"     -> command_MEMINFO();
            case "?stops"       -> command_STOPS();
            case "?sysinfo"     -> command_SYSINFO();
            case "?tasks"       -> command_TASKS();
            case "?untranslated"-> command_UNTRANSLATED();
            case "?unusedmem"   -> command_UNUSEDMEM();
            case "?servicecounter" -> command_SERVICECOUNTER();
            case "?servicedone"    -> command_SERVICEDONE();
            case "?servicestatus"  -> command_SERVICESTATUS();
            case "?timestamps"     -> command_TIMESTAMPS();
            case "?tronget"        -> command_TRONGET();
            case "*FILES"       -> command_FILES();
            case "*DIR"         -> command_DIR(parts);
            case "*DELETE"      -> command_DELETE(parts);
            case "&CHECK"       -> command_CHECK(parts);
            case "&GETSTAT"     -> command_GETSTAT(parts);
            case "&TEST"        -> command_TEST(parts);
            case "&SET"         -> command_SET(parts);
            case "&RESET"       -> command_RESET(parts);
            case "&UNCHECK"     -> command_UNCHECK(parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
            case "&RESTABLE"    -> command_RESTABLE();
            case "&SYMBOLTABLE" -> command_SYMBOLTABLE(parts);
            case "&LOGICSETUP"  -> command_LOGICSETUP(parts);
            case "&LOGOSTEPSTATE" -> command_LOGOSTEPSTATE();
            case "&REPORT"      -> command_REPORT(parts);
            case "&NOREPORT"    -> command_NOREPORT(parts);
            case "&LABELLERTYPE"  -> command_LABELLERTYPE();
            case "&TIMESSETUP"    -> command_TIMESSETUP(parts);
            case "&NODELIST"      -> command_NODELIST();
            case "&TIMESTAMPLIST" -> command_TIMESTAMPLIST();
            case "&INFOLINES"     -> command_INFOLINES();
            case "&MESSAGELINES"  -> command_MESSAGELINES();
            case "&ERRORLINES"    -> command_ERRORLINES();
            case "&LISTTABLE"     -> command_LISTTABLE();
            case "&CANRCLIST"     -> command_CANRCLIST();
            case "&NODESYMBOLS"   -> command_NODESYMBOLS();
            case "&NOTIMESTAMPS"  -> command_NOTIMESTAMPS();
            case "&OK"            -> command_OK();
            case "&TIMESTAMP"     -> command_TIMESTAMP(parts);
            case "&UNTIMESTAMP"   -> command_UNTIMESTAMP(parts);
            case "FD"           -> command_FD(parts);
            case "LOAD"         -> command_LOAD(parts);
            case "LK"           -> command_LK(parts);
            case "*HEXFILE",
                 "<LF>*HEXFILE" -> command_HEXFILE(parts);
            case "*TYPEHEX"     -> command_TYPEHEX(parts);
            case "*HEXFILEDATA" -> command_HEXFILEDATA(parts);
            case "*LAD"         -> command_LAD(parts);
            case "*RDR"         -> command_RDR();
            case "*SDR"         -> command_SDR();
            case "*VERBOSE",
                 "*ACKNAK"      -> command_VERBOSE();
            case "*VERSION"     -> command_VERSION();
            case "*LOGINFO"     -> command_LOGINFO();
            case "*TRXLOG"      -> command_TRXLOG();
            case "*DELLOG"      -> command_DELLOG();
            case "*GETTIME"     -> command_GETTIME();
            case "*SETTIME"     -> command_SETTIME(parts);
            case "*MEM"         -> command_MEM();
            case "*FONTS"       -> command_FONTS();
            case "*YMODEMERROR" -> command_YMODEMERROR();
            case "*GETCOUNT"    -> command_GETCOUNT(parts);
            case "*SETCOUNT"    -> command_SETCOUNT(parts);
            default             -> command_UNKNOWN(parts[0]);
        };
    }

    public ComboResult process(LinkedList<String> data) {
        ComboResult result = new ComboResult();
        String error = "";
        StringBuilder combined = new StringBuilder();

        for (String cmd : data) {
            ComboResult r = execute(cmd);
            if (!r.getResult() && error.isEmpty()) {
                error = r.getResponse();
            }
            combined.append(utils.encodeControlChars(r.getResponse()));
        }

        result.setResponse(combined.toString());
        if (!error.isEmpty()) {
            result.setResult(false);
            result.setResponse(error);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ComboResult ok(String response) {
        ComboResult r = new ComboResult();
        r.setResponse(response);
        return r;
    }
}
