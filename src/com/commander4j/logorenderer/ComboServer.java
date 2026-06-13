package com.commander4j.logorenderer;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

/**
 * TCP server thread that accepts a single client connection and processes
 * Logopak protocol commands. Independent compatibility implementation for
 * local testing — not a Logopak product; see README.txt.
 * Adapted from b6Logopak.Labeller_Server.
 */
public class ComboServer extends Thread {

    private DataInputStream  inp;
    private DataOutputStream outp;
    private String  currentCommand   = "";
    private boolean continuePolling  = true;
    private boolean continueIO       = true;
    private boolean shutdown         = false;

    private final int    port;
    private final String ip;
    private final ServerCallback   callback;
    private final ComboResponder   responder;
    private final ComboServerUtils utils = new ComboServerUtils();

    private ServerSocket serverSocket;
    private Socket       socket;
    private int          stx_etx_block_level = 0;

    public ComboServer(String ip, int port, ServerCallback callback) {
        this.ip       = ip;
        this.port     = port;
        this.callback = callback;
        this.responder = new ComboResponder(callback);
    }

    // -----------------------------------------------------------------------
    // Socket management
    // -----------------------------------------------------------------------

    private void socketClose() {
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
    }

    private void serverSocketClose() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
    }

    private void socketCloseAll() {
        socketClose();
        serverSocketClose();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void shutdown() {
        continuePolling = false;
        continueIO      = false;
        shutdown        = true;
        socketClose();
        serverSocketClose();
    }

    // -----------------------------------------------------------------------
    // Send
    // -----------------------------------------------------------------------

    public void send(String data) {
        if (shutdown || !continuePolling || !continueIO || data.isEmpty()) return;
        try {
            String[] lines = utils.decodeControlChars(data).split("<CR>");
            for (String line : lines) {
                String resp = utils.encodeControlChars(line + "<CR>");
                callback.appendLog(utils.decodeControlChars(resp), Color.BLUE);
                outp.writeBytes(resp);
                outp.flush();
                utils.pause(50);
            }
        } catch (EOFException e) {
            continueIO = false;
            continuePolling = true;
            socketCloseAll();
        } catch (IOException e) {
            continueIO = false;
            continuePolling = true;
            socketCloseAll();
        }
    }

    // -----------------------------------------------------------------------
    // Server loop
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        while (!shutdown) {
            try {
                InetAddress bindAddr = ip.equals("0.0.0.0") ? null : InetAddress.getByName(ip);
                serverSocket = new ServerSocket(port, 1, bindAddr);

                while (continuePolling && !shutdown) {
                    callback.appendLog("\nStartup.\n", ServerMemory.color_dark_green);
                    callback.appendLog("\nWaiting for connection on port " + port + "\n", ServerMemory.color_dark_green);

                    socket = serverSocket.accept();

                    callback.appendLog("\nClient connected " + socket.getRemoteSocketAddress()
                            + " on port " + socket.getLocalPort() + "\n", ServerMemory.color_dark_green);

                    if (!shutdown) {
                        currentCommand = "";
                        stx_etx_block_level = 0;
                        continueIO = true;
                        inp  = new DataInputStream(socket.getInputStream());
                        outp = new DataOutputStream(socket.getOutputStream());
                        LinkedList<String> commands = new LinkedList<>();

                        while (continueIO && !shutdown) {
                            String character = String.valueOf((char) inp.readByte());
                            String decoded   = utils.decodeControlChars(character);

                            switch (decoded) {
                                case "<STX>":
                                    callback.appendLog("<STX>", Color.RED);
                                    stx_etx_block_level++;
                                    break;
                                case "<ETX>":
                                    callback.appendLog("<ETX>", Color.RED);
                                    stx_etx_block_level--;
                                    break;
                                case "<CR>":
                                    callback.appendLog(utils.decodeControlChars(currentCommand) + "<CR>", Color.RED);
                                    commands.addLast(currentCommand);
                                    currentCommand = "";
                                    break;
                                default:
                                    currentCommand += character;
                                    break;
                            }

                            if (!commands.isEmpty() && stx_etx_block_level == 0) {
                                ComboResult reply = responder.process(commands);
                                commands.clear();
                                send(reply.getResponse());
                                if (responder.typeHex.isPending()) {
                                    responder.typeHex.transfer(inp, outp);
                                }
                                if (responder.leapLog.isPending()) {
                                    responder.leapLog.transfer(inp, outp);
                                }
                            }
                        }
                    } else {
                        continueIO      = false;
                        continuePolling = false;
                    }
                }

            } catch (EOFException e) {
                continueIO      = false;
                continuePolling = true;
                socketCloseAll();
            } catch (BindException e) {
                callback.appendLog(e.getMessage() + "\n", ServerMemory.color_dark_green);
                socketCloseAll();
                shutdown = true;
                callback.setServerRunning(false);
            } catch (Exception e) {
                continueIO      = false;
                continuePolling = true;
                socketCloseAll();
            }
        }
        callback.appendLog("\nShutdown completed.\n", ServerMemory.color_dark_green);
    }
}
