																																																																																																																																																																				package com.sigseg.android.worldmap;

import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
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

	private long startTime=0;
	private final Scene scene = new Scene();
	private final Touch touch = new Touch();
	
	class Touch {
		/** Are we the middle of a touch event? */
		boolean inTouch = false;
		/** Where on the view did we initially touch */
		final Point viewDown = new Point(0,0);
		/** What was the coordinates of the viewport origin? */
		final Point viewportOriginAtDown = new Point(0,0); 
		
		void down(MotionEvent event){
        	inTouch = true;
        	viewDown.x = (int) event.getX();
        	viewDown.y = (int) event.getY();
        	viewportOriginAtDown.set(
        			scene.viewport.getOriginX(),
        			scene.viewport.getOriginY()
        			);
		}
		
		void move(MotionEvent event){
			if (inTouch){
	        	int deltaX = (int) (event.getX()-viewDown.x);
	        	int deltaY = (int) (event.getY()-viewDown.y);
	        	int newX = (int) (viewportOriginAtDown.x - deltaX);
	        	int newY = (int) (viewportOriginAtDown.y - deltaY);
	        	
	        	scene.viewport.setOrigin(newX, newY);
			}
		}
		
		void up(MotionEvent event){
			if (inTouch){
				inTouch = false;
			}
		}
		
		void cancel(MotionEvent event){
			if (inTouch){
				inTouch = false;
			}
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
			scene.setFile(context, "world.jpg");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		scene.viewport.setSize(w, h);
		if (DEBUG)
			Log.d(TAG,String.format("onSizeChanged(w=%d,h=%d,oldw=%d,oldh=%d",w,h,oldw,oldh));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();
		int h = getHeight();
		
		// tell the scene to update it's viewport bitmap
		scene.update();
		
		// draw it
		canvas.drawBitmap(
				scene.viewport.bitmap,
				null,
				scene.viewport.bitmapRect,
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
        	touch.down(event);
            return true;
        case MotionEvent.ACTION_MOVE:
        	touch.move(event);
        	invalidate();
        	return true;
        case MotionEvent.ACTION_UP:
        	touch.up(event);
        	return true;
        case MotionEvent.ACTION_CANCEL:
        	touch.cancel(event);
        	return true;
        }
        return super.onTouchEvent(event);
    }
}



