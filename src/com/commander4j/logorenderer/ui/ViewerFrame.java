package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.commander4j.dialog.JDialogAbout;
import com.commander4j.dialog.JDialogLicenses;
import com.commander4j.gui.JButton4j;
import com.commander4j.gui.JComboBox4j;
import com.commander4j.gui.JLabel4j_std;
import com.commander4j.gui.JMenuItem4j;
import com.commander4j.gui.JToggleButton4j;
import com.commander4j.logorenderer.ChainWalker;
import com.commander4j.logorenderer.ComboServer;
import com.commander4j.logorenderer.FontMapper;
import com.commander4j.logorenderer.LabelField;
import com.commander4j.logorenderer.LlfLayout;
import com.commander4j.logorenderer.LlfParser;
import com.commander4j.logorenderer.PrintCycle;
import com.commander4j.logorenderer.ServerCallback;
import com.commander4j.logorenderer.ServerMemory;
import com.commander4j.logorenderer.UvarEvaluator;
import com.commander4j.sys.Common;
import com.commander4j.util.JHelp;
import com.commander4j.util.JUtility;

/**
 * Main application window for the Logo Label Viewer + TCP server (LogoRenderer).
 */
public class ViewerFrame extends JFrame implements ServerCallback
{

    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Label viewer state
    // -----------------------------------------------------------------------

    private final LabelCanvas       canvas    = new LabelCanvas();
    private final JLabel4j_std      statusBar = new JLabel4j_std(" ");
    private final LlfLinePanel      linePanel = new LlfLinePanel(new com.commander4j.logorenderer.FontMapper());
    private final LogPanel          logPanel  = new LogPanel();
    private QuePanel                quePanel;
    private JTabbedPane             rightTabs;
    private final JToggleButton4j   flipButton = new JToggleButton4j(Common.icon_rotate_down);

    private volatile File      currentFile   = null;
    private volatile LlfLayout currentLayout = null;

    private static int widthadjustment  = 0;
    private static int heightadjustment = 0;

    // -----------------------------------------------------------------------
    // Server state
    // -----------------------------------------------------------------------

    private ComboServer  server         = null;
    private JComboBox4j<String> ipCombo;
    private JComboBox4j<String> portCombo;
    private JToggleButton4j serverButton;

    // -----------------------------------------------------------------------
    // Pallet simulation controls (in top toolbar, enabled when field 19500 present)
    // -----------------------------------------------------------------------

    private JButton4j     btnPrint;
    private JLabel4j_std  lblSSCCLabel;
    private JLabel4j_std  lblSSCCValue;
    private JLabel4j_std  lblBuffersLabel;
    private JLabel4j_std  lblBufferCount;

