package com.htmlwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object HtmlRenderer {

    fun render(
        ctx: Context,
        uri: String,
        widthPx: Int,
        heightPx: Int,
        scrollX: Int,
        scrollY: Int,
        zoom: Int,
        delayMs: Long,
        onDone: (Bitmap?) -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            try {
                val renderH = (heightPx + scrollY + 500).coerceAtLeast(200)
                val renderW = (widthPx + scrollX + 200).coerceAtLeast(200)

                val wv = WebView(ctx)
                wv.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(renderW, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(renderH, android.view.View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, renderW, renderH)
                wv.setBackgroundColor(Color.WHITE)
                wv.setInitialScale(zoom)

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = true
                    allowContentAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                wv.scrollTo(scrollX, scrollY)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                                        val canvas = Canvas(bmp)
                                        canvas.drawColor(Color.WHITE)
                                        canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
                                        wv.draw(canvas)
                                        wv.destroy()
                                        onDone(bmp)
                                    } catch (e: Exception) {
                                        try { wv.destroy() } catch (_: Exception) {}
                                        onDone(null)
                                    }
                                }, 300)
                            } catch (e: Exception) {
                                try { wv.destroy() } catch (_: Exception) {}
                                onDone(null)
                            }
                        }, delayMs)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        try { wv.destroy() } catch (_: Exception) {}
                        onDone(null)
                    }
                }

                try {
                    val parsedUri = Uri.parse(uri)
                    val stream = ctx.contentResolver.openInputStream(parsedUri)
                    val html = stream?.bufferedReader()?.readText() ?: ""
                    stream?.close()
                    val basePath = parsedUri.path?.let {
                        val parent = java.io.File(it).parent
                        if (parent != null) "file://$parent/" else null
                    }
                    wv.loadDataWithBaseURL(basePath, html, "text/html", "UTF-8", null)
                } catch (e: Exception) {
                    try { wv.destroy() } catch (_: Exception) {}
                    onDone(null)
                }

            } catch (e: Exception) {
                onDone(null)
            }
        }
    }

    fun dpToPx(ctx: Context, dp: Int): Int =
        (dp * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(50)
}
