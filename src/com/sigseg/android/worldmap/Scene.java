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
import android.graphics.Matrix;
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

	private int width, height;

	public final Viewport viewport;
	public final Cache cache;

	class Viewport {
		/** Is the viewport ready to be used? */
		boolean ready = false;
		/** The bitmap of the current viewport */
		Bitmap bitmap = null;
		/** A Rect that can be used for drawing. Same size as bitmap */
		final Rect bitmapRect = new Rect(0,0,0,0);
		/** A Rect that defines where the Viewport is within the scene */
		final Rect originRect = new Rect(0,0,0,0);
		int getOriginX(){return originRect.left;}
		int getOriginY(){return originRect.top;}

		void setOrigin(int x, int y){
			int w = originRect.width();
			int h = originRect.height();

			// check bounds
			if (x < 0)
				x = 0;

			if (y < 0)
				y = 0;

			if (x + w > width)
				x = width - w;

			if (y + h > height)
				y = height - h;

			originRect.set(x, y, x+w, y+h);
		}
		
		void setSize( int w, int h ){
			if (bitmap==null){
				// first time
				bitmapRect.set(0, 0, w, h);
				originRect.set( originRect.left, originRect.top, originRect.left + w, originRect.top + h);
				bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
				ready = true;
			} else {
				synchronized (bitmap) {
					bitmap.recycle();
					bitmapRect.set(0, 0, w, h);
					originRect.set( originRect.left, originRect.top, originRect.left + w, originRect.top + h);
					bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
				}
			}
		}
		
		void update(){
			cache.update(this);
		}
	}
	
	class Cache {
		/** What percent of total memory should we use for the cache? */
		final int percent = 25; // Above this and we get OOMs
		/** What is the downsample size for the sample image? */
		final int downShift = 3;
		/** A Rect that defines where the Cache is within the scene */
		final Rect originRect = new Rect(0,0,0,0);
		/** Used to calculate the Rect within the cache to copy from for the Viewport */
		final Rect srcRect = new Rect(0,0,0,0);
		/** The bitmap of the current cache */
		WeakReference<Bitmap> bitmapRef = null;
		
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

			return sceneDimensions;
		}
		
		/** Fill the bitmap with the part of the scene referenced by the viewport Rect */
		void update(Viewport viewport){
			synchronized(viewport.bitmap){
//			Bitmap bitmap;
//			if (bitmapRef==null || bitmapRef.get()==null){
//				// Start the cache off right
//				if (DEBUG) Log.d(TAG,"decode first bitmap");
//				setOriginRect(viewport.originRect);
//				bitmap = decoder.decodeRegion( originRect, options );
//				bitmapRef = new WeakReference<Bitmap>(bitmap);
//			} else if (!originRect.contains(viewport.originRect)){
//				// Have to refresh the Cache -- the Viewport isn't completely within the Cache
//				if (DEBUG) Log.d(TAG,"viewport not in cache");
//				setOriginRect(viewport.originRect);
//				bitmap = decoder.decodeRegion( originRect, options );
//				bitmapRef = new WeakReference<Bitmap>(bitmap);
//			} else {
//				// Happy case -- the cache already contains the Viewport
//				bitmap = bitmapRef.get();
//			}
//			int left   = viewport.originRect.left - originRect.left;
//			int top    = viewport.originRect.top  - originRect.top;
//			int right  = left + viewport.originRect.width();
//			int bottom = top  + viewport.originRect.height();
//			srcRect.set( left, top, right, bottom );
//			Canvas c = new Canvas(viewport.bitmap);
//			c.drawBitmap(bitmap,
//					srcRect,
//					viewport.bitmapRect,
//					null);
				loadSampleIntoViewport(viewport);
			}
		}
		
		void loadSampleIntoViewport(Viewport viewport){
			Canvas c = new Canvas(viewport.bitmap);
			int left   = (viewport.originRect.left>>downShift);
			int top    = (viewport.originRect.top>>downShift);
			int right  = left + (viewport.originRect.width()>>downShift);
			int bottom = top + (viewport.originRect.height()>>downShift);
			srcRect.set( left, top, right, bottom );
			c.drawBitmap(
				sampleBitmap,
				srcRect,
				viewport.bitmapRect,
				null
				);
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
			originRect.set(left, top, right, bottom);
			if (DEBUG) Log.d(TAG,"new cache.originRect = "+originRect.toShortString()); 
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
}