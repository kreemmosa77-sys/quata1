package com.quota.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var share: EditText
    private lateinit var pkg: EditText
    private lateinit var warn: EditText
    private lateinit var unit: Spinner
    private lateinit var mode: Spinner
    private lateinit var auto: CheckBox
    private val h = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { show(); h.postDelayed(this, 2000) } }

    private fun tv(t: String) = TextView(this).apply { text = t; setPadding(0, 24, 0, 4) }
    private fun et(v: String) = EditText(this).apply { setText(v); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
    private fun spin(items: List<String>, sel: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items); setSelection(sel)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val s = Q.p(this)
        status = TextView(this).apply { textSize = 18f }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        share = et(s.getFloat("share", 10f).toString())
        unit = spin(listOf("جيجا", "% من الباقة"), if (s.getString("unit", "gb") == "gb") 0 else 1)
        pkg = et(s.getFloat("pkg", 200f).toString())
        warn = et(s.getInt("warn", 80).toString())
        mode = spin(listOf("واي فاي فقط", "كل الشبكات (واي فاي + داتا)"), if (s.getString("mode", "wifi") == "wifi") 0 else 1)
        auto = CheckBox(this).apply { text = "تجديد شهري تلقائي"; isChecked = s.getBoolean("auto", true) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        listOf<View>(status, bar, tv("الحصة"), share, unit, tv("حجم الباقة الكلي (GB)"), pkg,
            tv("تنبيه عند (%)"), warn, tv("الشبكة المحسوبة"), mode, auto,
            btn("حفظ وتشغيل") { save(); start() },
            btn("تصفير الاستهلاك") { reset() },
            btn("رجّع النت") { unblock() },
            btn("إيقاف التطبيق") { stopAll() }).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }

    private fun save() {
        Q.p(this).edit()
            .putFloat("share", share.text.toString().toFloatOrNull() ?: 10f)
            .putString("unit", if (unit.selectedItemPosition == 0) "gb" else "pct")
            .putFloat("pkg", pkg.text.toString().toFloatOrNull() ?: 200f)
            .putInt("warn", warn.text.toString().toFloatOrNull()?.toInt() ?: 80)
            .putString("mode", if (mode.selectedItemPosition == 0) "wifi" else "all")
            .putBoolean("auto", auto.isChecked).putLong("last", -1).apply()
        show()
    }

    private fun start() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        else vpn()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { vpn() }

    private fun vpn() {
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 7) else launch()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 7) {
            if (resultCode == RESULT_OK) launch()
            else Toast.makeText(this, "لازم توافق على الـ VPN عشان القطع يشتغل", Toast.LENGTH_LONG).show()
        }
    }

    private fun launch() {
        Q.p(this).edit().putBoolean("enabled", true).apply()
        val s = Intent(this, QuotaService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s) else startService(s)
        Toast.makeText(this, "اشتغل ✔", Toast.LENGTH_SHORT).show()
    }

    private fun reset() {
        Q.p(this).edit().putLong("used", 0).putBoolean("warned", false).apply(); show()
    }

    private fun unblock() {
        startService(Intent(this, BlockVpn::class.java).setAction("stop"))
        Q.p(this).edit().putBoolean("blocked", false).apply(); show()
    }

    private fun stopAll() {
        Q.p(this).edit().putBoolean("enabled", false).putBoolean("blocked", false).apply()
        stopService(Intent(this, QuotaService::class.java)); stopService(Intent(this, BlockVpn::class.java)); show()
    }

    private fun show() {
        val s = Q.p(this); val q = Q.quota(this); val u = s.getLong("used", 0)
        val pct = if (q > 0) u / q * 100 else 100.0
        val st = if (s.getBoolean("blocked", false)) "🔴 النت مقطوع" else if (s.getBoolean("enabled", false)) "🟢 شغال" else "⚪ متوقف"
        status.text = st + "\n%.2f من %.2f جيجا (%d%%)\nالمتبقي %.2f جيجا".format(u / Q.GB, q / Q.GB, pct.toInt(), maxOf(0.0, q - u) / Q.GB)
        bar.progress = minOf(100, pct.toInt())
    }
}
