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
 * 
 */
public abstract class Scene {
	private final String TAG = "Scene";
	private final boolean DEBUG = true;
	
	private enum CacheState {UNINITIALIZED,INITIALIZED,START_UPDATE,IN_UPDATE,READY,SUSPEND};

	protected int width, height;

	private final Viewport viewport;
	private final Cache cache;

	//[start] constructor
	/**
	 * Create a new Scene. Inexpensive operation.
	 */
	public Scene() {
		cache = new Cache();
		viewport = new Viewport();
	}
	//[end]
	//[start] public

	/**
	 * Get the origin
	 * @return the Point of the origin
	 */
	public Point getOrigin(){
		Point p = new Point();
		synchronized(viewport){
			p.set(viewport.origin.left, viewport.origin.top);
		}
		return p;
	}
	
	public int getHeight(){return height;}
	public int getWidth(){return width;}
	
	public void getOrigin(Point p){
		synchronized(viewport){
			p.set(viewport.origin.left, viewport.origin.top);
		}
	}
	
	public void start(){
		cache.start();
	}
	
	public void stop(){
		cache.stop();
	}
	
	public void initialize(){
		synchronized(cache){
			cache.state = CacheState.INITIALIZED;
		}
	}
	
	public void setSuspend(boolean suspend){
		synchronized(cache){
			if (suspend)
				cache.state = CacheState.SUSPEND;
			else
				cache.state = CacheState.INITIALIZED;
		}
	}
	
	/** Set the Origin */
	public void setOrigin(int x, int y){
		viewport.setOrigin(x, y);
	}
	
	/** Set the size of the view within the scene */
	public void setViewSize(int width, int height){
		viewport.setSize(width, height);
	}
	
	/** Return a Point with the x value set to the viewport width, y set to height */
	public void getViewSize(Point p){
		viewport.getSize(p);
	}
	
	/** Draw the scene to the canvas. This op fills the canvas */
	public void draw(Canvas c){
		viewport.draw(c);
	}
	//[end]
	//[start] protected abstract
	protected abstract Bitmap fillCache(Rect origin);
	protected abstract void drawSampleIntoBitmapAtPoint(Bitmap bitmap, Point point);
	//[end]
	//[start] class Viewport
	private class Viewport {
		/** Is the viewport ready to be used? */
		boolean ready = false;
		/** The bitmap of the current viewport */
		Bitmap bitmap = null;
		/** A Rect that can be used for drawing. Same size as bitmap */
		final Rect identity = new Rect(0,0,0,0);
		/** A Rect that defines where the Viewport is within the scene */
		final Rect origin = new Rect(0,0,0,0);

