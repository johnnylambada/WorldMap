package com.sigseg.android.worldmap;

import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
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
	private final BitmapFactory.Options options = new BitmapFactory.Options();
	private int sceneWidth, sceneHeight;

	public final Viewport viewport;
	public final Cache cache;

	class Viewport {
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

			if (x + w > sceneWidth)
				x = sceneWidth - w;

			if (y + h > sceneHeight)
				y = sceneHeight - h;

			originRect.set(x, y, x+w, y+h);
		}
		
		void setSize( int w, int h ){
			if (bitmap!=null){
				bitmap.recycle();
				bitmap = null;
			}
			bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
			bitmapRect.set(0, 0, w, h);
			originRect.set(
					originRect.left,
					originRect.top,
					originRect.left + w,
					originRect.top + h);
		}
		
		void update(){
			cache.update(this);
		}
	}
	
	class Cache {
		/** A Rect that defines where the Cache is within the scene */
		final Rect originRect = new Rect(0,0,0,0);
		/** Used to calculate the Rect within the cache to copy from for the Viewport */
		final Rect srcRect = new Rect(0,0,0,0);
		/** The bitmap of the current cache */
		Bitmap bitmap = null;
		
		/** Fill the bitmap with the part of the scene referenced by the viewport Rect */
		void update(Viewport viewport){
			
			if (bitmap==null){
				// Start the cache off right
				if (DEBUG) Log.d(TAG,"decode first bitmap");
				setOriginRect(viewport.originRect);
				bitmap = decoder.decodeRegion( originRect, options );
			} else if (!originRect.contains(viewport.originRect)){
				// Have to refresh the Cache -- the Viewport isn't completely within the Cache
				if (DEBUG) Log.d(TAG,"viewport not in cache");
				setOriginRect(viewport.originRect);
				bitmap = decoder.decodeRegion( originRect, options );
			} else {
				// Happy case -- the cache already contains the Viewport
			}
			int left   = viewport.originRect.left - originRect.left;
			int top    = viewport.originRect.top  - originRect.top;
			int right  = left + viewport.originRect.width();
			int bottom = top  + viewport.originRect.height();
			srcRect.set( left, top, right, bottom );
			Canvas c = new Canvas(viewport.bitmap);
			c.drawBitmap(bitmap,
					srcRect,
					viewport.bitmapRect,
					null);
		}
		
		/** Figure out the originRect based on the viewportRect */
		private void setOriginRect(Rect viewportRect ){
			int vw = viewportRect.width();
			int vh = viewportRect.height();
			int mw = vw; // marginWidth
			int mh = vh; // marginHeight 
			
			if (vw+mw > sceneWidth)
				mw = Math.max(0, sceneWidth-vw);
			if (vh+mh > sceneHeight)
				mh = Math.max(0, sceneHeight-vh);
			
			int left = viewportRect.left - (mw>>1);
			int right = viewportRect.right + (mw>>1);
			if (left<0){
				right = right - left; // Add's the overage on the left side back to the right
				left = 0;
			}
			if (right>sceneWidth){
				left = left - (right-sceneWidth); // Adds overage on right side back to left
				right = sceneWidth;
			}

		
			int top = viewportRect.top - (mh>>1); 
			int bottom = viewportRect.bottom + (mh>>1);
			if (top<0){
				bottom = bottom - top; // Add's the overage on the top back to the bottom
				top = 0;
			}
			if (bottom>sceneHeight){
				top = top - (bottom-sceneHeight); // Adds overage on bottom back to top
				bottom = sceneHeight;
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
		options.inPreferredConfig = Bitmap.Config.RGB_565;
	}

	/**
	 * Set the Scene to the named asset
	 * @param context
	 * @param assetName
	 * @return
	 * @throws IOException
	 */
	public Scene setFile(Context context, String assetName) throws IOException {
		is = context.getAssets().open(assetName);
		decoder = BitmapRegionDecoder.newInstance(is, false);

		BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
		tmpOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(is, null, tmpOptions);

		sceneWidth = tmpOptions.outWidth;
		sceneHeight = tmpOptions.outHeight;
		if (DEBUG)
			Log.d(TAG, String.format("setFile() decoded width=%d height=%d",
					sceneWidth, sceneHeight));
		return this;
	}

	/**
	 * Update the scene to reflect any changes
	 */
	public void update(){
		viewport.update();
	}
}