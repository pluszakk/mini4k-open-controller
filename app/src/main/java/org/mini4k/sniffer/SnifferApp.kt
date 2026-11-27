package org.mini4k.sniffer

import android.app.Application

/** Keeps one Sniffer for the whole process so a capture survives screen rotation. */
class SnifferApp : Application() {
    lateinit var sniffer: Sniffer
        private set

    override fun onCreate() {
        super.onCreate()
        sniffer = Sniffer(this)
    }
}
