package com.quota.phone

import android.app.*
import android.content.*
import android.net.TrafficStats
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

object Q {
    const val GB = 1024.0 * 1024.0 * 1024.0
    fun p(c: Context): SharedPreferences = c.getSharedPreferences("q", Context.MODE_PRIVATE)

    fun quota(c: Context): Double {
        val s = p(c)
        val share = s.getFloat("share", 10f).toDouble()
        return if (s.getString("unit", "gb") == "gb") share * GB else share / 100.0 * s.getFloat("pkg", 200f) * GB
    }

    private fun counter(c: Context): Long {
        fun f(x: Long) = if (x < 0) 0L else x
        val all = f(TrafficStats.getTotalRxBytes()) + f(TrafficStats.getTotalTxBytes())
        if (p(c).getString("mode", "wifi") == "all") return all
        val mob = f(TrafficStats.getMobileRxBytes()) + f(TrafficStats.getMobileTxBytes())
        return maxOf(0L, all - mob)
    }

    fun tick(c: Context) {
        val s = p(c); val e = s.edit()
        val mon = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        var used = s.getLong("used", 0)
        val oldMon = s.getString("month", "") ?: ""
        if (oldMon != mon) {
            if (oldMon != "" && s.getBoolean("auto", true)) { used = 0; e.putBoolean("warned", false) }
            e.putString("month", mon)
        }
        val cur = counter(c); val last = s.getLong("last", -1)
        val blocked = s.getBoolean("blocked", false)
        if (last >= 0 && !blocked) used += if (cur >= last) cur - last else cur
        e.putLong("last", cur).putLong("used", used)
        val q = quota(c); val over = used >= q
        if (over) c.startService(Intent(c, BlockVpn::class.java))
        else if (blocked) c.startService(Intent(c, BlockVpn::class.java).setAction("stop"))
        e.putBoolean("blocked", over)
        val pct = if (q > 0) used / q * 100 else 100.0
        val warn = s.getInt("warn", 80)
        if (!over && pct >= warn && !s.getBoolean("warned", false)) {
            notify(c, 2, "حصتك قربت تخلص", "وصلت ${pct.toInt()}%"); e.putBoolean("warned", true)
        }
        if (pct < warn) e.putBoolean("warned", false)
        e.apply()
    }

    fun notif(c: Context, t: String, b: String): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        val nb = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("q", "Quota", NotificationManager.IMPORTANCE_DEFAULT))
            Notification.Builder(c, "q")
        } else Notification.Builder(c)
        return nb.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(t).setContentText(b).setOnlyAlertOnce(true).build()
    }

    fun notify(c: Context, id: Int, t: String, b: String) {
        c.getSystemService(NotificationManager::class.java).notify(id, notif(c, t, b))
    }
}
