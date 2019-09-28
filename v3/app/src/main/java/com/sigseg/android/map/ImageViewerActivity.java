package com.sigseg.android.map;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.sigseg.android.io.AssetCopier;
import com.sigseg.android.io.RandomAccessFileInputStream;
import com.sigseg.android.worldmap.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;


public class ImageViewerActivity extends Activity {
    private static final String TAG = "ImageViewerActivity";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_FN = "FN";

    private static final String MAP_FILE = "world.jpg";
    
    private ImageSurfaceView imageSurfaceView;
    private String filename = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);
        imageSurfaceView = (ImageSurfaceView) findViewById(R.id.worldview);

        // Setup/restore state
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_X) && savedInstanceState.containsKey(KEY_Y)) {
            Log.d(TAG, "restoring state");
            int x = (Integer) savedInstanceState.get(KEY_X);
            int y = (Integer) savedInstanceState.get(KEY_Y);

            String fn = null;
            if (savedInstanceState.containsKey(KEY_FN))
                fn = (String) savedInstanceState.get(KEY_FN);

            try {
                if (fn == null || fn.length()==0) {
                    imageSurfaceView.setInputStream(getAssets().open(MAP_FILE));
                } else {
                    imageSurfaceView.setInputStream(new RandomAccessFileInputStream(fn));
                }
                imageSurfaceView.setViewport(new Point(x, y));
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            // Centering the map to start
            Intent intent = getIntent();
            try {
                Uri uri = null;
                if (intent!=null)
                    uri = getIntent().getData();

                InputStream is;
                if (uri != null) {
                    filename = uri.getPath();
                    is = new RandomAccessFileInputStream(uri.getPath());
                } else {

                    new AssetCopier(this).copy("", getFilesDir());

                    File f = new File(getFilesDir() + java.io.File.separator + MAP_FILE);
                    Log.d("JLSTUFF", "file length = "+f.length());
//                    is = new RandomAccessFileInputStream(f);
                    is = new RandomAccessFileInputStream(f);
//                    is = getAssets().open(MAP_FILE);
                }

                imageSurfaceView.setInputStream(is);
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
            imageSurfaceView.setViewportCenter();
        }
    }

//                    is = new RandomAccessFileInputStream("file://sdcard/world.jpg");
//                    AssetManager am = getAssets();
//                    AssetFileDescriptor afd = null;
//                    try {
//                        afd = am.openFd( MAP_FILE);
//
//                        // Create new file to copy into.
//                        File file = new File(getFilesDir(), MAP_FILE);
//                        file.createNewFile();
//
//                        copyFdToFile(afd.getFileDescriptor(), file);
//
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }

//    public static void copyFdToFile(FileDescriptor src, File dst) throws IOException {
//        FileChannel inChannel = new FileInputStream(src).getChannel();
//        FileChannel outChannel = new FileOutputStream(dst).getChannel();
//        try {
//            inChannel.transferTo(0, inChannel.size(), outChannel);
//        } finally {
//            if (inChannel != null)
//                inChannel.close();
//            if (outChannel != null)
//                outChannel.close();
//        }
//    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Point p = new Point();
        imageSurfaceView.getViewport(p);
        outState.putInt(KEY_X, p.x);
        outState.putInt(KEY_Y, p.y);
        if (filename!=null)
            outState.putString(KEY_FN, filename);
        super.onSaveInstanceState(outState);
    }
}
