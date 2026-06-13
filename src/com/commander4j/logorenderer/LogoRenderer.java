package com.commander4j.logorenderer;

import javax.swing.*;

import com.commander4j.logorenderer.ui.ViewerFrame;

/**
 * Entry point for the Logo Label Viewer application.
 *
 * Usage: java -jar logorenderer-1.0.0.jar [file.llf]
 *
 * Build: mvn package
 *
 * Run during development: mvn exec:java [-Dexec.args="path/to/label.llf"]
 */
public class LogoRenderer
{

	public static void main(String[] args)
	{
		// Set Nimbus BEFORE any Swing component is created. On macOS, if the
		// default Aqua L&F is active when a JFrame is constructed, an
		// AquaRootPaneUI window listener is installed that is never removed —
		// it crashes with NPE (cellFocusRingColor is null) whenever a dialog
		// (e.g. JFileChooser) triggers window activate/deactivate events.
		com.commander4j.util.JUtility.setLookAndFeel("Nimbus");

		// Push STATE lines to a subscribed client whenever a SymbolTable bit changes.
		SymbolTable.setListener(entry -> {
			if (!ServerMemory.reportingEnabled) return;
			ComboServer s = ServerMemory.activeServer;
			if (s == null) return;
			s.send("STATE," + entry.name() + "=" + entry.value() + "<CR>");
		});

		SwingUtilities.invokeLater(() -> {
			ViewerFrame frame = new ViewerFrame();
			frame.setVisible(true);

			// If a file was passed on the command line, open it immediately
			if (args.length > 0)
			{
				frame.openFromCommandLine(args[0]);
			}
		});
	}
}
