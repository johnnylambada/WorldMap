package com.sigseg.android.view;

import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.os.Debug;
import android.util.Log;

/*
 * +-------------------------------------------------------------------+
 * |                                        |                          |
 * |  +------------------------+            |                          |
 * |  |                        |            |                          |
 * |  |                        |            |                          |
 * |  |                        |            |                          |
 * |  |           Viewport     |            |                          |
 * |  +------------------------+            |                          |
 * |                                        |                          |
 * |                                        |                          |
 * |                                        |                          |
 * |                          Cache         |                          |
 * |----------------------------------------+                          |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                               Entire bitmap -- too big for memory |
 * +-------------------------------------------------------------------+
 */
/**
 * Keeps track of an entire Scene -- a bitmap (or virtual bitmap) that is much too large
 * to fit into memory. Clients subclass this class and extend its abstract methods to
 * actually return the necessary bitmaps.
 */
public abstract class Scene {
    private final String TAG = "Scene";

    private final static int MINIMUM_PIXELS_IN_VIEW = 50;

    /** The size of the Scene */
    private Point size = new Point();
    /** The viewport */
    private final Viewport viewport = new Viewport();
    /** The cache */
    private final Cache cache = new Cache();
    
    //region [gs]etSceneSize
    /** Set the size of the scene */
    public void setSceneSize(int width, int height){
        size.set(width, height);
    }
    /** Returns a Point representing the size of the scene. Don't modify the returned Point! */
    public Point getSceneSize(){
        return size;
    }
    /** Set the passed-in point to the size of the scene */
    public void getSceneSize(Point point){
        point.set(size.x, size.y);
    }
    //endregion

    //region getViewport()
    public Viewport getViewport(){return viewport;}
    //endregion

    //region initialize/start/stop/suspend/invalidate the cache
    /** Initializes the cache */
    public void initialize(){
        if (cache.getState()==CacheState.UNINITIALIZED){
            synchronized(cache){
                cache.setState(CacheState.INITIALIZED);
            }
        }
    }
    /** Starts the cache thread */
    public void start(){
        cache.start();
    }
    /** Stops the cache thread */
    public void stop(){
        cache.stop();
    }
    /** 
     * Suspends or unsuspends the cache thread. This can be
     * used to temporarily stop the cache from updating
     * during a fling event.
     * @param suspend True to suspend the cache. False to unsuspend.
     */
    public void setSuspend(boolean suspend){
        if (suspend) {
            synchronized(cache){
                cache.setState(CacheState.SUSPEND);
            }
        } else {
            if (cache.getState()==CacheState.SUSPEND) {
                synchronized(cache){
                    cache.setState(CacheState.INITIALIZED);
                }
            }
        }
    }
    /** Invalidate the cache. This causes it to refill */
    @SuppressWarnings("unused")
    public void invalidate(){
        cache.invalidate();
    }
    //endregion

    //region void draw(Canvas c)
    /**
     * Draw the scene to the canvas. This operation fills the canvas with
     * the bitmap referenced by the viewport's location within the Scene.
     * If the cache already has the data (and is not suspended), then the
     * high resolution bitmap from the cache is used. If it's not available,
     * then the lower resolution bitmap from the sample is used.
     */
    public void draw(Canvas c){
        viewport.draw(c);
    }
    //endregion

