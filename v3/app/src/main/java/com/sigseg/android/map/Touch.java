package com.sigseg.android.map;

import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;
import android.widget.Scroller;

import com.sigseg.android.view.Scene;

class Touch {
    TouchState state = TouchState.UNTOUCHED;
    /** Where on the view did we initially touch */
    final Point viewDown = new Point(0,0);
    /** What was the coordinates of the viewport origin? */
    final Point viewportOriginAtDown = new Point(0,0);

    final Scroller scroller;

    TouchThread touchThread;

    private final Func<Scene> scene;

    private final Runnable doInvalidate;

    Touch(Context context, Func<Scene> scene, Runnable doInvalidate){
        scroller = new Scroller(context);
        this.scene = scene;
        this.doInvalidate = doInvalidate;
    }

    void start(){
        touchThread = new TouchThread(this, scene);
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
    boolean fling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
        scene.invoke().getViewport().getOrigin(fling_viewOrigin);
        scene.invoke().getViewport().getSize(fling_viewSize);
        scene.invoke().getSceneSize(fling_sceneSize);

        synchronized(this){
            state = TouchState.START_FLING;
            scene.invoke().setSuspend(true);
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
        return true;
    }
    boolean down(MotionEvent event){
        scene.invoke().setSuspend(false);    // If we were suspended because of a fling
        synchronized(this){
            state = TouchState.IN_TOUCH;
            viewDown.x = (int) event.getX();
            viewDown.y = (int) event.getY();
            Point p = new Point();
            scene.invoke().getViewport().getOrigin(p);
            viewportOriginAtDown.set(p.x,p.y);
        }
        return true;
    }

    boolean move(MotionEvent event){
        if (state==TouchState.IN_TOUCH){
            float zoom = scene.invoke().getViewport().getZoom();
            float deltaX = zoom * ((float)(event.getX()-viewDown.x));
            float deltaY = zoom * ((float)(event.getY()-viewDown.y));
            float newX = ((float)(viewportOriginAtDown.x - deltaX));
            float newY = ((float)(viewportOriginAtDown.y - deltaY));

            scene.invoke().getViewport().setOrigin((int)newX, (int)newY);
            doInvalidate.run();
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

}
