package com.sigseg.android.worldmap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

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

	private InputStream is;
	private BitmapRegionDecoder decoder;
	private int width, height;

	private final Viewport viewport;
	private final Cache cache;

	class Viewport {
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
	
	class Cache {
		/** What percent of total memory should we use for the cache? */
		int percent = 25; // Above this and we get OOMs
		/** A Rect that defines where the Cache is within the scene */
		final Rect origin = new Rect(0,0,0,0);
		/** Used to calculate the Rect within the cache to copy from for the Viewport */
		final Rect srcRect = new Rect(0,0,0,0);
		/** The bitmap of the current cache */
		WeakReference<Bitmap> bitmapRef = null;
		private final BitmapFactory.Options options = new BitmapFactory.Options();
		
		public Cache(){
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		}
		
		/** Fill the bitmap with the part of the scene referenced by the viewport Rect */
		void update(Viewport viewport){
			Bitmap bitmap;
			if (bitmapRef==null || bitmapRef.get()==null){
				// Start the cache off right
				if (DEBUG) Log.d(TAG,"decode first bitmap");
				setOriginRect(viewport.origin);
				bitmap = decoder.decodeRegion( origin, options );
				bitmapRef = new WeakReference<Bitmap>(bitmap);
			} else if (!origin.contains(viewport.origin)){
				// Have to refresh the Cache -- the Viewport isn't completely within the Cache
				if (DEBUG) Log.d(TAG,"viewport not in cache");
				setOriginRect(viewport.origin);
				bitmap = decoder.decodeRegion( origin, options );
				bitmapRef = new WeakReference<Bitmap>(bitmap);
			} else {
				// Happy case -- the cache already contains the Viewport
				bitmap = bitmapRef.get();
			}
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
		is = context.getAssets().open(assetName);
		decoder = BitmapRegionDecoder.newInstance(is, false);

		BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
		tmpOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(is, null, tmpOptions);

		width = tmpOptions.outWidth;
		height = tmpOptions.outHeight;
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