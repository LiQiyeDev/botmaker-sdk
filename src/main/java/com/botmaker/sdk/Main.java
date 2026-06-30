package com.botmaker.sdk;

import com.botmaker.sdk.api.BotMaker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.opencv.OpenCvNative;

public class Main {

	static {
		OpenCvNative.ensureLoaded();
	}

	public static void main(String[] args) {

		System.out.print("Hello and welcome!");

		for (int i = 1; i <= 5; i++) {
			System.out.println("i = " + i);
		}
        MatchResult r= ImageFinder.find(new ImageTemplate("src/main/resources/images/accept_button.png"));
		BotMaker.print(r.isFound());
    }
}