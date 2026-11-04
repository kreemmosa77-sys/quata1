package com.quota.phone

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class QuotaService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            val c = this@QuotaService
            Q.tick(c)
            val s = Q.p(c)
            val u = s.getLong("used", 0)
            Q.notify(c, 1, if (s.getBoolean("blocked", false)) "🔴 النت مقطوع" else "🟢 شغال",
                "%.2f / %.2f GB".format(u / Q.GB, Q.quota(c) / Q.GB))
            h.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, Q.notif(this, "مدير الحصة", "شغال"))
        h.post(loop)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { h.removeCallbacks(loop); super.onDestroy() }
}
