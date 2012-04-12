package com.sigseg.android.worldmap;

import android.util.Log;

public class Application extends android.app.Application {
	private final static String TAG = "Application";
	public final static boolean DEBUG = true;
	
	@Override
	public void onCreate() {
		logMemory("app-start");
		super.onCreate();
		
	}

	private static void logMemory(String tag){
		long freeMemory = Runtime.getRuntime().freeMemory();
		long maxMemory = Runtime.getRuntime().maxMemory();
		if (Application.DEBUG)
			Log.d(tag,String.format("maxMemory=%d, freeMemory=%d, diff=%d",maxMemory,freeMemory,maxMemory-freeMemory));
	}
}
