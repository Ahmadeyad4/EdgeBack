package com.example.edgeback

import android.content.Context
import android.content.SharedPreferences

/** إعدادات التطبيق (تُحفظ وتُقرأ من الخدمة والواجهة) */
object Cfg {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun putBool(c: Context, k: String, v: Boolean) { sp(c).edit().putBoolean(k, v).apply() }
    fun putInt(c: Context, k: String, v: Int) { sp(c).edit().putInt(k, v).apply() }
    fun reset(c: Context) { sp(c).edit().clear().apply() }
}
