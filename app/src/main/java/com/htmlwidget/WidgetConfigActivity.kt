package com.htmlwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedUri: Uri? = null
    private var selectedName = ""
    private var syncing = false
    private var intervalSec = 900L

    // Yenileme birimi: 0=sn, 1=dk, 2=saat
    private val UNITS = arrayOf("saniye", "dakika", "saat")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContentView(R.layout.activity_widget_config)

        setupFile()
        setupScrollZoom()
        setupInterval()
        setupSize()
        setupButtons()
    }

    // ── DOSYA ──────────────────────────────────────────────────────────────
    private fun setupFile() {
        findViewById<Button>(R.id.btn_file).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html", "text/htm", "application/xhtml+xml"))
            }, REQ_FILE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            selectedUri = uri
            selectedName = queryName(uri)
            findViewById<TextView>(R.id.tv_file).text = selectedName
            loadPreview(uri)
        }
    }

    private fun queryName(uri: Uri): String {
        var name = ""
        try {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) name = it.getString(i)
                }
            }
        } catch (_: Exception) {}
        return name.ifEmpty { uri.lastPathSegment ?: "dosya.html" }
    }

    // ── ÖNİZLEME ──────────────────────────────────────────────────────────
    private val wvPreview get() = findViewById<WebView>(R.id.wv_preview)
    private val overlay get() = findViewById<SelectionOverlayView>(R.id.overlay)
    private val tvPreviewHint get() = findViewById<TextView>(R.id.tv_preview_hint)

    private fun loadPreview(uri: Uri) {
        tvPreviewHint.text = getString(R.string.preview_loading)
        tvPreviewHint.visibility = android.view.View.VISIBLE

        wvPreview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
        }

        wvPreview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                tvPreviewHint.visibility = android.view.View.GONE
                // Sayfa boyutunu al ve overlay'e bildir
                Handler(Looper.getMainLooper()).postDelayed({
                    wvPreview.evaluateJavascript(
                        "(function(){ return JSON.stringify({w: document.body.scrollWidth, h: document.body.scrollHeight}); })()"
                    ) { result ->
                        try {
                            val clean = result.trim('"').replace("\\\"", "\"")
                            val w = Regex("\"w\":(\\d+)").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: 1000
                            val h = Regex("\"h\":(\\d+)").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: 3000
                            overlay.contentWidth = w
                            overlay.contentHeight = h
                            // Mevcut slider değerini overlay'e yansıt
                            val sx = getIntVal(R.id.et_scroll_x, 0)
                            val sy = getIntVal(R.id.et_scroll_y, 0)
                            overlay.setScrollPosition(sx, sy)
                        } catch (_: Exception) {}
                    }
                }, 600)
            }
        }

        // Overlay → slider/sayı senkron
        overlay.onPositionChanged = { sx, sy ->
            syncing = true
            findViewById<SeekBar>(R.id.seek_scroll_x).progress = sx.coerceIn(0, 5000)
            findViewById<EditText>(R.id.et_scroll_x).setText(sx.toString())
            findViewById<SeekBar>(R.id.seek_scroll_y).progress = sy.coerceIn(0, 10000)
            findViewById<EditText>(R.id.et_scroll_y).setText(sy.toString())
            syncing = false
        }

        try {
            val stream = contentResolver.openInputStream(uri)
            val html = stream?.bufferedReader()?.readText() ?: ""
            stream?.close()
            val basePath = uri.path?.let { java.io.File(it).parent?.let { p -> "file://$p/" } }
            wvPreview.loadDataWithBaseURL(basePath, html, "text/html", "UTF-8", null)
        } catch (_: Exception) {
            tvPreviewHint.text = getString(R.string.error)
        }
    }

    // ── SCROLL / ZOOM ──────────────────────────────────────────────────────
    private fun setupScrollZoom() {
        bindIntSeek(R.id.seek_scroll_x, R.id.et_scroll_x, 0, 5000, 0) { sx ->
            if (!syncing) overlay.setScrollPosition(sx, getIntVal(R.id.et_scroll_y, 0))
        }
        bindIntSeek(R.id.seek_scroll_y, R.id.et_scroll_y, 0, 10000, 0) { sy ->
            if (!syncing) overlay.setScrollPosition(getIntVal(R.id.et_scroll_x, 0), sy)
        }
        // Zoom: 10-500
        bindIntSeek(R.id.seek_zoom, R.id.et_zoom, 10, 500, 100)
        // Delay: 0.0-30.0 sn
        bindFloatSeek(R.id.seek_delay, R.id.et_delay, 0f, 30f, 1.5f)
    }

    // ── YENİLEME SÜRESİ ────────────────────────────────────────────────────
    private fun setupInterval() {
        val tvLabel = findViewById<TextView>(R.id.tv_interval_label)
        val seek = findViewById<SeekBar>(R.id.seek_interval) // 0-1000 → logaritmik
        val etVal = findViewById<EditText>(R.id.et_interval_val)
        val spinner = findViewById<Spinner>(R.id.spinner_unit)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, UNITS).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(1) // default: dakika

        fun formatLabel(sec: Long): String = when {
            sec < 60 -> "$sec saniye"
            sec < 3600 && sec % 60 == 0L -> "${sec / 60} dakika"
            sec % 3600 == 0L -> "${sec / 3600} saat"
            else -> "${sec / 60} dk ${sec % 60} sn"
        }

        // Slider → saniye (logaritmik: 1sn - 86400sn)
        fun progressToSec(p: Int): Long {
            val t = p / 1000.0
            return (Math.exp(t * Math.log(86400.0)) + 0.5).toLong().coerceIn(1L, 86400L)
        }
        fun secToProgress(sec: Long): Int {
            val t = Math.log(sec.toDouble()) / Math.log(86400.0)
            return (t * 1000).toInt().coerceIn(0, 1000)
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                val sec = progressToSec(p)
                intervalSec = sec
                tvLabel.text = formatLabel(sec)
                syncing = true
                val unit = spinner.selectedItemPosition
                etVal.setText(when (unit) {
                    0 -> sec.toString()
                    1 -> (sec / 60.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else String.format("%.1f", it) }
                    else -> (sec / 3600.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else String.format("%.2f", it) }
                })
                syncing = false
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        etVal.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val v = s.toString().toDoubleOrNull() ?: return
                val unit = spinner.selectedItemPosition
                val sec = when (unit) {
                    0 -> v.toLong()
                    1 -> (v * 60).toLong()
                    else -> (v * 3600).toLong()
                }.coerceAtLeast(1L)
                intervalSec = sec
                syncing = true
                tvLabel.text = formatLabel(sec)
                seek.progress = secToProgress(sec)
                syncing = false
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                if (syncing) return
                syncing = true
                etVal.setText(when (pos) {
                    0 -> intervalSec.toString()
                    1 -> (intervalSec / 60.0).let { if (it % 1 == 0.0) it.toLong().toString() else String.format("%.1f", it) }
                    else -> (intervalSec / 3600.0).let { if (it % 1 == 0.0) it.toLong().toString() else String.format("%.2f", it) }
                })
                syncing = false
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Başlangıç: 15 dakika
        intervalSec = 900L
        seek.progress = secToProgress(900L)
        etVal.setText("15")
        tvLabel.text = formatLabel(900L)
    }

    // ── BOYUT ──────────────────────────────────────────────────────────────
    private fun setupSize() {
        bindIntSeek(R.id.seek_width, R.id.et_width, 40, 500, 160)
        bindIntSeek(R.id.seek_height, R.id.et_height, 40, 800, 160)
    }

    // ── KAYDET ─────────────────────────────────────────────────────────────
    private fun setupButtons() {
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { saveAndFinish() }
    }

    private fun saveAndFinish() {
        if (selectedUri == null) {
            Toast.makeText(this, "Lütfen bir HTML dosyası seçin", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setUri(this, widgetId, selectedUri.toString())
        Prefs.setName(this, widgetId, selectedName)
        Prefs.setIntervalSec(this, widgetId, intervalSec.coerceAtLeast(1L))
        Prefs.setScrollX(this, widgetId, getIntVal(R.id.et_scroll_x, 0))
        Prefs.setScrollY(this, widgetId, getIntVal(R.id.et_scroll_y, 0))
        Prefs.setZoom(this, widgetId, getIntVal(R.id.et_zoom, 100).coerceIn(10, 500))
        Prefs.setDelaySec(this, widgetId, getFloatVal(R.id.et_delay, 1.5f).coerceIn(0f, 30f))
        Prefs.setWidthDp(this, widgetId, getIntVal(R.id.et_width, 160).coerceIn(40, 500))
        Prefs.setHeightDp(this, widgetId, getIntVal(R.id.et_height, 160).coerceIn(40, 800))

        val mgr = AppWidgetManager.getInstance(this)
        HtmlWidgetProvider.update(this, mgr, widgetId)
        HtmlWidgetProvider.scheduleAlarm(this, widgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    // ── YARDIMCI ──────────────────────────────────────────────────────────
    private fun bindIntSeek(seekId: Int, editId: Int, min: Int, max: Int, default: Int, onChange: ((Int) -> Unit)? = null) {
        val seek = findViewById<SeekBar>(seekId)
        val edit = findViewById<EditText>(editId)
        seek.max = max - min
        seek.progress = default - min
        edit.setText(default.toString())

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                val v = p + min
                syncing = true
                edit.setText(v.toString())
                syncing = false
                onChange?.invoke(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val v = s.toString().toIntOrNull()?.coerceIn(min, max) ?: return
                syncing = true
                seek.progress = v - min
                syncing = false
                onChange?.invoke(v)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun bindFloatSeek(seekId: Int, editId: Int, min: Float, max: Float, default: Float) {
        val seek = findViewById<SeekBar>(seekId)
        val edit = findViewById<EditText>(editId)
        val steps = ((max - min) * 10).toInt()
        seek.max = steps
        seek.progress = ((default - min) * 10).toInt()
        edit.setText(String.format("%.1f", default))

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                syncing = true
                edit.setText(String.format("%.1f", min + p / 10f))
                syncing = false
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val v = s.toString().toFloatOrNull()?.coerceIn(min, max) ?: return
                syncing = true
                seek.progress = ((v - min) * 10).toInt()
                syncing = false
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun getIntVal(editId: Int, default: Int) =
        findViewById<EditText>(editId).text.toString().toIntOrNull() ?: default

    private fun getFloatVal(editId: Int, default: Float) =
        findViewById<EditText>(editId).text.toString().toFloatOrNull() ?: default

    companion object { private const val REQ_FILE = 1001 }
}
