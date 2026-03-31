package com.htmlwidget

import android.content.Context

object Prefs {
    private const val NAME = "hw3_prefs"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun setUri(ctx: Context, id: Int, v: String) = sp(ctx).edit().putString("uri_$id", v).apply()
    fun getUri(ctx: Context, id: Int) = sp(ctx).getString("uri_$id", null)

    fun setName(ctx: Context, id: Int, v: String) = sp(ctx).edit().putString("name_$id", v).apply()
    fun getName(ctx: Context, id: Int) = sp(ctx).getString("name_$id", "dosya.html") ?: "dosya.html"

    // Yenileme süresi saniye cinsinden (min 1 sn, sınırsız)
    fun setIntervalSec(ctx: Context, id: Int, v: Long) = sp(ctx).edit().putLong("interval_$id", v).apply()
    fun getIntervalSec(ctx: Context, id: Int) = sp(ctx).getLong("interval_$id", 900L)
    fun getIntervalMs(ctx: Context, id: Int) = getIntervalSec(ctx, id) * 1000L

    fun setScrollX(ctx: Context, id: Int, v: Int) = sp(ctx).edit().putInt("sx_$id", v).apply()
    fun getScrollX(ctx: Context, id: Int) = sp(ctx).getInt("sx_$id", 0)

    fun setScrollY(ctx: Context, id: Int, v: Int) = sp(ctx).edit().putInt("sy_$id", v).apply()
    fun getScrollY(ctx: Context, id: Int) = sp(ctx).getInt("sy_$id", 0)

    fun setZoom(ctx: Context, id: Int, v: Int) = sp(ctx).edit().putInt("zoom_$id", v).apply()
    fun getZoom(ctx: Context, id: Int) = sp(ctx).getInt("zoom_$id", 100)

    fun setDelaySec(ctx: Context, id: Int, v: Float) = sp(ctx).edit().putFloat("delay_$id", v).apply()
    fun getDelaySec(ctx: Context, id: Int) = sp(ctx).getFloat("delay_$id", 1.5f)
    fun getDelayMs(ctx: Context, id: Int) = (getDelaySec(ctx, id) * 1000f).toLong()

    fun setWidthDp(ctx: Context, id: Int, v: Int) = sp(ctx).edit().putInt("w_$id", v).apply()
    fun getWidthDp(ctx: Context, id: Int) = sp(ctx).getInt("w_$id", 160)

    fun setHeightDp(ctx: Context, id: Int, v: Int) = sp(ctx).edit().putInt("h_$id", v).apply()
    fun getHeightDp(ctx: Context, id: Int) = sp(ctx).getInt("h_$id", 160)

    fun remove(ctx: Context, id: Int) {
        sp(ctx).edit()
            .remove("uri_$id").remove("name_$id").remove("interval_$id")
            .remove("sx_$id").remove("sy_$id").remove("zoom_$id")
            .remove("delay_$id").remove("w_$id").remove("h_$id")
            .apply()
    }
}
