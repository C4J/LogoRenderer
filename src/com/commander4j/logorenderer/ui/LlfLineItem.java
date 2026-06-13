package com.commander4j.logorenderer.ui;

/**
 * Holds one raw line from the LLF file together with a flag indicating
 * whether it should be included when re-parsing for the preview.
 */
public class LlfLineItem {

    private final String  line;
    private       boolean enabled;

    public LlfLineItem(String line, boolean enabled) {
        this.line    = line;
        this.enabled = enabled;
    }

    public String  getLine()               { return line; }
    public boolean isEnabled()             { return enabled; }
    public void    setEnabled(boolean v)   { this.enabled = v; }

    @Override
    public String toString() { return line; }
}
