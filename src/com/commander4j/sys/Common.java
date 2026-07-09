package com.commander4j.sys;

import java.awt.Color;
import java.awt.Font;
import java.io.File;

import javax.swing.ImageIcon;

public class Common
{
    public static String iconPath = "." + File.separator + "images" + File.separator + "appIcons" + File.separator;

    // Application metadata
    public static final String programName = "LogoRenderer";
    public static final String version     = "2.05";
    public static final String helpURL     = "https://wiki.commander4j.com/index.php/LogoRenderer";

    // Shown in the About dialog (newlines collapse in its HTML rendering) and
    // logged line-by-line at startup.
    public static final String disclaimer =
          "LogoRenderer is an independent open-source tool and is not a Logopak product.\n"
        + "It is not affiliated with, endorsed by, or supported by Logopak.\n"
        + "Logopak, PowerLeap and related names are trademarks of their respective owners\n"
        + "and are used solely to describe interoperability.\n"
        + "Intended for local test and development use only — use entirely at your own risk.";

    /**
     * Build the window title.  If a filename is supplied it is appended in
     * square brackets; if null or empty, only the program name and version
     * are returned.
     */
    public static String buildTitle(String filename)
    {
        String base = programName + " " + version;
        if (filename == null || filename.isEmpty()) return base;
        return base + " [" + filename + "]";
    }

    // Look-and-feel adjustment fields (set by JUtility.adjustForLookandFeel)
    public static int LFAdjustWidth           = 0;
    public static int LFAdjustHeight          = 0;
    public static int LFTreeMenuAdjustWidth   = 0;
    public static int LFTreeMenuAdjustHeight  = 0;

    // Icons
    public final static ImageIcon icon_open          = new ImageIcon(iconPath + "open_file_24x24.png");
    public final static ImageIcon icon_reload        = new ImageIcon(iconPath + "refresh_24x24.png");
    public final static ImageIcon icon_save          = new ImageIcon(iconPath + "save_24x24.png");
    public final static ImageIcon icon_save_as       = new ImageIcon(iconPath + "save-as_24x24.png");
    public final static ImageIcon icon_export_png    = new ImageIcon(iconPath + "picture_24x24.png");
    public final static ImageIcon icon_eraser        = new ImageIcon(iconPath + "eraser_24x24.png");
    public final static ImageIcon icon_zoom_in       = new ImageIcon(iconPath + "zoom-in_24x24.png");
    public final static ImageIcon icon_zoom_out      = new ImageIcon(iconPath + "zoom-out_24x24.png");
    public final static ImageIcon icon_zoom_fit      = new ImageIcon(iconPath + "fit_size_24x24.png");
    public final static ImageIcon icon_rotate         = new ImageIcon(iconPath + "rotate_24x24.png");
    public final static ImageIcon icon_rotate_down   = new ImageIcon(iconPath + "rotate_down_24x24.png");
    public final static ImageIcon icon_rotate_up     = new ImageIcon(iconPath + "rotate_up_24x24.png");
    public final static ImageIcon icon_fonts         = new ImageIcon(iconPath + "font_24x24.png");
    public final static ImageIcon icon_ok            = new ImageIcon(iconPath + "ok_24x24.png");
    public final static ImageIcon icon_cancel        = new ImageIcon(iconPath + "cancel_24x24.png");
    public final static ImageIcon icon_font          = new ImageIcon(iconPath + "font_24x24.png");
    public final static ImageIcon icon_settings      = new ImageIcon(iconPath + "settings_24x24.png");
    public final static ImageIcon icon_exit          = new ImageIcon(iconPath + "exit_24x24.png");
    public final static ImageIcon icon_about         = new ImageIcon(iconPath + "about_24x24.png");
    public final static ImageIcon icon_help          = new ImageIcon(iconPath + "help_24x24.png");
    public final static ImageIcon icon_license       = new ImageIcon(iconPath + "open_source_24x24.png");
    public final static ImageIcon icon_select        = new ImageIcon(iconPath + "select_24x24.png");
    public final static ImageIcon icon_deselect      = new ImageIcon(iconPath + "deselect_24x24.png");
    public final static ImageIcon icon_insert_before = new ImageIcon(iconPath + "insert_before_24x24.png");
    public final static ImageIcon icon_insert_after  = new ImageIcon(iconPath + "insert_after_24x24.png");
    public final static ImageIcon icon_delete        = new ImageIcon(iconPath + "delete_24x24.png");
    public final static ImageIcon icon_edit          = new ImageIcon(iconPath + "edit_24x24.png");
    public final static ImageIcon icon_connected     = new ImageIcon(iconPath + "connected.png");
    public final static ImageIcon icon_disconnected  = new ImageIcon(iconPath + "disconnected.png");

    // Fonts
    public final static Font font_std        = new Font("Arial", Font.PLAIN, 11);
    public final static Font font_bold       = new Font("Arial", Font.BOLD, 11);
    public final static Font font_btn        = new Font("Arial", Font.PLAIN, 11);
    public final static Font font_input      = new Font("Arial", Font.PLAIN, 11);
    public final static Font font_combo      = new Font("Monospaced", Font.PLAIN, 11);
    public final static Font font_popup      = new Font("Arial", Font.PLAIN, 11);
    public final static Font font_list       = new Font("Monospaced", 0, 11);
    public final static Font font_menu       = new Font("Arial", Font.PLAIN, 12);
    public final static Font font_btn_bold   = new Font("Arial", Font.BOLD, 9);
    public final static Font font_btn_small  = new Font("Arial", Font.PLAIN, 9);

    // Colors
    public final static Color color_button                              = new Color(233, 236, 242);
    public final static Color color_button_hover                        = new Color(160, 160, 160);
    public final static Color color_button_font                         = Color.BLACK;
    public final static Color color_button_font_hover                   = Color.BLACK;
    public final static Color color_textfield_background_focus_color    = new Color(255, 255, 200);
    public final static Color color_textfield_background_nofocus_color  = Color.WHITE;
    public final static Color color_textfield_foreground_focus_color    = Color.BLACK;
    public final static Color color_textfield_forground_nofocus_color   = Color.BLACK;
    public final static Color color_textfield_foreground_disabled       = Color.BLUE;
    public final static Color color_textfield_background_disabled       = new Color(241, 241, 241);
    public final static Color color_text_maxsize_color                  = Color.RED;
    public final static Color color_app_window                          = new Color(241, 241, 241);
    public final static Color color_listBackground                      = new Color(243, 251, 255);
    public final static Color color_listSelectionBackground             = new Color(51, 122, 183);
    public final static Color color_listSelectionForeground             = Color.WHITE;
}
