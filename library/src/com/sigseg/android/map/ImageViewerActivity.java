package com.sigseg.android.map;

import android.net.Uri;
import java.io.InputStream;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import com.sigseg.android.io.RandomAccessFileInputStream;


public class ImageViewerActivity extends Activity {
	private static final String TAG = "ImageViewerActivity";
	private static final String KEY_X = "X";
	private static final String KEY_Y = "Y";
	private static final String KEY_FN = "FN";
	
	private ImageSurfaceView imageSurfaceView;
        String filename;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        imageSurfaceView = (ImageSurfaceView) findViewById(R.id.worldview);
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_X) && savedInstanceState.containsKey(KEY_Y)) {
            int x = (Integer) savedInstanceState.get(KEY_X);
            int y = (Integer) savedInstanceState.get(KEY_Y);
            String fn;
            Point p = new Point(x, y);

            Log.d(TAG, "restoring state");
            if (savedInstanceState.containsKey(KEY_FN))
                fn = (String) savedInstanceState.get(KEY_FN);
            else fn = null;
            Log.d(TAG, ".. fn = " + fn);
            try {
                if (fn == "" || fn == null) {
                    Log.d(TAG, "restore, setting stream to world.jpg");
                    imageSurfaceView.setImageIS(getAssets().open("world.jpg"));
                } else {
                    Log.d(TAG, "restore, opening file " + fn);
                    imageSurfaceView.setImageIS(new RandomAccessFileInputStream(fn));
                }
                imageSurfaceView.setViewport(p);
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
            Log.d(TAG, "restored state");
        } else {
            // Centering the map to start
            try {
                Uri uri = getIntent() != null ? getIntent().getData() : null;
                InputStream is;
                if (uri != null) {
                    Log.d(TAG, "file is: " + uri.getPath());
                    filename = uri.getPath();
                    is = (InputStream) new RandomAccessFileInputStream(uri.getPath());
                } else {
                    is = getAssets().open("world.jpg");
                    filename = "";
                }

                imageSurfaceView.setImageIS(is);
                Log.d(TAG, "filename is " + filename);
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
            imageSurfaceView.setViewportCenter();
        }
    }

    @Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(TAG, "onSaveInstanceState() filename "+filename);
		Point p = imageSurfaceView.getViewport();
		outState.putInt(KEY_X, p.x);
		outState.putInt(KEY_Y, p.y);
		outState.putString(KEY_FN, filename);
		super.onSaveInstanceState(outState);
	}
}
