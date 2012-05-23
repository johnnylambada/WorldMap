package com.sigseg.android.worldmap;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Window;
import android.view.WindowManager;

import com.apphance.android.Apphance;
import com.apphance.android.Log;

public class WorldMapActivity extends Activity {
	private static final String TAG = "WorldMapActivity";
	private static final String KEY_POINT = "POINT";
	
	public static final String APPHANCE_KEY = "574846bd9bd4cfa59623e7fbbb1c6d6b3f342a6a";
	
	private WorldView worldView;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate()");
        Apphance.startNewSession(this, APPHANCE_KEY , Apphance.Mode.Silent);
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