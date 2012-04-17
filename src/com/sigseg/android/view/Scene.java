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
	private final boolean DEBUG = true;

	/** The size of the Scene */
	private Point size = new Point();
	/** The viewport */
	private final Viewport viewport = new Viewport();
	/** The cache */
	private final Cache cache = new Cache();

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
	//[start] [gs]etViewport(Origin|Size)
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
	//[end]
	//[start] initialize/start/stop/suspend the cache
	/** Initializes the cache */
	public void initialize(){
		synchronized(cache){
			cache.state = CacheState.INITIALIZED;
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
				cache.state = CacheState.SUSPEND;
			}
		} else {
			if (cache.state==CacheState.SUSPEND) {
				synchronized(cache){
					cache.state = CacheState.INITIALIZED;
				}
			}
		}
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
//	protected abstract Rect calculateCacheWindow(Rect viewportRect);
	/**
	 * This method fills the passed-in bitmap with sample data. This function must 
	 * return as fast as possible so it shouldn't have to do any IO at all -- the
	 * quality of the user experience rests on the speed of this function.
	 * @param bitmap The Bitmap to fill
	 * @param rectOfSample Rectangle within the Scene that this bitmap represents.
	 */
	protected abstract void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample);
	//[end]
	//[start] class Viewport
	private class Viewport {
		/** The bitmap of the current viewport */
		Bitmap bitmap = null;
		/** A Rect that can be used for drawing. Same size as bitmap */
		final Rect identity = new Rect(0,0,0,0);
		/** A Rect that defines where the Viewport is within the scene */
		final Rect window = new Rect(0,0,0,0);

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
		void draw(Canvas c){
			cache.update(this);
			synchronized (this){
				if (bitmap!=null)
					c.drawBitmap( bitmap, null, identity, null );
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
		/** What percent of total memory should we use for the cache? The bigger the cache,
		 * the longer it takes to read -- 1.2 secs for 25%, 600ms for 10%, 500ms for 5%.
		 * User experience seems to be best for smaller values. 
		 */
		int percent = 5; // Above 25 and we get OOMs
		/** How many bytes does one pixel use? */
		final int BYTES_PER_PIXEL = 2;
		/** A Rect that defines where the Cache is within the scene */
		final Rect window = new Rect(0,0,0,0);
		/** The bitmap of the current cache */
		Bitmap bitmapRef = null;
		CacheState state = CacheState.UNINITIALIZED;

		
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
		
		/** Fill the bitmap with the part of the scene referenced by the viewport Rect */
		void update(Viewport viewport){
			Bitmap bitmap = null;	// If this is null at the bottom, then load from the sample
			synchronized(this){
				switch(state){
				case UNINITIALIZED:
					// nothing can be done -- should never get here
					return;
				case INITIALIZED:
					// time to cache some data
					state = CacheState.START_UPDATE;
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
						state = CacheState.START_UPDATE;
						cacheThread.interrupt();
					} else if (!window.contains(viewport.window)){
						if (DEBUG) Log.d(TAG,"viewport not in cache");
						state = CacheState.START_UPDATE;
						cacheThread.interrupt();
					} else {
						// Happy case -- the cache already contains the Viewport
						bitmap = bitmapRef;
					}
					break;
				}
			}
			if (bitmap==null)
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
					srcRect.set( left, top, right, bottom );
					Canvas c = new Canvas(viewport.bitmap);
					c.drawBitmap(
							bitmap,
							srcRect,
							viewport.identity,
							null);
				}
			}
		}
		final Rect srcRect = new Rect(0,0,0,0);
		
		void loadSampleIntoViewport(Viewport viewport){
			if (state!=CacheState.UNINITIALIZED){
				synchronized(viewport){
					drawSampleRectIntoBitmap(
						viewport.bitmap,
						viewport.window
						);
				}
			}
		}
		
		/** Figure out the originRect based on the viewportRect */
		void setOriginRect(Rect viewportRect ){
			long bytesToUse = Runtime.getRuntime().maxMemory() * percent / 100;

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
			window.set(left, top, right, bottom);
			if (DEBUG) Log.d(TAG,"new cache.originRect = "+window.toShortString()+" size="+size.toString()); 
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
				while(cache.state!=CacheState.START_UPDATE)
					try {
						// Sleep until we have something to do
						Thread.sleep(Integer.MAX_VALUE);
					} catch (InterruptedException e) {}
				if (!running)
					return;
	    		long start = System.currentTimeMillis();
				boolean cont = false;
				synchronized (cache) {
					if (cache.state==CacheState.START_UPDATE){
						cache.state = CacheState.IN_UPDATE;
						cache.bitmapRef = null;
						cont = true;
					}
				}
				if (cont){
					synchronized(viewport){
						viewportRect.set(viewport.window);
					}
					synchronized (cache) {
						if (cache.state==CacheState.IN_UPDATE)
							cache.setOriginRect(viewportRect);
						else
							cont = false;
					}
					if (cont){
						try{
							Bitmap bitmap = fillCache(cache.window);
							synchronized (cache){
								if (cache.state==CacheState.IN_UPDATE){
									cache.bitmapRef = bitmap;
									cache.state = CacheState.READY;
								} else {
									Log.w(TAG,"fillCache aborted");
								}
							}
				    		long done = System.currentTimeMillis();
				    		if (DEBUG) Log.d(TAG,String.format("fillCache in %dms",done-start)); 
						} catch (OutOfMemoryError e){
							/*
							 *  This is a feeble attempt to recover. Experience shows that if we
							 *  do get an OutOfMemoryError, we're pretty hosed and are going down.
							 *  For instance, if we're trying to decode a bitmap region with
							 *  BitmapRegionDecoder and we run out of memory, we're going to die
							 *  somewhere in the C code with a SIGSEGV. 
							 */
							synchronized (cache){
								if (cache.percent>0)
									cache.percent -= 1;
								if (cache.state==CacheState.IN_UPDATE){
									cache.state = CacheState.START_UPDATE;
								}
							}
							Log.e(TAG,String.format("caught oom -- cache now at %d percent.",cache.percent));
						}
					}
				}
			}
		}
	}
	//[end]
}