    //region protected abstract
    /**
     * This method must return a high resolution Bitmap that the Scene 
     * will use to fill out the viewport bitmap upon request. This bitmap
     * is normally larger than the viewport so that the viewport can be
     * scrolled without having to refresh the cache. This method runs
     * on a thread other than the UI thread, and it is not under a lock, so
     * it is expected that this method can run for a long time (seconds?). 
     * @param rectOfCache The Rect representing the area of the Scene that
     * the Scene wants cached.
     * @return the Bitmap representing the requested area of the larger bitmap
     */
    protected abstract Bitmap fillCache(Rect rectOfCache);
    /**
     * The memory allocation you just did in fillCache caused an OutOfMemoryError.
     * You can attempt to recover. Experience shows that when we get an 
     * OutOfMemoryError, we're pretty hosed and are going down. For instance, if
     * we're trying to decode a bitmap region with
     * {@link android.graphics.BitmapRegionDecoder} and we run out of memory, 
     * we're going to die somewhere in the C code with a SIGSEGV. 
     * @param error The OutOfMemoryError exception data
     */
    protected abstract void fillCacheOutOfMemoryError( OutOfMemoryError error );
    /**
     * Calculate the Rect of the cache's window based on the current viewportRect.
     * The returned Rect must at least contain the viewportRect, but it can be
     * larger if the system believes a bitmap of the returned size will fit into
     * memory. This function must be fast as it happens while the cache lock is held.
     * @param viewportRect The returned must be able to contain this Rect
     * @return The Rect that will be used to fill the cache
     */
    protected abstract Rect calculateCacheWindow(Rect viewportRect);
    /**
     * This method fills the passed-in bitmap with sample data. This function must
     * return as fast as possible so it shouldn't have to do any IO at all -- the
     * quality of the user experience rests on the speed of this function.
     * @param bitmap The Bitmap to fill
     * @param rectOfSample Rectangle within the Scene that this bitmap represents.
     */
    protected abstract void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample);
    /**
     * The Cache is done drawing the bitmap -- time to add the finishing touches
     * @param canvas a canvas on which to draw
     */
    protected abstract void drawComplete(Canvas canvas);
    //endregion

    //region class Viewport

    public class Viewport {
        /** The bitmap of the current viewport */
        Bitmap bitmap = null;
        /** A Rect that defines where the Viewport is within the scene */
        final Rect window = new Rect(0,0,0,0);
        float zoom = 1.0f;

        public void setOrigin(int x, int y){
            synchronized(this){
                int w = window.width();
                int h = window.height();
    
                // check bounds
                if (x < 0)
                    x = 0;
    
                if (y < 0)
                    y = 0;
    
                if (x + w > size.x)
                    x = size.x - w;
    
                if (y + h > size.y)
                    y = size.y - h;
    
                window.set(x, y, x+w, y+h);
            }
        }
        public void setSize( int w, int h ){
            synchronized (this) {
                if (bitmap !=null){
                    bitmap.recycle();
                    bitmap = null;
                }
                bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
                window.set(
                        window.left,
                        window.top,
                        window.left + w,
                        window.top + h);
            }
        }
        public void getOrigin(Point p){
            synchronized (this) {
                p.set(window.left, window.top);
            }
        }
        public void getSize(Point p){
            synchronized (this) {
                p.x = window.width();
                p.y = window.height();
            }
        }
        public void getPhysicalSize(Point p){
            synchronized (this){
                p.x = getPhysicalWidth();
                p.y = getPhysicalHeight();
            }
        }
        public int getPhysicalWidth(){
            return bitmap.getWidth();
        }
        public int getPhysicalHeight(){
            return bitmap.getHeight();
        }
        public float getZoom(){
            return zoom;
        }
        public void zoom(float factor, PointF screenFocus){
            if (factor!=1.0){

                PointF screenSize = new PointF(bitmap.getWidth(),bitmap.getHeight());
                PointF sceneSize = new PointF(getSceneSize());
                float screenWidthToHeight = screenSize.x / screenSize.y;
                float screenHeightToWidth = screenSize.y / screenSize.x;
                synchronized (this){
                    float newZoom = zoom * factor;
                    RectF w1 = new RectF(window);
                    RectF w2 = new RectF();
                    PointF sceneFocus = new PointF(
                            w1.left + (screenFocus.x/screenSize.x)*w1.width(),
                            w1.top + (screenFocus.y/screenSize.y)*w1.height()
                    );
                    float w2Width = getPhysicalWidth() * newZoom;
                    if (w2Width > sceneSize.x){
                        w2Width = sceneSize.x;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    if (w2Width < MINIMUM_PIXELS_IN_VIEW){
                        w2Width = MINIMUM_PIXELS_IN_VIEW;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    float w2Height = w2Width * screenHeightToWidth;
                    if (w2Height > sceneSize.y){
                        w2Height = sceneSize.y;
                        w2Width = w2Height * screenWidthToHeight;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    if (w2Height < MINIMUM_PIXELS_IN_VIEW){
                        w2Height = MINIMUM_PIXELS_IN_VIEW;
                        w2Width = w2Height * screenWidthToHeight;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    w2.left = sceneFocus.x - ((screenFocus.x/screenSize.x) * w2Width);
                    w2.top = sceneFocus.y - ((screenFocus.y/screenSize.y) * w2Height);
                    if (w2.left<0)
                        w2.left=0;
                    if (w2.top<0)
                        w2.top=0;
                    w2.right = w2.left+w2Width;
                    w2.bottom= w2.top+w2Height;
                    if (w2.right>sceneSize.x){
                        w2.right=sceneSize.x;
                        w2.left=w2.right-w2Width;
                    }
                    if (w2.bottom>sceneSize.y){
                        w2.bottom=sceneSize.y;
                        w2.top=w2.bottom-w2Height;
                    }
                    window.set((int)w2.left,(int)w2.top,(int)w2.right,(int)w2.bottom);
                    zoom = newZoom;
//                    Log.d(TAG,String.format(
//                            "f=%.2f, z=%.2f, scrf(%.0f,%.0f), scnf(%.0f,%.0f) w1s(%.0f,%.0f) w2s(%.0f,%.0f) w1(%.0f,%.0f,%.0f,%.0f) w2(%.0f,%.0f,%.0f,%.0f)",
//                            factor,
//                            zoom,
//                            screenFocus.x,
//                            screenFocus.y,
//                            sceneFocus.x,
//                            sceneFocus.y,
//                            w1.width(),w1.height(),
//                            w2Width, w2Height,
//                            w1.left,w1.top,w1.right,w1.bottom,
//                            w2.left,w2.top,w2.right,w2.bottom
//                            ));
                }
            }
        }
        void draw(Canvas c){
            cache.update(this);
            synchronized (this){
                if (c!=null && bitmap !=null){
                    c.drawBitmap(bitmap, 0F, 0F, null);
                    drawComplete(c);
                }
            }
        }
    }
    //endregion

    //region class Cache

    private enum CacheState {UNINITIALIZED,INITIALIZED,START_UPDATE,IN_UPDATE,READY,SUSPEND}
    /**
     * Keep track of the cached bitmap
     */
    private class Cache {
        /** A Rect that defines where the Cache is within the scene */
        final Rect window = new Rect(0,0,0,0);
        /** The bitmap of the current cache */
        Bitmap bitmapRef = null;
        CacheState state = CacheState.UNINITIALIZED;

        void setState(CacheState newState){
            if (Debug.isDebuggerConnected())
                Log.i(TAG,String.format("cacheState old=%s new=%s",state.toString(),newState.toString()));
            state = newState;
        }
        CacheState getState(){ return state; }
        
        /** Our load from disk thread */
        CacheThread cacheThread;
        
        void start(){
            if (cacheThread!=null){
                cacheThread.setRunning(false);
                cacheThread.interrupt();
                cacheThread = null;
            }
            cacheThread = new CacheThread(this);
            cacheThread.setName("cacheThread");
            cacheThread.start();
        }
        
        void stop(){
            cacheThread.running = false;
            cacheThread.interrupt();

            boolean retry = true;
            while (retry) {
                try {
                    cacheThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // we will try it again and again...
                }
            }
            cacheThread = null;
        }
        void invalidate(){
            synchronized(this){
                setState(CacheState.INITIALIZED);
                cacheThread.interrupt();
            }
        }
        
        /** Fill the bitmap with the part of the scene referenced by the viewport Rect */
        void update(Viewport viewport){
            Bitmap bitmap = null;    // If this is null at the bottom, then load from the sample
            synchronized(this){
                switch(getState()){
                case UNINITIALIZED:
                    // nothing can be done -- should never get here
                    return;
                case INITIALIZED:
                    // time to cache some data
                    setState(CacheState.START_UPDATE);
                    cacheThread.interrupt();
                    break;
                case START_UPDATE:
                    // I already told the thread to start
                    break;
                case IN_UPDATE:
                    // Already reading some data, just use the sample 
                    break;
                case SUSPEND:
                    // Loading from cache suspended.
                    break;
                case READY:
                    // I have some data to show
                    if (bitmapRef==null){
                        // Start the cache off right
                        if (Debug.isDebuggerConnected())
                            Log.d(TAG,"bitmapRef is null");
                        setState(CacheState.START_UPDATE);
                        cacheThread.interrupt();
                    } else if (!window.contains(viewport.window)){
                        if (Debug.isDebuggerConnected())
                            Log.d(TAG,"viewport not in cache");
                        setState(CacheState.START_UPDATE);
                        cacheThread.interrupt();
                    } else {
                        // Happy case -- the cache already contains the Viewport
                        bitmap = bitmapRef;
                    }
                    break;
                }
            }
            if (bitmap==null)
                loadSampleIntoViewport();
            else
                loadBitmapIntoViewport(bitmap);
        }
        
        void loadBitmapIntoViewport(Bitmap bitmap){
            if (bitmap!=null){
                synchronized(viewport){
                    int left   = viewport.window.left - window.left;
                    int top    = viewport.window.top  - window.top;
                    int right  = left + viewport.window.width();
                    int bottom = top  + viewport.window.height();
                    viewport.getPhysicalSize(dstSize);
                    srcRect.set( left, top, right, bottom );
                    dstRect.set(0, 0, dstSize.x, dstSize.y);
                    Canvas c = new Canvas(viewport.bitmap);
                    c.drawColor(Color.BLACK);
                    c.drawBitmap(
                            bitmap,
                            srcRect,
                            dstRect,
                            null);
//                    try {
//                        FileOutputStream fos = new FileOutputStream("/sdcard/viewport.png");
//                        viewport.bitmap.compress(Bitmap.CompressFormat.PNG, 99, fos);
//                        Thread.sleep(1000);
//                    } catch  (Exception e){
//                        System.out.print(e.getMessage());
//                    }
                }
            }
        }
        final Rect srcRect = new Rect(0,0,0,0);
        final Rect dstRect = new Rect(0,0,0,0);
        final Point dstSize = new Point();
        
        void loadSampleIntoViewport(){
            if (getState()!=CacheState.UNINITIALIZED){
                synchronized(viewport){
                    drawSampleRectIntoBitmap(
                        viewport.bitmap,
                        viewport.window
                        );
                }
            }
        }
    }
    //endregion

    //region class CacheThread
    /**
     * <p>The CacheThread's job is to wait until the {@link Cache#state} is 
     * {@link CacheState#START_UPDATE} and then update the {@link Cache} given
     * the current {@link Viewport#window}. It does not want to hold the cache
     * lock during the call to {@link Scene#fillCache(Rect)} because the call 
     * can take a long time. If we hold the lock, the user experience is very 
     * jumpy.</p>
     * <p>The CacheThread and the {@link Cache} work hand in hand, both using the 
     * cache itself to synchronize on and using the {@link Cache#state}. 
     * The {@link Cache} is free to update any part of the cache object as long 
     * as it holds the lock. The CacheThread is careful to make sure that it is
     * the {@link Cache#state} is {@link CacheState#IN_UPDATE} as it updates
     * the {@link Cache}. It locks and unlocks the cache all along the way, but
     * makes sure that the cache is not locked when it calls 
     * {@link Scene#fillCache(Rect)}. 
     */
    class CacheThread extends Thread {
        final Cache cache;
        boolean running = false;
        void setRunning(boolean value){ running = value; }
        
        CacheThread(Cache cache){ this.cache = cache; }
        
        @Override
        public void run() {
            running=true;
            Rect viewportRect = new Rect(0,0,0,0);
            while(running){
                while(running && cache.getState()!=CacheState.START_UPDATE)
                    try {
                        // Sleep until we have something to do
                        Thread.sleep(Integer.MAX_VALUE);
                    } catch (InterruptedException ignored) {}
                if (!running)
                    return;
                long start = System.currentTimeMillis();
                boolean cont = false;
                synchronized (cache) {
                    if (cache.getState()==CacheState.START_UPDATE){
                        cache.setState(CacheState.IN_UPDATE);
                        cache.bitmapRef = null;
                        cont = true;
                    }
                }
                if (cont){
                    synchronized(viewport){
                        viewportRect.set(viewport.window);
                    }
                    synchronized (cache) {
                        if (cache.getState()==CacheState.IN_UPDATE)
                            //cache.setWindowRect(viewportRect);
                            cache.window.set(calculateCacheWindow(viewportRect));
                        else
                            cont = false;
                    }
                    if (cont){
                        try{
                            Bitmap bitmap = fillCache(cache.window);
                            if (bitmap!=null){
                                synchronized (cache){
                                    if (cache.getState()==CacheState.IN_UPDATE){
                                        cache.bitmapRef = bitmap;
                                        cache.setState(CacheState.READY);
                                    } else {
                                        Log.w(TAG,"fillCache operation aborted");
                                    }
                                }
                            }
                            long done = System.currentTimeMillis();
                            if (Debug.isDebuggerConnected())
                                Log.d(TAG,String.format("fillCache in %dms",done-start));
                        } catch (OutOfMemoryError e){
                                    Log.d(TAG,"CacheThread out of memory");
                            /*
                             *  Attempt to recover. Experience shows that if we
                             *  do get an OutOfMemoryError, we're pretty hosed and are going down.
                             */
                            synchronized (cache){
                                fillCacheOutOfMemoryError(e);
                                if (cache.getState()==CacheState.IN_UPDATE){
                                    cache.setState(CacheState.START_UPDATE);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    //endregion
}
