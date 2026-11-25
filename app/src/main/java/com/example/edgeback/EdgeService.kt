package com.example.edgeback

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class EdgeService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val views = mutableListOf<View>()

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addEdge(Gravity.START)
        addEdge(Gravity.END)
    }

    private fun addEdge(side: Int) {
        val d = resources.displayMetrics
        val width = (20 * d.density).toInt()          // عرض منطقة الحافة
        val height = (d.heightPixels * 0.6).toInt()    // ارتفاع المنطقة (60% من الشاشة)
        val threshold = 60 * d.density                 // مسافة السحب المطلوبة
        val v = View(this)
        var startX = 0f
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> startX = e.rawX
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - startX
                    val inward = if (side == Gravity.START) dx > threshold else dx < -threshold
                    if (inward) performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = side or Gravity.CENTER_VERTICAL
        wm.addView(v, lp)
        views += v
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        super.onDestroy()
    }
}
