package com.sigseg.android.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.sigseg.android.view.InputStreamScene;

import java.io.IOException;
import java.io.InputStream;

public class ImageSurfaceView extends SurfaceView implements SurfaceHolder.Callback, OnGestureListener  {
    private final static String TAG = ImageSurfaceView.class.getSimpleName();

    private InputStreamScene scene;
    private final TouchController touch;
    private GestureDetector gestureDectector;
    private ScaleGestureDetector scaleGestureDetector;
    private long lastScaleTime = 0;
    private long SCALE_MOVE_GUARD = 500; // milliseconds after scale to ignore move events

    private DrawThread drawThread;

    //region getters and setters
    public void getViewport(Point p){
        scene.getViewport().getOrigin(p);
    }
    
    public void setViewport(Point viewport){
        scene.getViewport().setOrigin(viewport.x, viewport.y);
    }

    public void setViewportCenter() {
        Point viewportSize = new Point();
        Point sceneSize = scene.getSceneSize();
        scene.getViewport().getSize(viewportSize);

        int x = (sceneSize.x - viewportSize.x) / 2;
        int y = (sceneSize.y - viewportSize.y) / 2;
        scene.getViewport().setOrigin(x, y);
    }

    public void setInputStream(InputStream inputStream) throws IOException {
        scene = new InputStreamScene(inputStream);
    }

    //endregion

    //region extends SurfaceView
    @Override
    public boolean onTouchEvent(MotionEvent me) {
        boolean consumed = gestureDectector.onTouchEvent(me);
        if (consumed)
            return true;
        scaleGestureDetector.onTouchEvent(me);
        switch (me.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: return touch.down(me);
            case MotionEvent.ACTION_MOVE:
                if (scaleGestureDetector.isInProgress() || System.currentTimeMillis()-lastScaleTime<SCALE_MOVE_GUARD)
                    break;
                return touch.move(me);
            case MotionEvent.ACTION_UP: return touch.up();
            case MotionEvent.ACTION_CANCEL: return touch.cancel();
        }
        return super.onTouchEvent(me);
    }
    //endregion

    //region SurfaceHolder.Callback constructors
    public ImageSurfaceView(Context context) {
        super(context);
        touch = new TouchController(context, ()->scene, this::invalidate);
        init(context);
    }
    
    public ImageSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        touch = new TouchController(context, ()->scene, this::invalidate);
        init(context);
    }

    public ImageSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touch = new TouchController(context, ()->scene, this::invalidate);
        init(context);
    }

    private void init(Context context){
        gestureDectector = new GestureDetector(context,this);
        getHolder().addCallback(this);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
    }
    //endregion

    //region class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private PointF screenFocus = new PointF();
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scaleFactor!=0f && scaleFactor!=1.0f){
                scaleFactor = 1/scaleFactor;
                screenFocus.set(detector.getFocusX(),detector.getFocusY());
                scene.getViewport().zoom(
                        scaleFactor,
                        screenFocus);
                invalidate();
            }
            lastScaleTime = System.currentTimeMillis();
            return true;
        }
    }

    //endregion


    //region implements SurfaceHolder.Callback
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        scene.getViewport().setSize(width, height);
        Log.d(TAG,String.format("onSizeChanged(w=%d,h=%d)",width,height));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawThread = new DrawThread(holder);
        drawThread.setName("drawThread");
        drawThread.setRunning(true);
        drawThread.start();
        scene.start();
        touch.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        touch.stop();
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
    //endregion

    //region implements OnGestureListener
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return touch.fling(velocityX, velocityY);
    }
    //region the rest are defaults
    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }
    //endregion

    //endregion

    //region class DrawThread

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
                try {
                    // Don't hog the entire CPU
                    Thread.sleep(5);
                } catch (InterruptedException e) {}
                c = null;
                try {
                    c = surfaceHolder.lockCanvas();
                    if (c!=null){
                        synchronized (surfaceHolder) {
                            scene.draw(c);// draw it
                        }
                    }
                } finally {
                    if (c != null) {
                        surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }        
        }
    }
    //endregion

    //region class Touch

    //endregion

}
