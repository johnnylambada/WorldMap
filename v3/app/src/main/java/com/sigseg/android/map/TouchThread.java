package com.sigseg.android.map;

import com.sigseg.android.view.Scene;

class TouchThread extends Thread {
    private final Touch touch;
    boolean running = false;

    void setRunning(boolean value) {
        running = value;
    }

    private final Func<Scene> scene;

    TouchThread(Touch touch, Func<Scene> scene) {
        this.touch = touch;
        this.scene = scene;
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            while (touch.state != TouchState.START_FLING && touch.state != TouchState.IN_FLING) {
                try {
                    Thread.sleep(Integer.MAX_VALUE);
                } catch (InterruptedException e) {
                }
                if (!running)
                    return;
            }
            synchronized (touch) {
                if (touch.state == TouchState.START_FLING) {
                    touch.state = TouchState.IN_FLING;
                }
            }
            if (touch.state == TouchState.IN_FLING) {
                touch.scroller.computeScrollOffset();
                scene.invoke().getViewport().setOrigin(touch.scroller.getCurrX(), touch.scroller.getCurrY());
                if (touch.scroller.isFinished()) {
                    scene.invoke().setSuspend(false);
                    synchronized (touch) {
                        touch.state = TouchState.UNTOUCHED;
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }
    }
}