		void setOrigin(int x, int y){
			synchronized(this){
				int w = origin.width();
				int h = origin.height();
	
				// check bounds
				if (x < 0)
					x = 0;
	
				if (y < 0)
					y = 0;
	
				if (x + w > width)
					x = width - w;
	
				if (y + h > height)
					y = height - h;
	
				origin.set(x, y, x+w, y+h);
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
				origin.set(
						origin.left,
						origin.top,
						origin.left + w,
						origin.top + h);
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
	/**
	 * Keep track of the cached bitmap
	 */
	private class Cache {
		/** What percent of total memory should we use for the cache? The bigger the cache,
		 * the longer it takes to read -- 1.2 secs for 25%, 600ms for 10%, 500ms for 5%.
		 * User experience seems to be best for smaller values. 
		 */
		int percent = 5; // Above 25 and we get OOMs
		/** A Rect that defines where the Cache is within the scene 1=1/2, 2=1/4 3=1/8, etc */
		final Rect origin = new Rect(0,0,0,0);
		/** Used to calculate the Rect within the cache to copy from for the Viewport */
		final Rect srcRect = new Rect(0,0,0,0);
		/** The bitmap of the current cache */
		Bitmap bitmapRef = null;
		CacheState state = CacheState.UNINITIALIZED;
		Point margin = new Point(0,0);

		
		/** Our load from disk thread */
		CacheThread cacheThread;
		
		void start(){
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
			boolean loadSample = true;
			Bitmap bitmap = null;
			synchronized(this){
				switch(state){
				case UNINITIALIZED:
					// nothing can be done -- should never get here
					return;
				case INITIALIZED:
					// time to cache some data
					loadSample=true;
					state = CacheState.START_UPDATE;
					cacheThread.interrupt();
					break;
				case START_UPDATE:
					// I already told the thread to start
					loadSample=true;
					break;
				case IN_UPDATE:
					// Already reading some data, just use the sample 
					loadSample=true;
					break;
				case READY:
					// I have some data to show
					if (bitmapRef==null){
						// Start the cache off right
						if (DEBUG) Log.d(TAG,"bitmapRef is null");
						loadSample=true;
						state = CacheState.START_UPDATE;
						cacheThread.interrupt();
					} else if (!origin.contains(viewport.origin)){
						if (DEBUG) Log.d(TAG,"viewport not in cache");
						loadSample=true;
						state = CacheState.START_UPDATE;
						cacheThread.interrupt();
					} else {
						// Happy case -- the cache already contains the Viewport
						bitmap = bitmapRef;
						loadSample = false;
					}
					break;
				}
			}
			if (loadSample)
				loadSampleIntoViewport(viewport);
			else
				loadBitmapIntoViewport(bitmap, viewport);
		}
		
		void loadBitmapIntoViewport(Bitmap bitmap, Viewport viewport){
			if (bitmap!=null){
				synchronized(viewport){
					int left   = viewport.origin.left - origin.left;
					int top    = viewport.origin.top  - origin.top;
					int right  = left + viewport.origin.width();
					int bottom = top  + viewport.origin.height();
					srcRect.set( left, top, right, bottom );
					Canvas c = new Canvas(viewport.bitmap);
					c.drawBitmap(bitmap,
							srcRect,
							viewport.identity,
							null);
				}
			}
		}
		
		void loadSampleIntoViewport(Viewport viewport){
			if (state!=CacheState.UNINITIALIZED){
				synchronized(viewport){
					drawSampleIntoBitmapAtPoint(
						viewport.bitmap, 
						new Point(viewport.origin.left,viewport.origin.top)
						);
				}
			}
		}
		
		void calcMargin(int width, int height){
			long bytesToUse = Runtime.getRuntime().maxMemory() * percent / 100;
			
			int mwidth = 0;
			int mheight= 0;
			int pwidth = mwidth;
			int pheight = mheight;
			while((width+mwidth) * (height+mheight) * 2 < bytesToUse){
				pwidth = mwidth++;
				pheight = mheight++;
			}
			margin.set(pwidth, pheight);
		}
		
		/** Figure out the originRect based on the viewportRect */
		void setOriginRect(Rect viewportRect ){
			int vw = viewportRect.width();
			int vh = viewportRect.height();
			calcMargin(vw,vh);
			int mw = margin.x; 
			int mh = margin.y;
			
			if (vw+mw > width)
				mw = Math.max(0, width-vw);
			if (vh+mh > height)
				mh = Math.max(0, height-vh);
			
			int left = viewportRect.left - (mw>>1);
			int right = viewportRect.right + (mw>>1);
			if (left<0){
				right = right - left; // Add's the overage on the left side back to the right
				left = 0;
			}
			if (right>width){
				left = left - (right-width); // Adds overage on right side back to left
				right = width;
			}

		
			int top = viewportRect.top - (mh>>1); 
			int bottom = viewportRect.bottom + (mh>>1);
			if (top<0){
				bottom = bottom - top; // Add's the overage on the top back to the bottom
				top = 0;
			}
			if (bottom>height){
				top = top - (bottom-height); // Adds overage on bottom back to top
				bottom = height;
			}
			origin.set(left, top, right, bottom);
			if (DEBUG) Log.d(TAG,"new cache.originRect = "+origin.toShortString()); 
		}
	}
	//[end]
	//[start] class CacheThread
	/**
	 * The CahceThread's job is to wait until the cahce.state is START_UPDATE and then
	 * update the cache given the current viewport origin. It does not want to hold
	 * the cache lock during the call to cache.decoder.decodeRegion because the call
	 * can take over 1 second. If we hold the lock, the user experience is very
	 * jumpy.
	 */
	class CacheThread extends Thread {
		final Cache cache;
	    boolean running = false;
	    void setRunning(boolean value){ 
	    	running = value; 
	    }
	    
		CacheThread(Cache cache){
			this.cache = cache;
		}
		@Override
		public void run() {
			running=true;
			Rect viewportRect = new Rect(0,0,0,0);
			while(running){
				while(cache.state!=CacheState.START_UPDATE)
					try {
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
						viewportRect.set(viewport.origin);
					}
					cache.setOriginRect(viewportRect);
					try{
						Bitmap bitmap = fillCache(cache.origin);
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
						if (cache.percent>0)
							cache.percent -= 1;
						synchronized (cache){
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
	//[end]
}