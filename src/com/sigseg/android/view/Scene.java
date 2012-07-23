package com.sigseg.android.view;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;

public abstract class Scene {

    private Point size = new Point();

    private final Viewport viewport = new Viewport();

    private final Cache cache = new Cache();

    public void setSceneSize(int width, int height) {
        size.set(width, height);
    }

    public Point getSceneSize() {
        return size;
    }

    public void getSceneSize(Point point) {
        point.set(size.x, size.y);
    }

    public Point getViewportOrigin() {
        Point p = new Point();
        synchronized (viewport) {
            p.set(viewport.window.left, viewport.window.top);
        }
        return p;
    }

    public void getViewportOrigin(Point point) {
        synchronized (viewport) {
            point.set(viewport.window.left, viewport.window.top);
        }
    }

    public void setViewportOrigin(int x, int y) {
        viewport.setOrigin(x, y);
    }

    public void setViewportSize(int width, int height) {
        viewport.setSize(width, height);
    }

    public void getViewportSize(Point p) {
        viewport.getSize(p);
    }

    public void setViewportTranslation(int x, int y) {
        viewport.setTranslation(x, y);
    }

    public void getViewportTranslation(Point p) {
        viewport.getTranslation(p);
    }

    public void initialize() {
        if (cache.getState() == CacheState.UNINITIALIZED) {
            synchronized (cache) {
                cache.setState(CacheState.INITIALIZED);
            }
        }
    }

    public void start() {
        cache.start();
    }

    public void stop() {
        cache.stop();
    }

    public void setSuspend(boolean suspend) {
        if (suspend) {
            synchronized (cache) {
                cache.setState(CacheState.SUSPEND);
            }
        } else {
            if (cache.getState() == CacheState.SUSPEND) {
                synchronized (cache) {
                    cache.setState(CacheState.INITIALIZED);
                }
            }
        }
    }

    public void invalidate() {
        cache.invalidate();
    }

    public void draw(Canvas c, float scaleFactor) {
        viewport.draw(c, scaleFactor);
    }

    private class Viewport {
        /** The bitmap of the current viewport */
        Bitmap bitmap = null;
        /** A Rect that can be used for drawing. Same size as bitmap */
        final Rect identity = new Rect(0, 0, 0, 0);
        /** A Rect that defines where the Viewport is within the scene */
        final Rect window = new Rect(0, 0, 0, 0);
        /** The translation to apply before drawing the bitmap */
        Point translation = null;

        void setOrigin(int x, int y) {
            synchronized (this) {
                int w = window.width();
                int h = window.height();

                // check bounds
                if (x < 0)
                    x = 0;

                if (y < 0)
                    y = 0;

                if (x + w > size.x)
                    x = size.x - w;

                if (y + h > size.y)
                    y = size.y - h;

                window.set(x, y, x + w, y + h);
            }
        }

        void setSize(int w, int h) {
            synchronized (this) {
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
                bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
                identity.set(0, 0, w, h);
                window.set(window.left, window.top, window.left + w, window.top + h);
            }
        }

        void getSize(Point p) {
            synchronized (this) {
                p.x = identity.right;
                p.y = identity.bottom;
            }
        }

        void setTranslation(int x, int y) {
            synchronized (this) {
                if (x == 0 && y == 0)
                    translation = null;
                else
                    translation = new Point(x, y);
            }
        }

        void getTranslation(Point p) {
            synchronized (this) {
                p.set(translation.x, translation.y);
            }
        }

        void draw(Canvas c, float scaleFactor) {
            cache.update(this, scaleFactor);
            synchronized (this) {
                if (bitmap != null) {
                    if (translation != null)
                        c.translate(translation.x, translation.y);
                    c.scale(scaleFactor, scaleFactor);
                    c.drawBitmap(bitmap, null, identity, null);
                    if (translation != null)
                        c.translate(-translation.x, -translation.y);
                    drawComplete(c);
                }
            }
        }
    }

    private enum CacheState {
        UNINITIALIZED, INITIALIZED, START_UPDATE, IN_UPDATE, READY, SUSPEND
    };

    private class Cache {
        final Rect window = new Rect(0, 0, 0, 0);
        Bitmap bitmapRef = null;
        CacheState state = CacheState.UNINITIALIZED;

        void setState(CacheState newState) {
            state = newState;
        }

        CacheState getState() {
            return state;
        }

        CacheThread cacheThread;

