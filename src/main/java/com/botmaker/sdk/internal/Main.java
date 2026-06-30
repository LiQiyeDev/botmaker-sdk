package com.botmaker.sdk.internal;

import com.botmaker.sdk.internal.capture.ImageDisplay;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;

import static com.botmaker.sdk.internal.capture.CaptureTest.testLiveCapture;


public class Main {

    static{
        Loader.load(opencv_java.class);
    }

    public static void main(String[] args) {
        try {
            ImageDisplay imageDisplay = null;
            testLiveCapture();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void placeholder() {
        System.out.println("[Debug] Placeholder function executed.");
    }
}