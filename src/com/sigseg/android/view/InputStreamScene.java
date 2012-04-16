package com.sigseg.android.view;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;

public class InputStreamScene extends Scene {
//	private static final String TAG="FileBackedScene";
	
	private final BitmapFactory.Options options = new BitmapFactory.Options();
	public BitmapRegionDecoder decoder;
	
	public InputStreamScene(){
		options.inPreferredConfig = Bitmap.Config.RGB_565;
	}
	
	
	/**
	 * Set the Scene to the named asset
	 * @param context
	 * @param assetName
	 * @throws IOException
	 */
	public void setInputStream(InputStream inputStream) throws IOException {
		Point sceneDimensions = new Point(0,0);
		BitmapFactory.Options tmpOptions = new BitmapFactory.Options();

		synchronized(cache){
			this.decoder = BitmapRegionDecoder.newInstance(inputStream, false);

			// Grab the bounds for the return value
			tmpOptions.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(inputStream, null, tmpOptions);
			sceneDimensions.set(tmpOptions.outWidth, tmpOptions.outHeight);
			
			// Create the sample image
			tmpOptions.inJustDecodeBounds = false;
			tmpOptions.inSampleSize = (1<<cache.downShift);
			cache.sampleBitmap = BitmapFactory.decodeStream(inputStream, null, tmpOptions);
			
			cache.state = CacheState.INITIALIZED;
		}
		width = sceneDimensions.x;
		height = sceneDimensions.y;
//		Log.d(TAG, String.format("setFile() decoded width=%d height=%d", width, height));
	}


	@Override
	public Bitmap fillCache(Rect origin) {
		Bitmap bitmap = decoder.decodeRegion( origin, options );
		return bitmap;
	}
}
