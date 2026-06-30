package com.botmaker.sdk.internal.opencv;

import nu.pattern.OpenCV;

public final class OpenCvNative {

    private OpenCvNative() {}

    private static volatile boolean loaded = false;

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        // OpenPnP handles extracting and loading the correct OS native library automatically
        OpenCV.loadLocally();
        loaded = true;
    }
}