        void start() {
            if (cacheThread != null)
                cacheThread.stop();
            cacheThread = new CacheThread(this);
            cacheThread.setName("cacheThread");
            cacheThread.start();
        }

        void stop() {
            cacheThread.running = false;
            cacheThread.interrupt();

            boolean retry = true;
            while (retry) {
                try {
                    cacheThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // we will try it again and again...
                }
            }
            cacheThread = null;
        }

        void invalidate() {
            synchronized (this) {
                setState(CacheState.INITIALIZED);
                cacheThread.interrupt();
            }
        }

        void update(Viewport viewport, float scaleFactor) {
            Bitmap bitmap = null;
            synchronized (this) {
                switch (getState()) {
                case UNINITIALIZED:
                    return;
                case INITIALIZED:
                    setState(CacheState.START_UPDATE);
                    cacheThread.interrupt();
                    break;
                case START_UPDATE:
                    break;
                case IN_UPDATE:
                    break;
                case SUSPEND:
                    break;
                case READY:
                    if (bitmapRef == null) {
                        setState(CacheState.START_UPDATE);
                        cacheThread.interrupt();
                    } else if (!window.contains(viewport.window)) {
                        setState(CacheState.START_UPDATE);
                        cacheThread.interrupt();
                    } else {
                        bitmap = bitmapRef;
                    }
                    break;
                }
            }
            if (bitmap == null)
                loadSampleIntoViewport(viewport,scaleFactor);
            else
                loadBitmapIntoViewport(bitmap, viewport, scaleFactor);
        }

        void loadBitmapIntoViewport(Bitmap bitmap, Viewport viewport, float scaleFactor) {
            if (bitmap != null) {
                synchronized (viewport) {
                    int left = viewport.window.left - window.left;
                    int top = viewport.window.top - window.top;
                    int right = left + viewport.window.width();
                    int bottom = top + viewport.window.height();
                    srcRect.set(left, top, right, bottom);
                    Canvas c = new Canvas(viewport.bitmap);
                    c.scale(scaleFactor, scaleFactor);
                    c.drawBitmap(bitmap, srcRect, viewport.identity, null);
                }
            }
        }
        final Rect srcRect = new Rect(0, 0, 0, 0);

        void loadSampleIntoViewport(Viewport viewport, float scaleFactor) {
            if (getState() != CacheState.UNINITIALIZED) {
                synchronized (viewport) {
                    drawSampleRectIntoBitmap(viewport.bitmap, viewport.window, scaleFactor);
                }
            }
        }
    }

    class CacheThread extends Thread {
        final Cache cache;
        boolean running = false;

        void setRunning(boolean value) {
            running = value;
        }

        CacheThread(Cache cache) {
            this.cache = cache;
        }

        @Override
        public void run() {
            running = true;
            Rect viewportRect = new Rect(0, 0, 0, 0);
            while (running) {
                while (running && cache.getState() != CacheState.START_UPDATE)
                    try {
                        Thread.sleep(Integer.MAX_VALUE);
                    } catch (InterruptedException e) {
                    }
                if (!running)
                    return;
                boolean cont = false;
                synchronized (cache) {
                    if (cache.getState() == CacheState.START_UPDATE) {
                        cache.setState(CacheState.IN_UPDATE);
                        cache.bitmapRef = null;
                        cont = true;
                    }
                }
                if (cont) {
                    synchronized (viewport) {
                        viewportRect.set(viewport.window);
                    }
                    synchronized (cache) {
                        if (cache.getState() == CacheState.IN_UPDATE)
                            cache.window.set(calculateCacheWindow(viewportRect));
                        else
                            cont = false;
                    }
                    if (cont) {
                        try {
                            Bitmap bitmap = fillCache(cache.window);
                            synchronized (cache) {
                                if (cache.getState() == CacheState.IN_UPDATE) {
                                    cache.bitmapRef = bitmap;
                                    cache.setState(CacheState.READY);
                                }
                            }
                        } catch (OutOfMemoryError e) {
                            synchronized (cache) {
                                fillCacheOutOfMemoryError(e);
                                if (cache.getState() == CacheState.IN_UPDATE) {
                                    cache.setState(CacheState.START_UPDATE);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected abstract Bitmap fillCache(Rect rectOfCache);

    protected abstract void fillCacheOutOfMemoryError(OutOfMemoryError error);

    protected abstract Rect calculateCacheWindow(Rect viewportRect);

    protected abstract void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample, float scaleFactor);

    protected abstract void drawComplete(Canvas canvas);
}