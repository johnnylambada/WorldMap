package com.sigseg.android.view;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.*;
import android.util.Log;

public class InputStreamScene extends Scene {
    private static final String TAG=InputStreamScene.class.getSimpleName();
    
    private static final boolean DEBUG = false;
    private static final BitmapFactory.Options options = new BitmapFactory.Options();

    /** What is the downsample size for the sample image?  1=1/2, 2=1/4 3=1/8, etc */
    private static final int DOWN_SAMPLE_SHIFT = 2;

    /** How many bytes does one pixel use? */
    private final int BYTES_PER_PIXEL = 4;

    /** What percent of total memory should we use for the cache? The bigger the cache,
     * the longer it takes to read -- 1.2 secs for 25%, 600ms for 10%, 500ms for 5%.
     * User experience seems to be best for smaller values. 
     */
    private int percent = 5; // Above 25 and we get OOMs

    private BitmapRegionDecoder decoder;
    private Bitmap sampleBitmap;

    static {
        options.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    public InputStreamScene(InputStream inputStream) throws IOException {
        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();

        this.decoder = BitmapRegionDecoder.newInstance(inputStream, false);

        // Grab the bounds for the scene dimensions
        tmpOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, tmpOptions);
        setSceneSize(tmpOptions.outWidth, tmpOptions.outHeight);

        // Create the sample image
        tmpOptions.inJustDecodeBounds = false;
        tmpOptions.inSampleSize = (1<< DOWN_SAMPLE_SHIFT);
        sampleBitmap = BitmapFactory.decodeStream(inputStream, null, tmpOptions);

        initialize();
    }

    @Override
    protected Bitmap fillCache(Rect origin) {
        Bitmap bitmap = null;
        if (decoder!=null)
            bitmap = decoder.decodeRegion( origin, options );
        return bitmap;
    }

    private static Paint red = new Paint();
    static{
        red.setColor(Color.RED);
        red.setStrokeWidth(5L);
    }
    @Override
    protected void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample) {
        if (bitmap!=null){
            Canvas c = new Canvas(bitmap);
            int left   = (rectOfSample.left>> DOWN_SAMPLE_SHIFT);
            int top    = (rectOfSample.top>> DOWN_SAMPLE_SHIFT);
            int right  = left + (rectOfSample.width()>> DOWN_SAMPLE_SHIFT);
            int bottom = top + (rectOfSample.height()>> DOWN_SAMPLE_SHIFT);
            Rect srcRect = new Rect( left, top, right, bottom );
            Rect identity= new Rect(0,0,c.getWidth(),c.getHeight());
            c.drawBitmap(
                sampleBitmap,
                srcRect,
                identity,
                null
                );
//            c.drawLine(0L,0L,c.getWidth(),c.getHeight(),red);
        }
    }

//    @Override
//    protected Rect calculateCacheWindow(Rect viewportRect) {
//        // Simplest implementation
//        return viewportRect;
//    }

    private Rect calculatedCacheWindowRect = new Rect();
    @Override
    protected Rect calculateCacheWindow(Rect viewportRect) {
        long bytesToUse = Runtime.getRuntime().maxMemory() * percent / 100;
        Point size = getSceneSize();

        int vw = viewportRect.width();
        int vh = viewportRect.height();
        
        // Calculate the max size of the margins to fit in our memory budget
        int tw=0;
        int th=0;
        int mw = tw;
        int mh = th;
        while((vw+tw) * (vh+th) * BYTES_PER_PIXEL < bytesToUse){
            mw = tw++;
            mh = th++;
        }
        
        // Trim the margins if they're too big.
        if (vw+mw > size.x) // viewport width + margin width > width of the image
            mw = Math.max(0, size.x-vw);
        if (vh+mh > size.y) // viewport height + margin height > height of the image
            mh = Math.max(0, size.y-vh);
        
        // Figure out the left & right based on the margin. We assume our viewportRect
        // is <= our size. If that's not the case, then this logic breaks.
        int left = viewportRect.left - (mw>>1);
        int right = viewportRect.right + (mw>>1);
        if (left<0){
            right = right - left; // Add's the overage on the left side back to the right
            left = 0;
        }
        if (right>size.x){
            left = left - (right-size.x); // Adds overage on right side back to left
            right = size.x;
        }

        // Figure out the top & bottom based on the margin. We assume our viewportRect
        // is <= our size. If that's not the case, then this logic breaks.
        int top = viewportRect.top - (mh>>1); 
        int bottom = viewportRect.bottom + (mh>>1);
        if (top<0){
            bottom = bottom - top; // Add's the overage on the top back to the bottom
            top = 0;
        }
        if (bottom>size.y){
            top = top - (bottom-size.y); // Adds overage on bottom back to top
            bottom = size.y;
        }
        
        // Set the origin based on our new calculated values.
        calculatedCacheWindowRect.set(left, top, right, bottom);
        if (DEBUG) Log.d(TAG,"new cache.originRect = "+calculatedCacheWindowRect.toShortString()+" size="+size.toString());
        return calculatedCacheWindowRect;
    }

    @Override
    protected void fillCacheOutOfMemoryError(OutOfMemoryError error) {
        if (percent>0)
            percent -= 1;
        Log.e(TAG,String.format("caught oom -- cache now at %d percent.",percent));
    }

    @Override
    protected void drawComplete(Canvas canvas) {
        // TODO Auto-generated method stub
        
    }
}
