package com.sigseg.android.worldmap;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class WorldMapActivity extends Activity {
	private static final String TAG = "WorldMapActivity";
	private static final String KEY_POINT = "POINT";
	
	private WorldView worldView;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate()");
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        worldView = (WorldView) findViewById(R.id.worldview);
        if (savedInstanceState!=null) {
        	if (savedInstanceState.containsKey(KEY_POINT)){
        		Point p = (Point) savedInstanceState.get(KEY_POINT);
        		worldView.setViewport(p);
        		Log.d(TAG,"Setting viewport to "+p.toString());
        	} else {
            	Log.d(TAG,"savedInstanceState doesn't contain "+KEY_POINT);
        	}
        } else {
        	Log.d(TAG,"savedInstanceState is null");
        }
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(TAG, "onSaveInstanceState()");
		Point p = worldView.getViewport();
		outState.putParcelable(KEY_POINT, (Parcelable) p);
		super.onSaveInstanceState(outState);
	}
}