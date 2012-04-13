package com.sigseg.android.worldmap;

import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

/**
 * <code>
 * +-------------------------------------------------------------------+<br>
 * |                                        |                          |<br>
 * |  +------------------------+            |                          |<br>
 * |  |                        |            |                          |<br>
 * |  |                        |            |                          |<br>
 * |  |                        |            |                          |<br>
 * |  |           Viewport     |            |                          |<br>
 * |  +------------------------+            |                          |<br>
 * |                                        |                          |<br>
 * |                                        |                          |<br>
 * |                                        |                          |<br>
 * |                          Cache         |                          |<br>
 * |----------------------------------------+                          |<br>
 * |                                                                   |<br>
 * |                                                                   |<br>
 * |                                                                   |<br>
 * |                                                                   |<br>
 * |                                                                   |<br>
 * |                               Entire bitmap -- too big for memory |<br>
 * +-------------------------------------------------------------------+<br>
 * </code>
 */
class Scene {
	private final String TAG = "Scene";
	private final boolean DEBUG = true;
	
	private enum CacheState {UNINITIALIZED,INITIALIZED,START_UPDATE,IN_UPDATE,READY};

	private int width, height;

	private final Viewport viewport;
	private final Cache cache;

	class Viewport {
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
		
		void update(){
			cache.update(this);
		}
		
		void draw(Canvas c){
			synchronized (this){
				if (bitmap!=null)
					c.drawBitmap( bitmap, null, identity, null );
			}
		}
	}
	
	/**
	 * The CahceThread's job is to wait until the cahce.state is START_UPDATE and then
	 * update the cache given the current viewport origin. It does not want to hold
	 * the cache lock during the call to cache.decoder.decodeRegion because the call
	 * can take over 1 second. If we hold the lock, the user experience is very
	 * jumpy.
	 */
	class CacheThread extends Thread {
		final Cache cache;
	    private boolean running = false;
	    public void setRunning(boolean value){ 
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
						Thread.sleep(5);
						if (!running)
							return;
					} catch (InterruptedException e) {}
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
						Bitmap bitmap = cache.decoder.decodeRegion( cache.origin, cache.options );
						cache.bitmapRef = new NotWeakReference<Bitmap>(bitmap);
						cache.state = CacheState.READY;
			    		long done = System.currentTimeMillis();
			    		if (DEBUG) Log.d(TAG,String.format("decoderegion in %dms",done-start)); 
					} catch (OutOfMemoryError e){
						if (cache.percent>0)
							cache.percent -= 1;
						cache.state = CacheState.START_UPDATE;
						Log.e(TAG,String.format("caught oom -- cache now at %d percent.",cache.percent));
					}
				}
			}
		}
	}
	
	/**
	 * Substitute for WeakReference<T> that is not weak.
	 * @param <T>
	 */
	class NotWeakReference<T> {
		T referent;
		public NotWeakReference(T referent) {
			this.referent = referent;
		}
		public T get(){
			return referent;
		}
	}
	
	/**
	 * Keep track of the cached bitmap
	 */
	class Cache {
		/** What percent of total memory should we use for the cache? The bigger the cache,
		 * the longer it takes to read -- 1.2 secs for 25%, 600ms for 10%, 500ms for 5%.
		 * User experience seems to be best for smaller values. 
		 */
		int percent = 5; // Above 25 and we get OOMs
		/** What is the downsample size for the sample image? */
		final int downShift = 3;
		/** A Rect that defines where the Cache is within the scene 1=1/2, 2=1/4 3=1/8, etc */
		final Rect origin = new Rect(0,0,0,0);
		/** Used to calculate the Rect within the cache to copy from for the Viewport */
		final Rect srcRect = new Rect(0,0,0,0);
		/** The bitmap of the current cache */
		NotWeakReference<Bitmap> bitmapRef = null;
		CacheState state = CacheState.UNINITIALIZED;
		
		/** Our load from disk thread */
		CacheThread cacheThread;
		
		private Bitmap sampleBitmap;

		private final BitmapFactory.Options options = new BitmapFactory.Options();
		private InputStream is;
		private BitmapRegionDecoder decoder;
		
		public Cache(){
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		}
		
		public Point setFile(Context context, String assetName) throws IOException {
			Point sceneDimensions = new Point(0,0);
			BitmapFactory.Options tmpOptions = new BitmapFactory.Options();

			synchronized(this){
				is = context.getAssets().open(assetName);
				decoder = BitmapRegionDecoder.newInstance(is, false);

				// Grab the bounds for the return value
				tmpOptions.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(is, null, tmpOptions);
				sceneDimensions.set(tmpOptions.outWidth, tmpOptions.outHeight);
				
				// Create the sample image
				tmpOptions.inJustDecodeBounds = false;
				tmpOptions.inSampleSize = (1<<downShift);
				sampleBitmap = BitmapFactory.decodeStream(is, null, tmpOptions);
				
				state = CacheState.INITIALIZED;
			}
			return sceneDimensions;
		}
		
		public void start(){
			cacheThread = new CacheThread(this);
			cacheThread.setName("cacheThread");
			cacheThread.start();
		}
		
		public void stop(){
			cacheThread.running = false;
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
					if (bitmapRef==null || bitmapRef.get()==null){
						// Start the cache off right
						if (DEBUG) Log.d(TAG,"my bitmapRef disappeared");
						loadSample=true;
						state = CacheState.START_UPDATE;
					} else if (!origin.contains(viewport.origin)){
						if (DEBUG) Log.d(TAG,"viewport not in cache");
						loadSample=true;
						state = CacheState.START_UPDATE;
					} else {
						// Happy case -- the cache already contains the Viewport
						bitmap = bitmapRef.get();
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
					Canvas c = new Canvas(viewport.bitmap);
					int left   = (viewport.origin.left>>downShift);
					int top    = (viewport.origin.top>>downShift);
					int right  = left + (viewport.origin.width()>>downShift);
					int bottom = top + (viewport.origin.height()>>downShift);
					srcRect.set( left, top, right, bottom );
					c.drawBitmap(
						sampleBitmap,
						srcRect,
						viewport.identity,
						null
						);
				}
			}
		}
		
		Point margin = new Point(0,0);
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
			if (Application.DEBUG)
				Log.d(TAG,String.format("margin set to w=%d h=%d for %d bytes",margin.x,margin.y,bytesToUse));
		}
		
		/** Figure out the originRect based on the viewportRect */
		private void setOriginRect(Rect viewportRect ){
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

	/**
	 * Create a new BigBitmap. Inexpensive operation.
	 */
	public Scene() {
		cache = new Cache();
		viewport = new Viewport();
	}

	/**
	 * Set the Scene to the named asset
	 * @param context
	 * @param assetName
	 * @throws IOException
	 */
	public void setFile(Context context, String assetName) throws IOException {

		Point p = cache.setFile(context, assetName);
		width = p.x;
		height = p.y;

		if (DEBUG)
			Log.d(TAG, String.format("setFile() decoded width=%d height=%d",
					width, height));
	}

	/**
	 * Update the scene to reflect any changes
	 */
	public void update(){
		viewport.update();
	}
	
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
	
	public void start(){
		cache.start();
	}
	
	public void stop(){
		cache.stop();
	}
	
	/** Set the Origin */
	public void setOrigin(int x, int y){
		viewport.setOrigin(x, y);
	}
	
	/** Set the size of the view within the scene */
	public void setViewSize(int width, int height){
		viewport.setSize(width, height);
	}
	
	/** Draw the scene to the canvas. This op fills the canvas */
	public void draw(Canvas c){
		viewport.draw(c);
	}
}