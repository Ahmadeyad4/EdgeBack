package com.example.edgeback

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private enum class S { IDLE, DOWNLOADING, PAUSED }
    private var state = S.IDLE
    private var info: Updater.Info? = null
    private var dl: Downloader? = null
    private lateinit var apk: File
    private var pad = 0

    private lateinit var status: TextView
    private lateinit var panel: LinearLayout
    private lateinit var updTitle: TextView
    private lateinit var bar: ProgressBar
    private lateinit var mainBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var previewSw: Switch

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        apk = File(filesDir, "update.apk")
        pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply { text = "Edge Back"; textSize = 26f })
        status = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad) }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "تفعيل الخدمة (إعدادات إمكانية الوصول)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(settingsBox())

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, pad, 0, 0)
        }
        updTitle = TextView(this).apply { textSize = 16f }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        mainBtn = Button(this).apply { setOnClickListener { onMain() } }
        stopBtn = Button(this).apply { text = "إيقاف"; setOnClickListener { onStopClick() } }
        row.addView(mainBtn); row.addView(stopBtn)
        panel.addView(updTitle); panel.addView(bar); panel.addView(row)
        root.addView(panel)
        setContentView(ScrollView(this).apply { addView(root) })
        render()

        // فحص التحديث مع كل دخول للتطبيق
        Thread {
            val r = runCatching { Updater.check() }.getOrNull()
            runOnUiThread {
                if (r != null) {
                    info = r
                    updTitle.text = "يتوفر تحديث جديد: ${r.version}\n${r.notes}"
                    panel.visibility = View.VISIBLE
                    render()
                }
            }
        }.start()
    }

    // ---------- الإعدادات ----------
    private fun sw(label: String, key: String, def: Boolean): Switch {
        val s = Switch(this)
        s.text = label
        s.isChecked = Cfg.bool(this, key, def)
        s.setPadding(0, pad / 2, 0, pad / 2)
        s.setOnCheckedChangeListener { _, c -> Cfg.putBool(this, key, c) }
        return s
    }

    private fun seek(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, pad / 2, 0, 0)
        val cur = Cfg.int(this, key, def)
        val t = TextView(this)
        t.text = "$label: $cur$unit"
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = cur - lo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + lo
                t.text = "$label: $v$unit"
                if (fromUser) {
                    Cfg.putInt(this@MainActivity, key, v)
                    previewSw.isChecked = true   // نعرض المكان بلون أثناء التعديل
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(t)
        box.addView(sb, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return box
    }

    private fun settingsBox(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, pad, 0, 0)
        val title = TextView(this)
        title.text = "الإعدادات"
        title.textSize = 20f
        box.addView(title)
        box.addView(sw("إظهار تأثير السحب", "effect", true))
        box.addView(sw("تفعيل الحافة اليمين", "right", true))
        box.addView(sw("تفعيل الحافة اليسار", "left", true))
        previewSw = sw("إظهار مكان السحب بلون (معاينة)", "preview", false)
        box.addView(previewSw)
        box.addView(seek("مسافة السحب المطلوبة (أقل = أسرع وأحسّ)", "dist", 10, 120, 60, "dp"))
        box.addView(seek("عرض المنطقة", "width", 10, 80, 20, "dp"))
        box.addView(seek("ارتفاع المنطقة", "height", 10, 100, 60, "%"))
        box.addView(seek("مكان المنطقة (من الأعلى للأسفل)", "pos", 0, 100, 50, "%"))
        val reset = Button(this)
        reset.text = "إعادة الضبط الافتراضي"
        reset.setOnClickListener { Cfg.reset(this); recreate() }
        box.addView(reset)
        return box
    }

    // ---------- الحالة والتحديث ----------
    override fun onResume() {
        super.onResume()
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = (if (on) "الخدمة شغالة ✅" else "الخدمة مو مفعلة ❌") +
                "\nالإصدار: ${BuildConfig.VERSION_NAME}"
    }

    private fun onMain() {
        val i = info ?: return
        when (state) {
            S.IDLE, S.PAUSED -> {
                state = S.DOWNLOADING
                dl = Downloader(i.apkUrl, apk,
                    { d, t -> runOnUiThread { bar.progress = if (t > 0) (d * 100 / t).toInt() else 0 } },
                    { runOnUiThread { state = S.IDLE; install() } },
                    { m -> runOnUiThread { state = S.PAUSED; render(); Toast.makeText(this, "فشل: $m", Toast.LENGTH_LONG).show() } }
                ).also { it.start() }
            }
            S.DOWNLOADING -> { dl?.pause(); state = S.PAUSED }
        }
        render()
    }

    private fun onStopClick() {
        dl?.cancel(); dl = null
        apk.delete(); bar.progress = 0; state = S.IDLE; render()
    }

    private fun render() {
        mainBtn.text = when (state) {
            S.IDLE -> "تحميل التحديث"
            S.DOWNLOADING -> "إيقاف مؤقت"
            S.PAUSED -> "استئناف"
        }
        stopBtn.isEnabled = state != S.IDLE
    }

    private fun install() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            Toast.makeText(this, "فعّل السماح بالتثبيت ثم اضغط تحميل مرة ثانية", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
