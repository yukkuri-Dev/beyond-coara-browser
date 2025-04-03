package com.coara.browser;

public class HighlightNative {
    static {
        System.loadLibrary("highlight_native");
    }
    
    public static native int[][] highlightHtmlNative(String html);
}
