package com.sigseg.android.map

internal class TouchThread(
    private val touch: TouchController
) : Thread() {
    private var running = false

    init {
        name = "TouchThread"
    }

    override fun start() {
        running = true
        super.start()
    }

    fun stopThread() {
        running = false
        interrupt()
    }

    override fun run() {
        while (running) {
            while (!touch.inFling()) {
                try { sleep(Long.MAX_VALUE) } catch (_: InterruptedException) { }
                if (!running) return
            }
            touch.startFling()
        }
    }
}
