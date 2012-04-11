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
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class WorldView extends View {
	private final static String TAG = "WorldView";
	private final boolean DEBUG = true;

	private final Point down = new Point(0,0);
	private final Point leftTopAtDown = new Point(0,0);
	private long startTime=0;
	private final Background background = new Background();
	
	private Bitmap screenBitmap = null;
	
	class Background {
		private final String TAG = "Background";
		private final boolean DEBUG = true;
		
		private InputStream is;
		private Bitmap regionBitmap;
		private BitmapRegionDecoder decoder;
		private final BitmapFactory.Options options = new BitmapFactory.Options();
		private int imageWidth, imageHeight;
		private final Rect regionRect = new Rect();
		
		public Background(){
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		}
		
		public Background setFile(Context context, String assetName ) throws IOException{
			is = context.getAssets().open(assetName);
			decoder = BitmapRegionDecoder.newInstance(is, false);

			BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
			tmpOptions.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(is, null, tmpOptions);
			
			imageWidth = tmpOptions.outWidth;
			imageHeight = tmpOptions.outHeight;
			if (DEBUG) Log.d(TAG, String.format("setFile() decoded width=%d height=%d",imageWidth, imageHeight));
			return this;
		}
		
		public int getImageWidth(){return imageWidth;}
		public int getImageHeight(){return imageHeight;}
		
		public Background setRegionSize( int width, int height ){
			regionRect.set(0, 0, width, height);
			return this;
		}
		
		public Background setRegionXY(int newX, int newY){
			int w = regionRect.width();
			int h = regionRect.height();

        	// check bounds
        	if (newX<0)		
        		newX=0;
        	
        	if (newY<0)		
        		newY=0;
        	
        	if (newX + w > imageWidth)
        		newX = imageWidth - w;
        	
        	if (newY + h > imageHeight)
        		newY = imageHeight - h;
        	
			regionRect.set(newX, newY, newX+w, newY+h);
			return this;
		}
		
//		public Bitmap getBitmap(Bitmap screenBitmap){
//			if (DEBUG) Log.d(TAG,"getBitmap() regionRect="+regionRect.toShortString());
//			regionBitmap = decoder.decodeRegion( regionRect, options );
//			return regionBitmap;
//		}

		public Bitmap getBitmap(Bitmap screenBitmap){
			if (DEBUG) Log.d(TAG,"getBitmap() regionRect="+regionRect.toShortString());
			Bitmap bitmap;
			if (regionBitmap==null){
				Rect r = new Rect(regionRect);
				r.right = Math.min(r.right + r.width(), imageWidth);
				r.bottom = Math.min(r.bottom + r.height(), imageHeight);
				regionBitmap = decoder.decodeRegion( r, options );
			}
//			bitmap = Bitmap.createBitmap(
//					regionBitmap,
//					regionRect.left, regionRect.top, regionRect.width(), regionRect.height());
			Canvas screenBitmapCanvas = new Canvas(screenBitmap);
			screenBitmapCanvas.drawBitmap(
					regionBitmap,
					regionRect,
					new Rect(0, 0, regionRect.width(), regionRect.height()),
					null);
			return screenBitmap;
		}

		
		
		public int getRegionX() {
			return regionRect.left;
		}

		public int getRegionY() {
			return regionRect.top;
		}
	}
	
	public WorldView(Context context) {
		super(context);
		init(context);
	}
	
	public WorldView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	public WorldView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}
	
	private void init(Context context){
		try {
			background.setFile(context, "world.jpg");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		background.setRegionSize(w,h);
		screenBitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
		Log.d(TAG,String.format("onSizeChanged(w=%d,h=%d,oldw=%d,oldh=%d",w,h,oldw,oldh));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();
		int h = getHeight();
		Bitmap region = background.getBitmap(screenBitmap);
		canvas.drawBitmap(
				region, 
				null,
				new Rect(0,0,w,h),
				null
				);
    	if (DEBUG){
    		long now = System.currentTimeMillis();
			double n = ((double)now)/1000L;
			double s = ((double)startTime)/1000L;
			double fps = 1L/(n-s);
			Log.d(TAG,String.format("msec=%d FPS=%.2f",now-startTime,fps));
			startTime = System.currentTimeMillis();
    	}
	}
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
        	down.x = (int) event.getX();
        	down.y = (int) event.getY();
        	leftTopAtDown.set(background.getRegionX(), background.getRegionY());
            return true;
        case MotionEvent.ACTION_MOVE:
        	int deltaX = (int) (event.getX()-down.x);
        	int deltaY = (int) (event.getY()-down.y);
        	int newX = (int) (leftTopAtDown.x - deltaX);
        	int newY = (int) (leftTopAtDown.y - deltaY);
        	background.setRegionXY(newX, newY);
        	invalidate();
        	return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
        }
        return super.onTouchEvent(event);
    }


}
