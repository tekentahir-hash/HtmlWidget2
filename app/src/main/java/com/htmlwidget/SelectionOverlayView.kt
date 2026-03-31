package com.htmlwidget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SelectionOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    // Seçim kutusu (0f-1f oransal koordinatlar)
    private var selLeft = 0f
    private var selTop = 0f
    private var selRight = 0.5f
    private var selBottom = 0.5f

    private var dragging = false
    private var dragOffX = 0f
    private var dragOffY = 0f

    private val boxW get() = selRight - selLeft
    private val boxH get() = selBottom - selTop

    private val paintBox = Paint().apply {
        color = Color.argb(60, 33, 150, 243)
        style = Paint.Style.FILL
    }
    private val paintBorder = Paint().apply {
        color = Color.rgb(33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val paintHandle = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val paintHandleBorder = Paint().apply {
        color = Color.rgb(33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    var onPositionChanged: ((scrollX: Int, scrollY: Int) -> Unit)? = null

    // Önizleme içindeki gerçek içerik boyutu (px)
    var contentWidth = 1000
    var contentHeight = 3000

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val l = selLeft * w
        val t = selTop * h
        val r = selRight * w
        val b = selBottom * h

        // Karartma (seçim dışı)
        val darken = Paint().apply { color = Color.argb(100, 0, 0, 0) }
        canvas.drawRect(0f, 0f, w, t, darken)
        canvas.drawRect(0f, t, l, b, darken)
        canvas.drawRect(r, t, w, b, darken)
        canvas.drawRect(0f, b, w, h, darken)

        // Seçim kutusu
        canvas.drawRect(l, t, r, b, paintBox)
        canvas.drawRect(l, t, r, b, paintBorder)

        // Köşe tutamaçlar
        val hs = 14f
        listOf(l to t, r to t, l to b, r to b).forEach { (cx, cy) ->
            canvas.drawCircle(cx, cy, hs, paintHandle)
            canvas.drawCircle(cx, cy, hs, paintHandleBorder)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val tx = event.x / w
        val ty = event.y / h

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Kutu içinde sürükleme
                if (tx in selLeft..selRight && ty in selTop..selBottom) {
                    dragging = true
                    dragOffX = tx - selLeft
                    dragOffY = ty - selTop
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val newL = (tx - dragOffX).coerceIn(0f, 1f - boxW)
                    val newT = (ty - dragOffY).coerceIn(0f, 1f - boxH)
                    selLeft = newL
                    selTop = newT
                    selRight = newL + boxW
                    selBottom = newT + boxH
                    invalidate()
                    notifyPosition()
                }
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
            }
        }
        return true
    }

    private fun notifyPosition() {
        val scrollX = (selLeft * contentWidth).toInt()
        val scrollY = (selTop * contentHeight).toInt()
        onPositionChanged?.invoke(scrollX, scrollY)
    }

    /** Dışarıdan scroll değeri verilince kutuyu güncelle */
    fun setScrollPosition(scrollX: Int, scrollY: Int) {
        if (contentWidth <= 0 || contentHeight <= 0) return
        selLeft = (scrollX.toFloat() / contentWidth).coerceIn(0f, 1f - boxW)
        selTop = (scrollY.toFloat() / contentHeight).coerceIn(0f, 1f - boxH)
        selRight = selLeft + boxW
        selBottom = selTop + boxH
        invalidate()
    }
}
