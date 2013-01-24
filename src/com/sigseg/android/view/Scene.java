package com.sigseg.android.view;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
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
	private final boolean DEBUG = false;

	/** The size of the Scene */
	private Point size = new Point();
	/** The viewport */
	private final Viewport viewport = new Viewport();
	/** The cache */
	private final Cache cache = new Cache();
	
	/** The current scale factor */
	private float scaleFactor = 1;

	//[start] [gs]etSceneSize
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
	//[end]
	//[start] [gs]etViewport(Origin|Size|Translation)
	/**
	 * Get the viewport origin.
	 * @return the a new Point of the origin
	 */
	public Point getViewportOrigin(){
		Point p = new Point();
		synchronized(viewport){
			p.set(viewport.window.left, viewport.window.top);
		}
		return p;
	}
	/**
	 * Set the passed-in point to the current viewport origin.
	 * @param point set() to the current viewport origin.
	 */
	public void getViewportOrigin(Point point){
		synchronized(viewport){
			point.set(viewport.window.left, viewport.window.top);
		}
	}
	/** Set the Viewport origin */
	public void setViewportOrigin(int x, int y){
		viewport.setOrigin(x, y);
	}
	/** Set the size of the viewport within the scene */
	public void setViewportSize(int width, int height){
		viewport.setSize(width, height);
	}
	/** Return a Point with the x value set to the viewport width, y set to height */
	public void getViewportSize(Point p){
		viewport.getSize(p);
	}
	/** Set the translation for the draw of the bitmap within the viewport */
	public void setViewportTranslation(int x, int y){
		viewport.setTranslation(x, y);
	}
	/** Return the Point  */
	public void getViewportTranslation(Point p){
		viewport.getTranslation(p);
	}
	//[end]
	
	
	//[start] changeScaleFactor/getScaleFactor
	public void changeScaleFactor(float multiplier) {
	    
	    float tempScaleFactor = scaleFactor * multiplier;

        // Don't let the object get too small or too large.
        Point viewportSize = new Point();
        Point sceneSize = getSceneSize();
        getViewportSize(viewportSize);
        float min = Math.max((float) viewportSize.x / (float) sceneSize.x, (float) viewportSize.y / (float) sceneSize.y);
        tempScaleFactor = Math.max(min, Math.min(tempScaleFactor, 5.0f));
	    
	    // change the real scale factor used by the display thread
        scaleFactor = tempScaleFactor;
    }
	
	public float getScaleFactor() {
	    return scaleFactor;
	}
	
	//[end]
	
	//[start] initialize/start/stop/suspend/invalidate the cache
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
	public void invalidate(){
		cache.invalidate();
	}
	//[end]
	//[start] void draw(Canvas c)
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
	//[end]
	//[start] protected abstract
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
	 * @param canvas
	 */
	protected abstract void drawComplete(Canvas canvas);
	//[end]
	//[start] class Viewport
	private class Viewport {
		/** The bitmap of the current viewport */
		Bitmap bitmap = null;
		/** A Rect that can be used for drawing. Same size as bitmap */
		final Rect identity = new Rect(0,0,0,0);
		/** A Rect that defines where the Viewport is within the scene */
		final Rect window = new Rect(0,0,0,0);
		/** The translation to apply before drawing the bitmap */
		Point translation = null;

		void setOrigin(int x, int y){
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
		
		void setSize( int w, int h ){
			synchronized (this) {
				if (bitmap!=null){
					bitmap.recycle();
					bitmap = null;
				}
				bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
				identity.set(0, 0, w, h);
				window.set(
						window.left,
						window.top,
						window.left + w,
						window.top + h);
			}
		}
		void getSize(Point p){
			synchronized (this) {
				p.x = identity.right;
				p.y = identity.bottom;
			}
		}
		void setTranslation(int x, int y){
			synchronized (this) {
				if (x==0 && y==0)
					translation = null;
				else
					translation = new Point(x,y);
			}
		}
		void getTranslation(Point p){
			synchronized (this) {
				p.set(translation.x,translation.y);
			}
		}
		void draw(Canvas c){
			cache.update(this);
			synchronized (this){
				if (c!=null && bitmap!=null){
					if (translation!=null)
						c.translate(translation.x, translation.y);
					Rect srcRect = null;
                    if (scaleFactor >= 1f) {
                        srcRect = new Rect(0, 0, (int) (identity.right / scaleFactor), (int) (identity.bottom / scaleFactor));
                    }
					c.drawBitmap( bitmap, srcRect, identity, null );
					if (translation!=null)
						c.translate(-translation.x, -translation.y);
					drawComplete(c);
				}
			}
		}
	}
	//[end]
	//[start] class Cache
	private enum CacheState {UNINITIALIZED,INITIALIZED,START_UPDATE,IN_UPDATE,READY,SUSPEND};
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
			if (DEBUG) Log.i(TAG,String.format("cacheState old=%s new=%s",state.toString(),newState.toString()));
			state = newState;
		}
		CacheState getState(){ return state; }
		
		/** Our load from disk thread */
		CacheThread cacheThread;
		
		void start(){
			if (cacheThread!=null)
				cacheThread.stop();
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
			Bitmap bitmap = null;	// If this is null at the bottom, then load from the sample
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
						if (DEBUG) Log.d(TAG,"bitmapRef is null");
						setState(CacheState.START_UPDATE);
						cacheThread.interrupt();
					} else if (!window.contains(viewport.window)){
						if (DEBUG) Log.d(TAG,"viewport not in cache");
						setState(CacheState.START_UPDATE);
						cacheThread.interrupt();
					} else {
						// Happy case -- the cache already contains the Viewport
						bitmap = bitmapRef;
					}
					break;
				}
			}
			// draw the sample if the cache is not ready or if the scale factor is below 1
			if (bitmap==null ||scaleFactor < 1f)
				loadSampleIntoViewport(viewport);
			else
				loadBitmapIntoViewport(bitmap, viewport);
		}
		
		void loadBitmapIntoViewport(Bitmap bitmap, Viewport viewport){
			if (bitmap!=null){
				synchronized(viewport){
					int left   = viewport.window.left - window.left;
					int top    = viewport.window.top  - window.top;
					int right  = left + viewport.window.width();
					int bottom = top  + viewport.window.height();
					if (scaleFactor < 1f) {
                        right = (int) (left + viewport.window.width() / scaleFactor);
                        bottom = (int) (top + viewport.window.height() / scaleFactor);
                    }
					srcRect.set( left, top, right, bottom );
					Canvas c = new Canvas(viewport.bitmap);
					c.drawBitmap(
							bitmap,
							srcRect,
							viewport.identity,
							null);
					
//					try {
//						FileOutputStream fos = new FileOutputStream("/sdcard/viewport.png");
//						viewport.bitmap.compress(Bitmap.CompressFormat.PNG, 99, fos);
//						Thread.sleep(1000);
//					} catch  (Exception e){
//						System.out.print(e.getMessage());
//					}
				}
			}
		}
		final Rect srcRect = new Rect(0,0,0,0);
		
		void loadSampleIntoViewport(Viewport viewport){
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
	//[end]
	//[start] class CacheThread
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
					} catch (InterruptedException e) {}
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
							synchronized (cache){
								if (cache.getState()==CacheState.IN_UPDATE){
									cache.bitmapRef = bitmap;
									cache.setState(CacheState.READY);
								} else {
									Log.w(TAG,"fillCache operation aborted");
								}
							}
				    		long done = System.currentTimeMillis();
				    		if (DEBUG) Log.d(TAG,String.format("fillCache in %dms",done-start)); 
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
	//[end]
}
