																																																																																																																																																																				package com.sigseg.android.worldmap;

import java.io.IOException;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class WorldView extends SurfaceView implements SurfaceHolder.Callback, OnGestureListener{
	private final static String TAG = "WorldView";
	private final boolean DEBUG = true;

//	private long startTime=0;
	private final Scene scene = new Scene();
	
	
	private final Touch touch = new Touch();
	private GestureDetector gestureDectector;
	
	private DrawThread drawThread;
	
	//[start] SurfaceHolder.Callback constructors
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
		gestureDectector = new GestureDetector(this);
		getHolder().addCallback(this);
		try {
			scene.setFile(context, "world.jpg");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
	}
	//[end]
	//[start] OnGestureListener
	@Override
	public boolean onDown(MotionEvent e) {
		Log.d(TAG,"onDown");
		return false;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		Log.d(TAG,"onFling");
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		Log.d(TAG,"onLongPress");
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		Log.d(TAG,"onScroll");
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
		Log.d(TAG,"onShowPress");
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		Log.d(TAG,"onSingleTapUp");
		return false;
	}
	// [end]
	//[start] View overrides
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		scene.setViewSize(w, h);
		if (DEBUG)
			Log.d(TAG,String.format("onSizeChanged(w=%d,h=%d,oldw=%d,oldh=%d",w,h,oldw,oldh));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		// tell the scene to update it's viewport bitmap
		scene.update();
		
		// draw it
		scene.draw(canvas);
//    	if (DEBUG){
//    		long now = System.currentTimeMillis();
//			double n = ((double)now)/1000L;
//			double s = ((double)startTime)/1000L;
//			double fps = 1L/(n-s);
//			Log.d(TAG,String.format("msec=%d FPS=%.2f",now-startTime,fps));
//			startTime = System.currentTimeMillis();
//    	}
	}
    @Override
    public boolean onTouchEvent(MotionEvent me) {
    	boolean consumed = gestureDectector.onTouchEvent(me);
    	if (consumed)
    		return true;
        switch (me.getAction()) {
        case MotionEvent.ACTION_DOWN:
        	touch.down(me);
            return true;
        case MotionEvent.ACTION_MOVE:
        	touch.move(me);
        	invalidate();
        	return true;
        case MotionEvent.ACTION_UP:
        	touch.up(me);
        	return true;
        case MotionEvent.ACTION_CANCEL:
        	touch.cancel(me);
        	return true;
        }
        return super.onTouchEvent(me);
    }
	//[end]
    //[start] SurfaceHolder.Callback overrides
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		onSizeChanged(width, height, 0, 0);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		drawThread = new DrawThread(holder);
		drawThread.setName("drawThread");
		drawThread.setRunning(true);
		drawThread.start();
		scene.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	    scene.stop();
	    drawThread.setRunning(false);
	    boolean retry = true;
	    while (retry) {
	        try {
	            drawThread.join();
	            retry = false;
	        } catch (InterruptedException e) {
	            // we will try it again and again...
	        }
	    }
	}
	//[end]
	class DrawThread extends Thread {
	    private SurfaceHolder surfaceHolder;

	    private boolean running = false;
	    public void setRunning(boolean value){ running = value; }
	    
	    public DrawThread(SurfaceHolder surfaceHolder){
	    	this.surfaceHolder = surfaceHolder;
	    }
	    
		@Override
		public void run() {
		    Canvas c;
		    while (running) {
		        c = null;
		        try {
		            c = surfaceHolder.lockCanvas(null);
		            synchronized (surfaceHolder) {
	            		onDraw(c);
		            }
		        } finally {
		            if (c != null) {
		            	surfaceHolder.unlockCanvasAndPost(c);
		            }
		        }
		    }		
		}
	}
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
        	Point p = scene.getOrigin();
        	viewportOriginAtDown.set(p.x,p.y);
		}
		
		void move(MotionEvent event){
			if (inTouch){
	        	int deltaX = (int) (event.getX()-viewDown.x);
	        	int deltaY = (int) (event.getY()-viewDown.y);
	        	int newX = (int) (viewportOriginAtDown.x - deltaX);
	        	int newY = (int) (viewportOriginAtDown.y - deltaY);
	        	
	        	scene.setOrigin(newX, newY);
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
}