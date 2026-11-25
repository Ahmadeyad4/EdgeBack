package com.example.edgeback

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class EdgeService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val views = mutableListOf<View>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rebuild() }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Cfg.sp(this).registerOnSharedPreferenceChangeListener(listener)
        rebuild()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::wm.isInitialized) rebuild()
    }

    private fun rebuild() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        if (Cfg.bool(this, "left", true)) addEdge(Gravity.LEFT)
        if (Cfg.bool(this, "right", true)) addEdge(Gravity.RIGHT)
    }

    private fun overlay(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
        )
    }

    private fun addEdge(side: Int) {
        val dm = resources.displayMetrics
        val dp = dm.density
        val screenH = dm.heightPixels
        val left = side == Gravity.LEFT

        // حجم ومكان منطقة السحب (من الإعدادات)
        val w = (Cfg.int(this, "width", 20) * dp).toInt()
        val h = (screenH * Cfg.int(this, "height", 60) / 100f).toInt().coerceAtLeast((40 * dp).toInt())
        val centerY = screenH * Cfg.int(this, "pos", 50) / 100f
        val top = (centerY - h / 2f).toInt().coerceIn(0, maxOf(0, screenH - h))
        val threshold = Cfg.int(this, "dist", 60) * dp

        // التأثير الشفاف أثناء السحب
        var fx: View? = null
        var fxLp: WindowManager.LayoutParams? = null
        val fxBg = GradientDrawable()
        val fxH = (110 * dp).toInt()
        val fxMaxW = (44 * dp).toInt()
        if (Cfg.bool(this, "effect", true)) {
            val r = 40 * dp
            fxBg.cornerRadii = if (left)
                floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            else
                floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            fxBg.setColor(Color.argb(110, 61, 139, 255))
            val v = View(this)
            v.background = fxBg
            v.visibility = View.GONE
            val lp = overlay(1, fxH, false)
            lp.gravity = side or Gravity.TOP
            wm.addView(v, lp)
            views += v
            fx = v
            fxLp = lp
        }

        fun showFx(p: Float, y: Float) {
            val v = fx ?: return
            val lp = fxLp ?: return
            lp.width = maxOf(1, (fxMaxW * p).toInt())
            lp.y = (y - fxH / 2f).toInt().coerceIn(0, maxOf(0, screenH - fxH))
            fxBg.setColor(
                if (p >= 1f) Color.argb(150, 0, 200, 83) else Color.argb(110, 61, 139, 255)
            )
            v.alpha = 0.35f + 0.65f * p
            v.visibility = View.VISIBLE
            wm.updateViewLayout(v, lp)
        }

        fun hideFx() {
            fx?.visibility = View.GONE
        }

        // منطقة اللمس
        val zone = View(this)
        if (Cfg.bool(this, "preview", false)) {
            zone.setBackgroundColor(Color.argb(110, 255, 152, 0))
        }
        var startX = 0f
        fun inward(x: Float): Float = if (left) x - startX else startX - x

        zone.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> startX = e.rawX
                MotionEvent.ACTION_MOVE -> {
                    val d = inward(e.rawX)
                    if (d > minOf(6 * dp, threshold / 3)) showFx((d / threshold).coerceIn(0f, 1f), e.rawY) else hideFx()
                }
                MotionEvent.ACTION_UP -> {
                    if (inward(e.rawX) > threshold) performGlobalAction(GLOBAL_ACTION_BACK)
                    hideFx()
                }
                MotionEvent.ACTION_CANCEL -> hideFx()
            }
            true
        }
        val zlp = overlay(w, h, true)
        zlp.gravity = side or Gravity.TOP
        zlp.y = top
        wm.addView(zone, zlp)
        views += zone
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        runCatching { Cfg.sp(this).unregisterOnSharedPreferenceChangeListener(listener) }
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        super.onDestroy()
    }
}
