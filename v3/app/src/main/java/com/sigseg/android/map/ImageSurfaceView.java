package com.sigseg.android.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.OnGestureListener;
import android.widget.Scroller;
import com.sigseg.android.view.InputStreamScene;

import java.io.IOException;
import java.io.InputStream;

public class ImageSurfaceView extends SurfaceView implements SurfaceHolder.Callback, OnGestureListener  {
    private final static String TAG = ImageSurfaceView.class.getSimpleName();

    private InputStreamScene scene;
    private final Touch touch;
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
            case MotionEvent.ACTION_UP: return touch.up(me);
            case MotionEvent.ACTION_CANCEL: return touch.cancel(me);
        }
        return super.onTouchEvent(me);
    }
    //endregion

    //region SurfaceHolder.Callback constructors
    public ImageSurfaceView(Context context) {
        super(context);
        touch = new Touch(context);
        init(context);
    }
    
    public ImageSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        touch = new Touch(context);
        init(context);
    }

    public ImageSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touch = new Touch(context);
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
        return touch.fling( e1, e2, velocityX, velocityY);
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

    enum TouchState {UNTOUCHED,IN_TOUCH,START_FLING,IN_FLING};
    class Touch {
        TouchState state = TouchState.UNTOUCHED;
        /** Where on the view did we initially touch */
        final Point viewDown = new Point(0,0);
        /** What was the coordinates of the viewport origin? */
        final Point viewportOriginAtDown = new Point(0,0);
        
        final Scroller scroller;
        
        TouchThread touchThread;
        
        Touch(Context context){
            scroller = new Scroller(context);
        }
        
        void start(){
            touchThread = new TouchThread(this);
            touchThread.setName("touchThread");
            touchThread.start();
        }
        
        void stop(){
            touchThread.running = false;
            touchThread.interrupt();

            boolean retry = true;
            while (retry) {
                try {
                    touchThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // we will try it again and again...
                }
            }
            touchThread = null;
        }
        
        Point fling_viewOrigin = new Point();
        Point fling_viewSize = new Point();
        Point fling_sceneSize = new Point();
        boolean fling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
            scene.getViewport().getOrigin(fling_viewOrigin);
            scene.getViewport().getSize(fling_viewSize);
            scene.getSceneSize(fling_sceneSize);

            synchronized(this){
                state = TouchState.START_FLING;
                scene.setSuspend(true);
                scroller.fling(
                    fling_viewOrigin.x,
                    fling_viewOrigin.y,
                    (int)-velocityX,
                    (int)-velocityY,
                    0, 
                    fling_sceneSize.x-fling_viewSize.x, 
                    0,
                    fling_sceneSize.y-fling_viewSize.y);
                touchThread.interrupt();
            }
//            Log.d(TAG,String.format("scroller.fling(%d,%d,%d,%d,%d,%d,%d,%d)",
//                    fling_viewOrigin.x,
//                    fling_viewOrigin.y,
//                    (int)-velocityX,
//                    (int)-velocityY,
//                    0, 
//                    fling_sceneSize.x-fling_viewSize.x,
//                    0,
//                    fling_sceneSize.y-fling_viewSize.y));
            return true;
        }
        boolean down(MotionEvent event){
            scene.setSuspend(false);    // If we were suspended because of a fling
            synchronized(this){
                state = TouchState.IN_TOUCH;
                viewDown.x = (int) event.getX();
                viewDown.y = (int) event.getY();
                Point p = new Point();
                scene.getViewport().getOrigin(p);
                viewportOriginAtDown.set(p.x,p.y);
            }
            return true;
        }
        
        boolean move(MotionEvent event){
            if (state==TouchState.IN_TOUCH){
                float zoom = scene.getViewport().getZoom();
                float deltaX = zoom * ((float)(event.getX()-viewDown.x));
                float deltaY = zoom * ((float)(event.getY()-viewDown.y));
                float newX = ((float)(viewportOriginAtDown.x - deltaX));
                float newY = ((float)(viewportOriginAtDown.y - deltaY));
                
                scene.getViewport().setOrigin((int)newX, (int)newY);
                invalidate();
            }
            return true;
        }
        
        boolean up(MotionEvent event){
            if (state==TouchState.IN_TOUCH){
                state = TouchState.UNTOUCHED;
            }
            return true;
        }
        
        boolean cancel(MotionEvent event){
            if (state==TouchState.IN_TOUCH){
                state = TouchState.UNTOUCHED;
            }
            return true;
        }
        
        class TouchThread extends Thread {
            final Touch touch;
            boolean running = false;
            void setRunning(boolean value){ running = value; }
            
            TouchThread(Touch touch){ this.touch = touch; }
            @Override
            public void run() {
                running=true;
                while(running){
                    while(touch.state!=TouchState.START_FLING && touch.state!=TouchState.IN_FLING){
                        try {
                            Thread.sleep(Integer.MAX_VALUE);
                        } catch (InterruptedException e) {}
                        if (!running)
                            return;
                    }
                    synchronized (touch) {
                        if (touch.state==TouchState.START_FLING){
                            touch.state = TouchState.IN_FLING;
                        }
                    }
                    if (touch.state==TouchState.IN_FLING){
                        scroller.computeScrollOffset();
                        scene.getViewport().setOrigin(scroller.getCurrX(), scroller.getCurrY());
                        if (scroller.isFinished()){
                            scene.setSuspend(false);
                            synchronized (touch) {
                                touch.state = TouchState.UNTOUCHED;
                                try{
                                    Thread.sleep(5);
                                } catch (InterruptedException e) {}
                            }
                        }
                    }
                }
            }
        }
    }
    //endregion

}
