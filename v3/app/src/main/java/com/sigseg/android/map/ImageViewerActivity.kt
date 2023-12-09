package com.sigseg.android.map

import android.app.Activity
import android.graphics.Point
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import com.sigseg.android.worldmap.R

private const val KEY_X = "X"
private const val KEY_Y = "Y"
private const val MAP_FILE = "world.jpg"

class ImageViewerActivity : Activity() {
    private val imageSurfaceView by lazy { findViewById<ImageSurfaceView>(R.id.worldview) }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.main)
        with(imageSurfaceView) {
            setInputStream(assets.open(MAP_FILE))
            post {
                val p = bundle?.takeIf { it.containsKey(KEY_X) && it.containsKey(KEY_Y) }?.let {
                    Point(it.getInt(KEY_X), it.getInt(KEY_Y))
                }
                if (p != null) {
                    imageSurfaceView.setViewport(p)
                } else {
                    imageSurfaceView.setViewportCenter()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val p = Point().apply { imageSurfaceView.getViewport(this) }
        outState.putInt(KEY_X, p.x)
        outState.putInt(KEY_Y, p.y)
        super.onSaveInstanceState(outState)
    }
}
