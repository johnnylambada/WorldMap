package com.sigseg.android.map;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.sigseg.android.worldmap.R;


public class ImageViewerActivity extends Activity {
    private static final String TAG = "ImageViewerActivity";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String MAP_FILE = "world.jpg";
    
    private ImageSurfaceView imageSurfaceView;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);
        imageSurfaceView = findViewById(R.id.worldview);

        try {
            imageSurfaceView.setInputStream(getAssets().open(MAP_FILE));
        } catch (java.io.IOException e) {
            Log.wtf(TAG, "Fail", e);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_X) && savedInstanceState.containsKey(KEY_Y)) {
            imageSurfaceView.post(new Runnable() {
                @Override
                public void run() {
                    final int x = (int) savedInstanceState.get(KEY_X);
                    final int y = (int) savedInstanceState.get(KEY_Y);
                    imageSurfaceView.setViewport(new Point(x, y));
                }
            });
        } else {
            // Centering the map to start
            imageSurfaceView.post(new Runnable() {
                @Override
                public void run() {
                    imageSurfaceView.setViewportCenter();
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        final Point p = new Point();
        imageSurfaceView.getViewport(p);
        outState.putInt(KEY_X, p.x);
        outState.putInt(KEY_Y, p.y);
        super.onSaveInstanceState(outState);
    }
}
