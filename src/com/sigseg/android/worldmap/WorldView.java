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
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class WorldView extends View {
	private final static String TAG = "WorldView";
	private BitmapFactory.Options bitmapFactoryOptions = new BitmapFactory.Options();
	BitmapRegionDecoder decoder;
	final Point leftTop = new Point(0,0);
	final Point down = new Point(0,0);
	final Point leftTopAtDown = new Point(0,0);
	final Point size = new Point(0,0);
	
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
		bitmapFactoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
		InputStream is;
		try {
			is = context.getAssets().open("world.jpg");
			decoder = BitmapRegionDecoder.newInstance(is, false);
			bitmapFactoryOptions.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(is, null, bitmapFactoryOptions);
			size.x = bitmapFactoryOptions.outWidth;
			size.y = bitmapFactoryOptions.outHeight;
			Log.d(TAG, String.format("bitmap width=%d height=%d",size.x,size.y));
		} catch (IOException e) {
		}
		
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();
		int h = getHeight();
		Bitmap region = decoder.decodeRegion(
				new Rect(leftTop.x,leftTop.y,leftTop.x+w,leftTop.y+h),
				bitmapFactoryOptions
				);
		canvas.drawBitmap(
				region, 
				null,
				new Rect(0,0,w,h),
				null
				);
	}
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
        	down.x = (int) event.getX();
        	down.y = (int) event.getY();
        	leftTopAtDown.x = leftTop.x;
        	leftTopAtDown.y = leftTop.y;
            return true;
        case MotionEvent.ACTION_MOVE:
        	int deltaX = (int) (event.getX()-down.x);
        	int deltaY = (int) (event.getY()-down.y);
        	int newX = (int) (leftTopAtDown.x - deltaX);
        	int newY = (int) (leftTopAtDown.y - deltaY);
        	
        	// check bounds
        	if (newX<0)
        		newX=0;
        	if (newY<0)
        		newY=0;
        	if (newX + getWidth()>size.x)
        		newX = size.x - getWidth();
        	if (newY + getHeight()>size.y)
        		newY = size.y - getHeight();
        	
        	leftTop.x = newX;
        	leftTop.y = newY;
        	invalidate();
        	return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
        }
        return super.onTouchEvent(event);
    }


}
