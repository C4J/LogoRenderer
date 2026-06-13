package com.commander4j.logorenderer;

/**
 * Holds the response string and success/failure flag for a single protocol command.
 */
public class ComboResult {

    private String response = "";
    private boolean result  = true;

    public String getResponse()  { return response; }
    public void setResponse(String response) { this.response = response; }

    public boolean getResult()   { return result; }
    public void setResult(boolean result) { this.result = result; }
}
