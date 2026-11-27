package com.ahorasuena

import android.app.Application
import android.os.Handler
import android.os.Looper

class App : Application() {
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            SessionReader.refresh(this@App)
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(tick)
    }
}
