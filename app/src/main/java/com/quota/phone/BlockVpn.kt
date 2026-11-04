package com.quota.phone

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/** VPN فاضي: بياخد كل الترافيك ومبيبعتوش لأي مكان = قطع النت. */
class BlockVpn : VpnService() {
    private var pfd: ParcelFileDescriptor? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i?.action == "stop") {
            pfd?.close(); pfd = null; stopSelf(); return START_NOT_STICKY
        }
        if (pfd == null) {
            pfd = Builder().setSession("Quota")
                .addAddress("10.9.8.1", 32).addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:2:3::1", 128).addRoute("::", 0)
                .establish()
        }
        return START_STICKY
    }

    override fun onDestroy() { pfd?.close(); pfd = null; super.onDestroy() }
}
