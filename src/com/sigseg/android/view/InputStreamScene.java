package com.sigseg.android.view;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;

public class InputStreamScene extends Scene {

    private final BitmapFactory.Options options = new BitmapFactory.Options();
    private BitmapRegionDecoder decoder;
    private Bitmap sampleBitmap;
    private final Rect calculatedCacheWindowRect = new Rect();

    /**
     * What is the downsample size for the sample image? 1=1/2, 2=1/4 3=1/8, etc
     */
    private final int downShift = 2;

    /**
     * What percent of total memory should we use for the cache? The bigger the
     * cache, the longer it takes to read -- 1.2 secs for 25%, 600ms for 10%,
     * 500ms for 5%. User experience seems to be best for smaller values.
     */
    int percent = 10; // Above 25 and we get OOMs

    /** How many bytes does one pixel use? */
    final int BYTES_PER_PIXEL = 4;

    public InputStreamScene() {
        options.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    /**
     * Set the Scene to the named asset
     * 
     * @param context
     * @param assetName
     * @throws IOException
     */
    public void setInputStream(InputStream inputStream) throws IOException {
        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
        this.decoder = BitmapRegionDecoder.newInstance(inputStream, false);
        tmpOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, tmpOptions);
        setSceneSize(tmpOptions.outWidth, tmpOptions.outHeight);
        tmpOptions.inJustDecodeBounds = false;
        tmpOptions.inSampleSize = (1 << downShift);
        sampleBitmap = BitmapFactory.decodeStream(inputStream, null, tmpOptions);
        initialize();
    }

    public void setInputStreamFromAssets(Context context, String assetName) {
        new GetInputStreamFromAssetsAsyncTask(context).execute(assetName);
    }

    public void setInputStreamFromFile(Context context, String fileNameWithPath) {
        try {
            BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
            this.decoder = BitmapRegionDecoder.newInstance(fileNameWithPath, false);
            tmpOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(fileNameWithPath, tmpOptions);
            setSceneSize(tmpOptions.outWidth, tmpOptions.outHeight);
            tmpOptions.inJustDecodeBounds = false;
            tmpOptions.inSampleSize = (1 << downShift);
            sampleBitmap = BitmapFactory.decodeFile(fileNameWithPath, tmpOptions);
            initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setInputStreamFromBitmap(Context context, Bitmap bitmap) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] byteArray = baos.toByteArray();
            ByteArrayInputStream inputstream = new ByteArrayInputStream(byteArray);
            setInputStream(inputstream);
            inputstream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected Bitmap fillCache(Rect origin) {
        Bitmap bitmap = decoder.decodeRegion(origin, options);
        return bitmap;
    }

    @Override
    protected void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample, float scaleFactor) {
        Canvas c = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        int left = (rectOfSample.left >> downShift);
        int top = (rectOfSample.top >> downShift);
        int right = left + (c.getWidth() >> downShift);
        int bottom = top + (c.getHeight() >> downShift);
        Rect srcRect = new Rect(left, top, right, bottom);
        Rect identity = new Rect(0, 0, c.getWidth(), c.getHeight());
        c.scale(scaleFactor, scaleFactor);
        c.drawBitmap(sampleBitmap, srcRect, identity, paint);
    }

    @Override
    protected Rect calculateCacheWindow(Rect viewportRect) {
        long bytesToUse = Runtime.getRuntime().maxMemory() * percent / 100;
        Point size = getSceneSize();

        int vw = viewportRect.width();
        int vh = viewportRect.height();

        int tw = 0;
        int th = 0;
        int mw = tw;
        int mh = th;
        while ((vw + tw) * (vh + th) * BYTES_PER_PIXEL < bytesToUse) {
            mw = tw++;
            mh = th++;
        }

        if (vw + mw > size.x)
            mw = Math.max(0, size.x - vw);
        if (vh + mh > size.y)
            mh = Math.max(0, size.y - vh);
        int left = viewportRect.left - (mw >> 1);
        int right = viewportRect.right + (mw >> 1);
        if (left < 0) {
            right = right - left;
            left = 0;
        }
        if (right > size.x) {
            left = left - (right - size.x);
            right = size.x;
        }

        int top = viewportRect.top - (mh >> 1);
        int bottom = viewportRect.bottom + (mh >> 1);
        if (top < 0) {
            bottom = bottom - top;
            top = 0;
        }
        if (bottom > size.y) {
            top = top - (bottom - size.y);
            bottom = size.y;
        }

        calculatedCacheWindowRect.set(left, top, right, bottom);
        return calculatedCacheWindowRect;
    }

    @Override
    protected void fillCacheOutOfMemoryError(OutOfMemoryError error) {
        if (percent > 0)
            percent -= 1;
    }

    @Override
    protected void drawComplete(Canvas canvas) {
    }
    
    public class GetInputStreamFromAssetsAsyncTask extends AsyncTask<String, Void, InputStream> {
        
        private Context context;

        public GetInputStreamFromAssetsAsyncTask(Context context){
            this.context = context.getApplicationContext();
        }
        
        protected InputStream doInBackground(String... names) {           
            try {
                return context.getApplicationContext().getAssets().open(names[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }


        protected void onPostExecute(InputStream result) {
            if (result!=null){
                try {
                    setInputStream(result);
                    result.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    

}
