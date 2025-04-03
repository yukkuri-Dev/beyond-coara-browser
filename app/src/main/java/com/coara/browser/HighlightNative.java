package com.coara.browser;

public class HighlightNative {
    static {
        System.loadLibrary("highlightNative");
    }
    
    public static native int[][] highlightHtmlNative(String html);
}
