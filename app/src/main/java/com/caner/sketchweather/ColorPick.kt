package com.caner.sketchweather

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Renk seçici: doygunluk/parlaklık karesi, renk tonu çubuğu, HEX alanı, duvar kağıdı / son kullanılan / hazır renkler. */
object ColorPick {
    private val PRESETS = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFF3E6CF.toInt(), 0xFFE8EDF5.toInt(), 0xFFB8C0CC.toInt(), 0xFF5D6170.toInt(), 0xFF1E2230.toInt(),
        0xFFFF9F43.toInt(), 0xFFFF6B6B.toInt(), 0xFFF2E85C.toInt(), 0xFFFFD25C.toInt(), 0xFF5EE6B5.toInt(), 0xFFB5D68A.toInt(),
        0xFF7CC7FF.toInt(), 0xFF8FA8FF.toInt(), 0xFFB79CFF.toInt(), 0xFFFF4FD8.toInt(), 0xFFFFB3C7.toInt(), 0xFFC4452E.toInt()
    )

    /** onPick(null) = otomatik (tasarımın rengi). */
    fun show(a: Activity, title: String, current: Int?, onPick: (Int?) -> Unit) {
        val dp = { v: Int -> Ui.dp(a, v) }
        val hsv = FloatArray(3)
        Color.colorToHSV(current ?: Color.WHITE, hsv)
        var updating = false

        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(4)) }
        val swatch = View(a).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)) }
        val hex = EditText(a).apply {
            setTextColor(Ui.FG); textSize = 18f; setSingleLine(); filters = arrayOf(InputFilter.LengthFilter(7))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) }
        }
        val sv = SVView(a); val hue = HueView(a)
        fun col() = Color.HSVToColor(hsv)
        fun refresh(fromHex: Boolean = false) {
            swatch.background = GradientDrawable().apply { setColor(col()); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.argb(80, 255, 255, 255)) }
            sv.hue = hsv[0]; sv.s = hsv[1]; sv.v = hsv[2]; sv.invalidate()
            hue.hue = hsv[0]; hue.invalidate()
            if (!fromHex) { updating = true; hex.setText(String.format("#%06X", col() and 0xFFFFFF)); updating = false }
        }
        sv.onChange = { s, v -> hsv[1] = s; hsv[2] = v; refresh() }
        hue.onChange = { h -> hsv[0] = h; refresh() }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (updating) return
                val t = e.toString().trim().removePrefix("#")
                if (t.length == 6) try { Color.colorToHSV(Color.parseColor("#$t"), hsv); refresh(true) } catch (x: Exception) { }
            }
        })
        fun pickFrom(colr: Int) { Color.colorToHSV(colr, hsv); refresh() }

        root.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            addView(swatch); addView(hex)
        })
        root.addView(sv.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(170)).apply { topMargin = dp(10) } })
        root.addView(hue.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(30)).apply { topMargin = dp(10) } })

        fun swRow(label: String, cols: IntArray) {
            if (cols.isEmpty()) return
            root.addView(TextView(a).apply { text = label; textSize = 12f; setTextColor(Ui.MUTED); setPadding(0, dp(10), 0, dp(4)) })
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for (cc in cols) r.addView(View(a).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(8) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cc); setStroke(dp(1), Color.argb(90, 255, 255, 255)) }
                setOnClickListener { pickFrom(cc) }
            })
            root.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(r) })
        }
        swRow("Duvar kağıdından", Ink.wallpaperSwatches(a))
        swRow("Son kullanılanlar", Ink.recent(a))
        swRow("Hazır renkler", PRESETS)
        refresh()

        val sc = android.widget.ScrollView(a).apply { addView(root) }
        AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setView(sc)
            .setPositiveButton("Tamam") { _, _ -> val cc = col(); Ink.pushRecent(a, cc); onPick(cc) }
            .setNeutralButton("Otomatik") { _, _ -> onPick(null) }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** Liste satırındaki küçük renk dairesi; renk yoksa çapraz çizgili "oto" görünümü. */
    fun dot(a: Activity, col: Int?): View = object : View(a) {
        override fun onDraw(cv: Canvas) {
            val r = minOf(width, height) / 2f - 2
            val cx = width / 2f; val cy = height / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (col != null) { p.color = col; cv.drawCircle(cx, cy, r, p) }
            else {
                p.color = Color.rgb(46, 51, 62); cv.drawCircle(cx, cy, r, p)
                p.color = Ui.MUTED; p.strokeWidth = 3f; cv.drawLine(cx - r * .6f, cy + r * .6f, cx + r * .6f, cy - r * .6f, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.argb(110, 255, 255, 255); cv.drawCircle(cx, cy, r, p)
        }
    }

    private class SVView(c: Context) : View(c) {
        var hue = 0f; var s = 1f; var v = 1f
        var onChange: (Float, Float) -> Unit = { _, _ -> }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val sat = LinearGradient(0f, 0f, r.right, 0f, Color.WHITE, Color.HSVToColor(floatArrayOf(hue, 1f, 1f)), Shader.TileMode.CLAMP)
            val dark = LinearGradient(0f, 0f, 0f, r.bottom, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
            p.shader = ComposeShader(sat, dark, PorterDuff.Mode.MULTIPLY)
            cv.drawRoundRect(r, 24f, 24f, p); p.shader = null
            val x = s * width; val y = (1 - v) * height
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = Color.WHITE; cv.drawCircle(x, y, 18f, p)
            p.strokeWidth = 2f; p.color = Color.BLACK; cv.drawCircle(x, y, 22f, p); p.style = Paint.Style.FILL
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            s = (e.x / width).coerceIn(0f, 1f); v = 1 - (e.y / height).coerceIn(0f, 1f)
            onChange(s, v); return true
        }
    }

    private class HueView(c: Context) : View(c) {
        var hue = 0f
        var onChange: (Float) -> Unit = {}
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val cols = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
            p.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, cols, null, Shader.TileMode.CLAMP)
            cv.drawRoundRect(RectF(0f, height * .2f, width.toFloat(), height * .8f), height * .3f, height * .3f, p); p.shader = null
            val x = hue / 360f * width
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = Color.WHITE; cv.drawCircle(x, height / 2f, height * .42f, p); p.style = Paint.Style.FILL
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            hue = (e.x / width).coerceIn(0f, 1f) * 359.9f; onChange(hue); return true
        }
    }
}
