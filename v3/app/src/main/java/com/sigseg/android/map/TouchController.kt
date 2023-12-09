package com.sigseg.android.map

import android.content.Context
import android.graphics.Point
import android.view.MotionEvent
import android.widget.Scroller
import com.sigseg.android.view.Scene

internal class TouchController(
    context: Context,
    private val scene: () -> Scene,
    private val doInvalidate: Runnable
) {
    private var state = TouchState.UNTOUCHED

    /** Where on the view did we initially touch  */
    private val viewDown = Point(0, 0)

    /** What was the coordinates of the viewport origin?  */
    private val viewportOriginAtDown = Point(0, 0)
    private val scroller = Scroller(context)
    private var touchThread: TouchThread? = null

    fun start() {
        touchThread = TouchThread(this).apply {
            start()
        }
    }

    fun stop() {
        val thread = touchThread
        if (thread!= null) {
            thread.stopThread()
            var retry = true
            while (retry) {
                try {
                    thread.join()
                    retry = false
                } catch (e: InterruptedException) {
                    // we will try it again and again...
                }
            }
            touchThread = null
        }
    }


    fun fling(velocityX: Float, velocityY: Float): Boolean {
        val thread = touchThread
        if (thread != null ){
            val origin = Point().apply { scene().viewport.getOrigin(this) }
            val viewSize = Point().apply { scene().viewport.getSize(this) }
            val sceneSize = Point().apply { scene().getSceneSize(this) }

            synchronized(this) {
                state = TouchState.START_FLING
                scene().setSuspend(true)
                scroller.fling(
                    origin.x,
                    origin.y, -velocityX.toInt(), -velocityY.toInt(),
                    0,
                    sceneSize.x - viewSize.x,
                    0,
                    sceneSize.y - viewSize.y
                )
                thread.interrupt()
            }
        }
        return true
    }

    fun down(event: MotionEvent): Boolean {
        scene().setSuspend(false) // If we were suspended because of a fling
        synchronized(this) {
            state = TouchState.IN_TOUCH
            viewDown.x = event.x.toInt()
            viewDown.y = event.y.toInt()
            val p = Point()
            scene().viewport.getOrigin(p)
            viewportOriginAtDown[p.x] = p.y
        }
        return true
    }

    fun move(event: MotionEvent): Boolean {
        if (state == TouchState.IN_TOUCH) {
            val zoom = scene().viewport.zoom
            val deltaX = zoom * (event.x - viewDown.x)
            val deltaY = zoom * (event.y - viewDown.y)
            scene().viewport.setOrigin(
                (viewportOriginAtDown.x - deltaX).toInt(),
                (viewportOriginAtDown.y - deltaY).toInt()
            )
            doInvalidate.run()
        }
        return true
    }

    fun up(): Boolean {
        if (state == TouchState.IN_TOUCH) {
            state = TouchState.UNTOUCHED
        }
        return true
    }

    fun cancel(): Boolean {
        if (state == TouchState.IN_TOUCH) {
            state = TouchState.UNTOUCHED
        }
        return true
    }

    fun inFling() = state in setOf(TouchState.START_FLING, TouchState.IN_FLING)

    fun startFling() {
        synchronized(this) {
            if (state == TouchState.START_FLING) {
                state = TouchState.IN_FLING
            }
        }
        if (state == TouchState.IN_FLING) {
            scroller.computeScrollOffset()
            scene().viewport.setOrigin(scroller.currX, scroller.currY)
            if (scroller.isFinished) {
                scene().setSuspend(false)
                synchronized(this) {
                    state = TouchState.UNTOUCHED
                    try {
                        Thread.sleep(5)
                    } catch (e: InterruptedException) {
                    }
                }
            }
        }
    }
}
