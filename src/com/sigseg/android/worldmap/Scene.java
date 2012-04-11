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
 * |  |        displayBitmap   |            |                          |<br>
 * |  +------------------------+            |                          |<br>
 * |                                        |                          |<br>
 * |                                        |                          |<br>
 * |                                        |                          |<br>
 * |                          cacheBitmap   |                          |<br>
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
		/** The coordinates of the left,top of the Cache within the entire bitmap */
		private final Point origin = new Point(0,0);
		/** The bitmap of the current cache */
		Bitmap bitmap;
		
		/** Fill the bitmap with the part of the scene referenced by the viewport Rect */
		void update(Viewport viewport){
			bitmap = decoder.decodeRegion( viewport.originRect, options );
			Canvas c = new Canvas(viewport.bitmap);
			c.drawBitmap(bitmap,
					null,
					viewport.bitmapRect,
					null);
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

//
//	/**
//	 * Fill the displayBitmap with the cacheBitmap starting at x,y
//	 * @param x
//	 * @param y
//	 * @param displayBitmap
//	 */
//	public void getBitmap(Point point, Bitmap displayBitmap) {
//		if (DEBUG)
//			Log.d(TAG, "getBitmap() regionRect=" + cacheRect.toShortString());
//		if (cacheBitmap == null) {
//			Rect r = new Rect(cacheRect);
//			r.right = Math.min(r.right + r.width(), sceneWidth);
//			r.bottom = Math.min(r.bottom + r.height(), sceneHeight);
//			cacheBitmap = decoder.decodeRegion(r, options);
//		}
//		// bitmap = Bitmap.createBitmap(
//		// regionBitmap,
//		// regionRect.left, regionRect.top, regionRect.width(),
//		// regionRect.height());
//		Canvas canvas = new Canvas(displayBitmap);
//		int w = displayBitmap.getWidth();
//		int h = displayBitmap.getHeight();
//		Rect s = new Rect(point.x,point.y,point.x+w,point.y+h);
//		Rect d = new Rect(0,0,w,h);
//		canvas.drawBitmap(
//				cacheBitmap,
//				s, 
//				d, 
//				null);
//	}
}