    // (Field table removed — field editing is now handled by the source-panel
    // property grid.  ServerMemory.Fields is still populated for socket/server
    // communication but no longer has a dedicated UI tab.)

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ViewerFrame()
    {
        super(Common.buildTitle(null));
        JUtility.setLookAndFeel("Nimbus");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e) { confirmExit(); }
        });
        setSize(1600, 900);
        setLocationRelativeTo(null);

        linePanel.setRefreshListener(this::refreshFromLines);
        linePanel.setFontSaveCallback(this::refreshCanvas);
        linePanel.setFontMappingsCallback(this::openFontMappings);
        canvas.setLogger(logPanel);
        canvas.setElementClickListener(this::onElementClicked);

        // Right side: tabbed pane with Source, Fields, QUE Input, Log
        quePanel = new QuePanel((id, value) -> {
            logPanel.info("QUE Input: field=" + id + " value=" + value);
            onFieldCommand(id, value);
        });
        rightTabs = new JTabbedPane(JTabbedPane.TOP);
        rightTabs.addTab("Source",    linePanel);
        rightTabs.addTab("QUE Input", quePanel);
        rightTabs.addTab("Log",       logPanel);
        rightTabs.setPreferredSize(new java.awt.Dimension(500, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(canvas), rightTabs);
        split.setResizeWeight(0.75);
        split.setOneTouchExpandable(true);

        setJMenuBar(buildMenuBar());
        getContentPane().add(buildTopToolBar(),  BorderLayout.NORTH);
        getContentPane().add(buildLeftToolBar(), BorderLayout.WEST);
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

        widthadjustment  = JUtility.getOSWidthAdjustment();
        heightadjustment = JUtility.getOSHeightAdjustment();

        GraphicsDevice gd = JUtility.getGraphicsDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();

        setBounds(
            screenBounds.x + ((screenBounds.width  - getWidth())  / 2),
            screenBounds.y + ((screenBounds.height - getHeight()) / 2),
            getWidth()  + widthadjustment,
            getHeight() + heightadjustment);

        logPanel.info(Common.programName + " " + Common.version);
        for (String line : Common.disclaimer.split("\n"))
        {
            logPanel.info(line);
        }

        loadSscc();
        setVisible(true);
    }

    // -----------------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------------

    // buildFieldPanel() removed — field editing via source-panel property grid

    private JMenuBar buildMenuBar()
    {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("Open...",       "ctrl O",       this::openFile));
        file.add(menuItem("Reload",        "F5",           this::reloadFile));
        file.addSeparator();
        file.add(menuItem("Save LLF",      "ctrl shift S", this::saveLlf));
        file.add(menuItem("Save LLF As...", null,          this::saveLlfAs));
        file.addSeparator();
        file.add(menuItem("Save PNG...",   "ctrl P",       this::savePng));
        file.addSeparator();
        file.add(menuItem("Exit",          "ctrl Q",       _ -> confirmExit()));

        JMenu view = new JMenu("View");
        view.add(menuItem("Zoom In",   "ctrl EQUALS", _ -> adjustZoom(+0.25f)));
        view.add(menuItem("Zoom Out",  "ctrl MINUS",  _ -> adjustZoom(-0.25f)));
        view.addSeparator();
        view.add(menuItem("Zoom 25%",  null, _ -> setZoom(0.25f)));
        view.add(menuItem("Zoom 50%",  null, _ -> setZoom(0.50f)));
        view.add(menuItem("Zoom 100%", "ctrl 1", _ -> setZoom(1.00f)));
        view.add(menuItem("Zoom 150%", null, _ -> setZoom(1.50f)));
        view.add(menuItem("Zoom 200%", "ctrl 2", _ -> setZoom(2.00f)));
        view.add(menuItem("Fit Window","ctrl F",  this::fitToWindow));
        view.addSeparator();
        JCheckBoxMenuItem flipMenuItem = new JCheckBoxMenuItem("Rotate 180°");
        flipMenuItem.setAccelerator(KeyStroke.getKeyStroke("ctrl R"));
        flipMenuItem.addActionListener(_ -> {
            canvas.setFlipped(flipMenuItem.isSelected());
            flipButton.setSelected(flipMenuItem.isSelected());
            flipButton.setIcon(flipMenuItem.isSelected() ? Common.icon_rotate_up : Common.icon_rotate_down);
        });
        view.add(flipMenuItem);
        flipButton.addActionListener(_ -> {
            canvas.setFlipped(flipButton.isSelected());
            flipMenuItem.setSelected(flipButton.isSelected());
            flipButton.setIcon(flipButton.isSelected() ? Common.icon_rotate_up : Common.icon_rotate_down);
        });

        JMenu settings = new JMenu("Settings");
        settings.add(menuItem("Font Mappings...", null, _ -> openFontMappings()));

        mb.add(file);
        mb.add(view);
        mb.add(settings);
        return mb;
    }

    private JToolBar buildTopToolBar()
    {
        JToolBar tb = new JToolBar(JToolBar.HORIZONTAL);
        tb.setFloatable(false);

        // ---- TCP server controls ----

        tb.add(new JLabel4j_std("  IP Address: "));

        Vector<String> ips = JUtility.getHostIPAddresses();
        if (!ips.contains("127.0.0.1")) ips.insertElementAt("127.0.0.1", 0);
        ipCombo = new JComboBox4j<>(ips.toArray(new String[0]));
        ipCombo.setSelectedItem("127.0.0.1");
        ipCombo.setToolTipText("Bind address");
        ipCombo.setPreferredSize(new Dimension(140, 28));
        ipCombo.setMaximumSize(new Dimension(140, 28));
        tb.add(ipCombo);

        tb.add(new JLabel4j_std("  Port: "));

        portCombo = new JComboBox4j<>(new String[] { "8000", "8100", "8200", "8300" });
        portCombo.setEditable(true);
        portCombo.setFocusable(true);
        portCombo.getEditor().getEditorComponent().setFocusable(true);
        portCombo.setSelectedItem("8000");
        portCombo.setToolTipText("TCP port number (editable)");
        portCombo.setPreferredSize(new Dimension(75, 28));
        portCombo.setMaximumSize(new Dimension(75, 28));
        tb.add(portCombo);

        tb.addSeparator();

        serverButton = new JToggleButton4j(Common.icon_disconnected);
        serverButton.setToolTipText("Start Server");
        serverButton.setPreferredSize(new Dimension(36, 36));
        serverButton.setMaximumSize(new Dimension(36, 36));
        serverButton.addActionListener(_ -> toggleServer());
        tb.add(serverButton);

        // ---- Pallet simulation controls (enabled only when field 19500 present) ----
        tb.addSeparator();

        btnPrint = new JButton4j("Print Label");
        btnPrint.setToolTipText("Simulate a pallet label print (2 sides, same SSCC)");
        btnPrint.addActionListener(_ -> printLabel());
        tb.add(btnPrint);

        lblSSCCLabel = new JLabel4j_std("  SSCC: ");
        tb.add(lblSSCCLabel);

        lblSSCCValue = new JLabel4j_std("000000000000000001");
        lblSSCCValue.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        lblSSCCValue.setForeground(Color.BLUE);
        lblSSCCValue.setToolTipText("Next SSCC-18 to be assigned");
        tb.add(lblSSCCValue);

        lblBuffersLabel = new JLabel4j_std("  Buffers: ");
        tb.add(lblBuffersLabel);

        lblBufferCount = new JLabel4j_std("0");
        lblBufferCount.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        lblBufferCount.setForeground(Color.RED);
        lblBufferCount.setToolTipText("Number of pending log buffers");
        tb.add(lblBufferCount);

        setPalletControlsEnabled(false);

        return tb;
    }

    private JToolBar buildLeftToolBar()
    {
        JToolBar tb = new JToolBar(JToolBar.VERTICAL);
        tb.setFloatable(false);

        // ---- File actions (moved from top toolbar) ----
        tb.add(iconButton(Common.icon_open,       "Open...",        this::openFile));
        tb.add(iconButton(Common.icon_reload,     "Reload (F5)",    this::reloadFile));
        tb.add(iconButton(Common.icon_save,       "Save LLF",       this::saveLlf));
        tb.add(iconButton(Common.icon_save_as,    "Save LLF As...", this::saveLlfAs));
        tb.add(iconButton(Common.icon_export_png, "Export PNG...",  this::savePng));
        tb.add(iconButton(Common.icon_eraser,     "Clear / Reset",  this::clearWorkspace));

        tb.add(iconButton(Common.icon_zoom_in,  "Zoom In",       _ -> adjustZoom(+0.25f)));
        tb.add(iconButton(Common.icon_zoom_out, "Zoom Out",      _ -> adjustZoom(-0.25f)));
        tb.add(iconButton(Common.icon_zoom_fit, "Fit to Window", this::fitToWindow));

        flipButton.setToolTipText("Rotate 180°");
        flipButton.setPreferredSize(new Dimension(36, 36));
        flipButton.setMaximumSize(new Dimension(36, 36));
        tb.add(flipButton);
        tb.add(iconButton(Common.icon_about,   "About",   _ -> new JDialogAbout().setVisible(true)));
        tb.add(iconButton(Common.icon_license, "Licences", _ -> new JDialogLicenses(ViewerFrame.this).setVisible(true)));

        JButton4j btnHelp = new JButton4j(Common.icon_help);
        btnHelp.setToolTipText("Help");
        btnHelp.setPreferredSize(new Dimension(36, 36));
        btnHelp.setMaximumSize(new Dimension(36, 36));
        new JHelp().enableHelpOnButton(btnHelp, Common.helpURL);
        tb.add(btnHelp);
        tb.add(iconButton(Common.icon_exit, "Exit", _ -> confirmExit()));

        return tb;
    }

    private JPanel buildStatusBar()
    {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        p.add(statusBar, BorderLayout.WEST);
        return p;
    }

    // -----------------------------------------------------------------------
    // Server management
    // -----------------------------------------------------------------------

    private void toggleServer()
    {
        if (server == null || !server.isAlive()) {
            startServer();
        } else {
            stopServer();
        }
    }

    private void startServer()
    {
        String ip = (String) ipCombo.getSelectedItem();
        int port;
        try {
            Object sel = portCombo.getSelectedItem();
            port = Integer.parseInt(sel == null ? "" : sel.toString().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        server = new ComboServer(ip, port, this);
        server.setDaemon(true);
        ServerMemory.activeServer = server;
        ServerMemory.reportingEnabled = false;
        server.start();
        serverButton.setIcon(Common.icon_connected);
        serverButton.setToolTipText("Stop Server");
        ipCombo.setEnabled(false);
        portCombo.setEnabled(false);
    }

    private void stopServer()
    {
        if (server != null) {
            server.shutdown();
            server = null;
        }
        ServerMemory.activeServer = null;
        ServerMemory.reportingEnabled = false;
        serverButton.setIcon(Common.icon_disconnected);
        serverButton.setToolTipText("Start Server");
        serverButton.setSelected(false);
        ipCombo.setEnabled(true);
        portCombo.setEnabled(true);
    }

    // -----------------------------------------------------------------------
    // Pallet simulation
    // -----------------------------------------------------------------------

    private void printLabel()
    {
        synchronized (ServerMemory.logBuffers) {
            if (ServerMemory.logBuffers.size() >= 2) {
                appendLog("PRINT: cannot add buffer — 2 buffers already pending.\n", Color.RED);
                return;
            }
        }

        long sscc = ServerMemory.ssccCounter.get();
        String ssccStr = String.format("%018d", sscc);

        // Seed the SSCC into field 19500 so a VAR referencing %19500F picks
        // it up during evaluation.
        if (currentLayout != null) currentLayout.setField(19500, ssccStr);

        // Resolve field 19500 the way a real labeller does:
        //   FL,<var>,19500  — run VAR formula against current field values
        //   no FL link      — use the seed value (SSCC) directly
        // resolveAll() cascades nested VARs so %NF tokens that reference other
        // VAR outputs (not just FDs) resolve correctly.
        String logEntry = ssccStr;
        if (currentLayout != null) {
            UvarEvaluator evaluator = new UvarEvaluator();
            evaluator.resolveAll(currentLayout);
            int varId = currentLayout.getSourceForElement(19500);
            if (varId >= 0 && currentLayout.getUvarFormula(varId) != null) {
                String resolved = currentLayout.getField(varId);
                if (resolved != null && !resolved.isEmpty()) {
                    logEntry = resolved;
                    currentLayout.setField(19500, resolved);
                }
            }
        }

        // Mirror the resolved value into ServerMemory so the Fields tab and
        // any onFieldCommand consumers see what was actually logged.
        if (ServerMemory.Fields.containsKey("19500")) {
            ServerMemory.Fields.get("19500").setFieldValue(logEntry);
            ServerMemory.Fields.get("19500").setFieldUpdateSource("print");
            refreshFieldTable();
        }

        // One log entry per label applied — 2 labels per pallet, same content,
        // same SSCC. Matches what *TRXLOG returns on a real PL3.
        List<String> buffer = new ArrayList<>();
        buffer.add(logEntry);
        buffer.add(logEntry);

        synchronized (ServerMemory.logBuffers) {
            ServerMemory.logBuffers.add(buffer);
        }

        long nextSscc = ServerMemory.ssccCounter.incrementAndGet();
        lblSSCCValue.setText(String.format("%018d", nextSscc));
        updateBufferCount();

        saveSscc();
        appendLog("PRINT: pallet " + ssccStr + " — 2 labels queued. Log=[" + logEntry + "]\n",
                ServerMemory.color_dark_green);

        PrintCycle.run();
    }

    private static String getSsccFilePath()
    {
        return System.getProperty("user.dir") + File.separator + "sscc.dat";
    }

    private void loadSscc()
    {
        try {
            File f = new File(getSsccFilePath());
            if (f.exists()) {
                String s = new String(Files.readAllBytes(Paths.get(getSsccFilePath())),
                        StandardCharsets.US_ASCII).trim();
                ServerMemory.ssccCounter.set(Long.parseLong(s));
            }
        } catch (Exception ignored) {}
        if (lblSSCCValue != null)
            lblSSCCValue.setText(String.format("%018d", ServerMemory.ssccCounter.get()));
    }

    private void saveSscc()
    {
        try {
            Files.write(Paths.get(getSsccFilePath()),
                    String.valueOf(ServerMemory.ssccCounter.get()).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            appendLog("SSCC save error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    // -----------------------------------------------------------------------
    // ServerCallback implementation
    // -----------------------------------------------------------------------

    @Override
    public void appendLog(String msg, Color c)
    {
        logPanel.appendColoured(msg, c);
    }

    @Override
    public void onLoadCommand(String filename)
    {
        SwingUtilities.invokeLater(() -> {
            logPanel.clear();
            logPanel.info("Server LOAD: " + filename);
        });
        loadMasterLayout(filename);
        File file = resolveServerFile(filename);
        if (file != null && file.exists()) {
            currentFile = file;
            SwingUtilities.invokeLater(() -> {
                setTitle(Common.buildTitle(file.getName()));
                updateStatus();
                setPalletControlsEnabled(layoutHasLogField());
                SwingUtilities.invokeLater(this::doFitToWindow);
            });
        }
    }

    @Override
    public void onFieldCommand(String fieldId, String value)
    {
        // Update field storage and layout synchronously on the server thread.
        // command_FD already verified the field exists, so containsKey is always true here.
        if (ServerMemory.Fields.containsKey(fieldId)) {
            LabelField lf = ServerMemory.Fields.get(fieldId);
            lf.setFieldValue(value);
            lf.setFieldUpdateSource("direct");
        }
        if (currentLayout != null) {
            try {
                currentLayout.setField(Integer.parseInt(fieldId), value);
            } catch (NumberFormatException ignored) {}
        }
        // Canvas/table refresh must happen on the EDT
        SwingUtilities.invokeLater(() -> {
            if (currentLayout != null) refreshCanvas();
            refreshFieldTable();
        });
    }

    @Override
    public void populateFieldTable()
    {
        SwingUtilities.invokeLater(this::refreshFieldTable);
    }

    @Override
    public void clearFieldTable()
    {
        // Clear synchronously so subsequent LOAD/FD commands see an empty map
        ServerMemory.Fields.clear();
        SwingUtilities.invokeLater(this::refreshFieldTable);
    }

    @Override
    public void setServerRunning(boolean running)
    {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                serverButton.setIcon(Common.icon_connected);
                serverButton.setToolTipText("Stop Server");
                serverButton.setSelected(true);
                ipCombo.setEnabled(false);
                portCombo.setEnabled(false);
            } else {
                serverButton.setIcon(Common.icon_disconnected);
                serverButton.setToolTipText("Start Server");
                serverButton.setSelected(false);
                ipCombo.setEnabled(true);
                portCombo.setEnabled(true);
                server = null;
            }
        });
    }

    @Override
    public void updateBufferCount()
    {
        SwingUtilities.invokeLater(() -> {
            int count;
            synchronized (ServerMemory.logBuffers) { count = ServerMemory.logBuffers.size(); }
            lblBufferCount.setText(String.valueOf(count));
        });
    }

    // -----------------------------------------------------------------------
    // Pallet control enable/disable
    // -----------------------------------------------------------------------

    /**
     * Checks whether the current layout defines field 19500 (the log data
     * field required for pallet label printing on the real labeller).
     */
    private boolean layoutHasLogField()
    {
        if (currentLayout == null) return false;
        return currentLayout.getElementById(19500) != null
            || currentLayout.getFields().containsKey(19500);
    }

    private void setPalletControlsEnabled(boolean enabled)
    {
        btnPrint.setEnabled(enabled);
        lblSSCCLabel.setEnabled(enabled);
        lblSSCCValue.setEnabled(enabled);
        lblBuffersLabel.setEnabled(enabled);
        lblBufferCount.setEnabled(enabled);
    }

    /** Rebuild the QUE Input panel from the current layout. */
    private void refreshQuePanel()
    {
        if (quePanel != null) quePanel.loadPrompts(currentLayout);
    }

    /** Switch the right tab pane to the QUE Input tab and focus first input. */
    private void selectQueTab()
    {
        if (rightTabs == null || quePanel == null) return;
        int idx = rightTabs.indexOfComponent(quePanel);
        if (idx >= 0) rightTabs.setSelectedIndex(idx);
        quePanel.focusFirstInput();
    }

    // -----------------------------------------------------------------------
    // Field table helpers
    // -----------------------------------------------------------------------

    private void populateFieldsFromLayout()
    {
        ServerMemory.Fields.clear();
        if (currentLayout != null) {
            for (Map.Entry<Integer, String> entry : currentLayout.getFields().entrySet()) {
                LabelField lf = new LabelField();
                lf.setFieldID(String.valueOf(entry.getKey()));
                lf.setFieldType("FD");
                lf.setFieldValue(entry.getValue());
                lf.setFieldUpdateSource("LOAD");
                ServerMemory.Fields.put(String.valueOf(entry.getKey()), lf);
            }
        }
        refreshFieldTable();
    }

    private void refreshFieldTable()
    {
        // No-op — field table UI removed; ServerMemory.Fields still maintained
    }

    private File resolveServerFile(String filename)
    {
        // Strip /c0/ prefix if present
        filename = filename.replace("/c0/", "").replace("\\c0\\", "");
        // Try virtual_disk/c0/ under working directory
        File f = new File(System.getProperty("user.dir")
                + File.separator + "virtual_disk"
                + File.separator + "c0"
                + File.separator + filename);
        if (f.exists()) return f;
        // Try directory of currently open file
        if (currentFile != null) {
            f = new File(currentFile.getParentFile(), filename);
            if (f.exists()) return f;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // File / canvas actions
    // -----------------------------------------------------------------------

    /**
     * Ensure {@code fonts.xml} has an entry for every font referenced by the
     * just-loaded layout. Missing entries are added with metric-derived
     * ascent/descent factors so rendering works out of the box; users can
     * fine-tune against printed output via the font-mapping dialog.
     */
    private void autoAddMissingFonts(LlfLayout layout)
    {
        if (layout == null) return;
        java.util.Set<String> used = layout.getUsedFontNames();
        if (used.isEmpty()) return;
        FontMapper fm = new FontMapper();
        java.util.Set<String> added = fm.addMissingFonts(used);
        if (added.isEmpty()) return;
        logPanel.info("Auto-added " + added.size() + " font mapping(s) to fonts.xml: "
                + String.join(", ", new java.util.TreeSet<>(added))
                + " — calibrate Asc/Desc against printed output");
    }

    private void openFontMappings()
    {
        java.util.Set<String> usedFonts = currentLayout != null
                ? currentLayout.getUsedFontNames()
                : java.util.Collections.emptySet();
        String selectedFont = linePanel.getSelectedFontName();
        JDialogFontMapping dlg = new JDialogFontMapping(
                this, new FontMapper(), usedFonts, this::refreshCanvas, selectedFont);
        dlg.setLogger(logPanel);
        dlg.setVisible(true);
    }

    private void refreshCanvas()
    {
        if (currentLayout != null && currentFile != null)
            canvas.setLayout(currentLayout, currentFile.toPath().getParent());
    }

    /**
     * Canvas click handler: takes the drawing-element id reported by the hit
     * test, walks the FL / VAR / FD chain, and asks the source panel to tint
     * every related row. A negative id (click missed any element) clears the
     * highlight.
     */
    private void onElementClicked(int elementId)
    {
        if (elementId < 0 || currentLayout == null) {
            linePanel.highlightChain(Collections.emptySet());
            return;
        }
        List<String> lines = linePanel.getAllLines();
        Set<Integer> indices = ChainWalker.indicesFor(elementId, currentLayout, lines);
        int elementRow = ChainWalker.elementRowIndex(elementId, lines);
        linePanel.highlightChain(indices, elementRow);
        selectSourceTab();
    }

    private void selectSourceTab()
    {
        for (int i = 0; i < rightTabs.getTabCount(); i++) {
            if ("Source".equals(rightTabs.getTitleAt(i))) {
                rightTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private void openFile(ActionEvent e)
    {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Logo label files (*.llf, *.lqf, *.ldf)", "llf", "lqf", "ldf"));
        fc.setAcceptAllFileFilterUsed(true);
        if (currentFile != null) {
            fc.setCurrentDirectory(currentFile.getParentFile());
        } else {
            File defaultDir = new File("virtual_disk/c0");
            if (defaultDir.isDirectory()) fc.setCurrentDirectory(defaultDir);
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            loadFile(fc.getSelectedFile());
    }

    private void reloadFile(ActionEvent e)
    {
        if (currentFile != null) loadFile(currentFile);
    }

    /**
     * Reset the workspace to the state it was in immediately after program
     * start: no file loaded, empty canvas, empty source/QUE/log panels, and
     * all in-memory field data cleared. Server connection and SSCC counter
     * are left alone.
     */
    private void clearWorkspace(ActionEvent e)
    {
        int choice = JOptionPane.showConfirmDialog(this,
                "Clear the loaded file and reset the workspace?\n"
                + "Any unsaved changes will be lost.",
                "Confirm Reset",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        resetWorkspaceState();
    }

    /**
     * Clear the workspace without any UI confirmation. Invoked by the eraser
     * toolbar button (via {@link #clearWorkspace}) and by the LEAP {@code LK}
     * command when parsing an LDF file (which kills the current layout and
     * all associated field data before a fresh LOAD).
     */
    private void resetWorkspaceState()
    {
        currentFile   = null;
        currentLayout = null;

        canvas.clear();
        canvas.setFlipped(false);
        canvas.setZoom(1.0f);
        flipButton.setSelected(false);
        flipButton.setIcon(Common.icon_rotate_down);

        linePanel.loadLines(new ArrayList<>());
        logPanel.clear();
        ServerMemory.Fields.clear();
        refreshQuePanel();
        setPalletControlsEnabled(false);

        setTitle(Common.buildTitle(null));
        statusBar.setText(" ");
    }

    private void savePng(ActionEvent e)
    {
        if (canvas.getLlfLayout() == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
        if (currentFile != null) {
            String name = currentFile.getName().replaceAll("\\.[^.]+$", "") + ".png";
            fc.setSelectedFile(new File(currentFile.getParentFile(), name));
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = fc.getSelectedFile();
            if (!out.getName().toLowerCase().endsWith(".png"))
                out = new File(out.getParentFile(), out.getName() + ".png");
            try {
                com.commander4j.logorenderer.LabelRenderer r = new com.commander4j.logorenderer.LabelRenderer();
                if (currentFile != null) r.setImageSearchPath(currentFile.toPath().getParent());
                r.setLogger(logPanel);
                java.awt.image.BufferedImage img = r.render(currentLayout);
                if (canvas.isFlipped()) img = flip180(img);
                ImageIO.write(img, "png", out);
                statusBar.setText("Saved: " + out.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveLlf(ActionEvent e)
    {
        if (currentFile == null) { saveLlfAs(e); return; }
        writeLlfLines(currentFile);
    }

    private void saveLlfAs(ActionEvent e)
    {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Logo label files (*.llf, *.lqf)", "llf", "lqf"));
        if (currentFile != null) fc.setSelectedFile(currentFile);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = fc.getSelectedFile();
            writeLlfLines(out);
            currentFile = out;
            setTitle(Common.buildTitle(out.getName()));
        }
    }

    private void writeLlfLines(File file)
    {
        try {
            Charset charset = currentFile != null
                    ? LlfParser.detectCharset(currentFile.toPath())
                    : StandardCharsets.ISO_8859_1;
            Files.write(file.toPath(), linePanel.getAllLines(), charset);
            statusBar.setText("Saved: " + file.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void openFromCommandLine(String path)
    {
        loadFile(new File(path));
    }

    private void loadFile(File file)
    {
        if (file.getName().toLowerCase().endsWith(".ldf")) {
            loadLdfFile(file);
            return;
        }
        try {
            Path llfPath = file.toPath();
            Charset charset = LlfParser.detectCharset(llfPath);
            List<String> rawLines = Files.readAllLines(llfPath, charset);
            logPanel.clear();
            logPanel.info("Loading: " + file.getName() + " (charset=" + charset.displayName() + ")");
            LlfParser parser = new LlfParser();
            parser.setLogger(logPanel);
            LlfLayout layout = parser.parse(rawLines);
            currentFile   = file;
            currentLayout = layout;
            autoAddMissingFonts(layout);
            canvas.setLayout(layout, llfPath.getParent());
            linePanel.loadLines(rawLines);
            setTitle(Common.buildTitle(file.getName()));
            updateStatus();
            populateFieldsFromLayout();
            setPalletControlsEnabled(layoutHasLogField());
            refreshQuePanel();
            if (file.getName().toLowerCase().endsWith(".lqf"))
                selectQueTab();
            SwingUtilities.invokeLater(this::doFitToWindow);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Load a .ldf (data file) by executing its LOAD and FD commands,
     * reusing the same logic as the socket server protocol but without
     * STX/ETX framing.  Runs entirely on the EDT (called from file chooser).
     */
    private void loadLdfFile(File ldfFile)
    {
        try {
            Path ldfPath = ldfFile.toPath();
            Charset charset = LlfParser.detectCharset(ldfPath);
            List<String> lines = Files.readAllLines(ldfPath, charset);
            logPanel.clear();
            logPanel.info("Loading LDF: " + ldfFile.getName() + " (charset=" + charset.displayName() + ", " + lines.size() + " lines)");

            // Set currentFile to the LDF so resolveServerFile can find the
            // master layout relative to the LDF's directory.
            currentFile = ldfFile;

            for (String line : lines)
            {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("LK,") || trimmed.startsWith("LK "))
                {
                    // LK,id — kill current layout & associated fields before a
                    // fresh LOAD. Uses the same reset as the eraser button.
                    logPanel.info("LDF LK: " + trimmed);
                    resetWorkspaceState();
                    currentFile = ldfFile;
                }
                else if (trimmed.startsWith("LOAD,") || trimmed.startsWith("LOAD "))
                {
                    String filename = trimmed.substring(5).trim();
                    filename = filename.replaceAll("[,\\s]+$", "");
                    logPanel.info("LDF LOAD: " + filename);
                    loadMasterLayout(filename);
                }
                else if (trimmed.startsWith("FD,") || trimmed.startsWith("FD "))
                {
                    String remainder = trimmed.substring(3).trim();
                    int commaPos = remainder.indexOf(',');
                    if (commaPos > 0)
                    {
                        String fieldId = remainder.substring(0, commaPos).trim();
                        String value   = remainder.substring(commaPos + 1);
                        logPanel.info("LDF FD: field=" + fieldId + " value=" + value);
                        onFieldCommand(fieldId, value);
                    }
                    else
                    {
                        logPanel.warn("LDF: malformed FD line: " + trimmed);
                    }
                }
                else
                {
                    logPanel.warn("LDF: unrecognised command: " + trimmed);
                }
            }

            // Keep currentFile pointing at the LDF
            currentFile = ldfFile;
            setTitle(Common.buildTitle(ldfFile.getName()));
            updateStatus();
            setPalletControlsEnabled(layoutHasLogField());
            refreshQuePanel();
            SwingUtilities.invokeLater(this::doFitToWindow);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load LDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Load a master layout file (called from LDF processing and from
     * onLoadCommand).  Parses the layout, populates fields & UI.
     * Safe to call from any thread — UI updates go through invokeLater
     * only when not already on the EDT.
     */
    private void loadMasterLayout(String filename)
    {
        File file = resolveServerFile(filename);
        if (file == null || !file.exists()) {
            logPanel.error("LOAD: file not found: " + filename);
            return;
        }
        try {
            Path llfPath = file.toPath();
            Charset charset = LlfParser.detectCharset(llfPath);
            List<String> rawLines = Files.readAllLines(llfPath, charset);
            logPanel.info("LOAD: " + file.getName() + " (charset=" + charset.displayName() + ")");

            LlfParser parser = new LlfParser();
            parser.setLogger(logPanel);
            LlfLayout layout = parser.parse(rawLines);

            currentLayout = layout;
            autoAddMissingFonts(layout);
            ServerMemory.Fields.clear();
            for (Map.Entry<Integer, String> entry : layout.getFields().entrySet()) {
                LabelField lf = new LabelField();
                lf.setFieldID(String.valueOf(entry.getKey()));
                lf.setFieldType("FD");
                lf.setFieldValue(entry.getValue());
                lf.setFieldUpdateSource("LOAD");
                ServerMemory.Fields.put(String.valueOf(entry.getKey()), lf);
            }
            logPanel.info("LOAD: " + file.getName() + " — " + ServerMemory.Fields.size() + " fields loaded");

            // Update UI — safe from EDT or server thread
            final List<String> finalLines  = rawLines;
            final File         finalFile   = file;
            final LlfLayout    finalLayout = layout;
            Runnable uiUpdate = () -> {
                canvas.setLayout(finalLayout, finalFile.toPath().getParent());
                linePanel.loadLines(finalLines);
                refreshFieldTable();
                refreshQuePanel();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                uiUpdate.run();
            } else {
                SwingUtilities.invokeLater(uiUpdate);
            }
        } catch (Exception e) {
            logPanel.error("LOAD: error parsing " + filename + ": " + e.getMessage());
        }
    }

    private void refreshFromLines(List<String> enabledLines)
    {
        if (currentFile == null) return;
        try {
            logPanel.clear();
            logPanel.info("Refresh from source panel (" + enabledLines.size() + " enabled lines)");

            // Snapshot the live field values (LDF overrides, socket-delivered
            // FD updates, QUE input) before re-parsing. The source text knows
            // only the LQF's FD defaults, so without this restore we'd wipe
            // every runtime override every time the user toggled a line.
            java.util.Map<Integer, String> runtimeFields = (currentLayout != null)
                    ? new java.util.LinkedHashMap<>(currentLayout.getFields())
                    : java.util.Collections.emptyMap();

            LlfParser refreshParser = new LlfParser();
            refreshParser.setLogger(logPanel);
            LlfLayout layout = refreshParser.parse(enabledLines);

            // Restore runtime overrides (LDF/socket/QUE) only for fields that
            // the source text did NOT define.  If the user edited an FD line in
            // the source panel the parser already has the new value and we must
            // not overwrite it with the stale snapshot.
            for (java.util.Map.Entry<Integer, String> e : runtimeFields.entrySet()) {
                if (!layout.getFields().containsKey(e.getKey())) {
                    layout.setField(e.getKey(), e.getValue());
                }
            }

            currentLayout = layout;
            autoAddMissingFonts(layout);
            canvas.setLayout(layout, currentFile.toPath().getParent());
            updateStatus();
            refreshQuePanel();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Refresh failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void adjustZoom(float delta) { setZoom(canvas.getZoom() + delta); }

    private void setZoom(float zoom)
    {
        canvas.setZoom(zoom);
        updateStatus();
    }

    private void fitToWindow(ActionEvent e) { doFitToWindow(); }

    private void doFitToWindow()
    {
        if (canvas.getLlfLayout() == null) return;
        JScrollPane sp = (JScrollPane) canvas.getParent().getParent();
        Dimension vp = sp.getViewport().getSize();
        int w = canvas.getLlfLayout().getLayoutWidth();
        int h = canvas.getLlfLayout().getLayoutHeight();
        if (w <= 0 || h <= 0) return;
        float zw = (vp.width - 60f) / w;
        float zh = (vp.height - 60f) / h;
        setZoom(Math.min(zw, zh));
    }

    private void updateStatus()
    {
        if (currentLayout == null) { statusBar.setText("No file loaded"); return; }
        int w = currentLayout.getLayoutWidth();
        int h = currentLayout.getLayoutHeight();
        // Non-square dot geometry: X = 11.8 dpmm (head), Y = 12 dpmm (paper feed).
        statusBar.setText(String.format(
                "%s   |   %d × %d dots  (%.1f × %.1f mm)   |   Zoom: %.0f%%   |   Fields: %d",
                currentFile != null ? currentFile.getName() : "",
                w, h, w / LlfParser.DOTS_PER_MM_X, h / LlfParser.DOTS_PER_MM_Y,
                canvas.getZoom() * 100,
                currentLayout.getElements().size()));
    }

    private void confirmExit()
    {
        int answer = JOptionPane.showConfirmDialog(this, "Exit application?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            stopServer();
            System.exit(0);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static java.awt.image.BufferedImage flip180(java.awt.image.BufferedImage src)
    {
        int w = src.getWidth(), h = src.getHeight();
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, src.getType());
        Graphics2D g = out.createGraphics();
        g.translate(w, h);
        g.rotate(Math.PI);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static JMenuItem menuItem(String label, String accel, ActionListener a)
    {
        JMenuItem4j item = new JMenuItem4j(label);
        if (accel != null) item.setAccelerator(KeyStroke.getKeyStroke(accel));
        item.addActionListener(a);
        return item;
    }

    private static JButton4j iconButton(ImageIcon icon, String tooltip, ActionListener a)
    {
        JButton4j btn = new JButton4j(icon);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setMaximumSize(new Dimension(36, 36));
        btn.addActionListener(a);
        return btn;
    }
}
