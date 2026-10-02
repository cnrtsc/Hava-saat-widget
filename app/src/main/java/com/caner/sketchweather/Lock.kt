package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Kilit ekranı tarzı: üstte tarih + hava satırı, altında büyük saat (isteğe bağlı çerçeve). */
object Lock {
    fun clockPaint(c: Context, size: Float, color: Int, lk: Combo.Look): Paint {
        val p = Combo.text(Style.clockTypeface(c, Style.font(c)), size, color, lk)
        applyFx(p, Style.clockFx(c), size, color)
        return p
    }

    /** Saat efektleri: tek bir Paint ayarıyla çizilebilen görünümler. */
    fun applyFx(p: Paint, fx: Int, size: Float, color: Int) {
        when (fx) {
            1 -> { p.style = Paint.Style.STROKE; p.strokeWidth = size * .028f; p.strokeJoin = Paint.Join.ROUND }
            2 -> { p.style = Paint.Style.STROKE; p.strokeWidth = size * .065f; p.strokeJoin = Paint.Join.ROUND }
            3 -> p.setShadowLayer(size * .08f, size * .03f, size * .06f, 0xB0000000.toInt())
            4 -> p.setShadowLayer(size * .22f, 0f, 0f, color)
            5 -> p.setShadowLayer(size * .14f, 0f, 0f, 0xCCFFFFFF.toInt())
            6 -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = size * .04f; p.strokeCap = Paint.Cap.ROUND
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(size * .09f, size * .06f), 0f)
            }
            7 -> { p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = size * .035f; p.strokeJoin = Paint.Join.ROUND }
            8 -> { p.alpha = 150; p.setShadowLayer(size * .05f, 0f, size * .03f, 0x80000000.toInt()) }
        }
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, compact: Boolean): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(120, 560) * k).toInt()
        val H = (hdp.coerceIn(40, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val lk = Combo.look(c, Palette.get(c), 0)
        val tr = Locale("tr", "TR")
        val tf = Style.textTypeface(c, Style.font(c))
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.fill }
        cv.drawRoundRect(RectF(4 * k, 4 * k, W - 4 * k, H - 4 * k), 24 * k, 24 * k, bgP)
        val time = SimpleDateFormat("HH:mm", tr).format(Date())
        val dateStr = SimpleDateFormat(if (compact) "EEEE, d MMMM" else "EEE d", tr).format(Date())
        val temp = if (w != null) "${w.temp}°" else "--°"
        if (!compact) {
            // üst satır: tarih  ikon  derece
            val ts = H * .11f
            val tp = Combo.text(tf, ts, lk.ink, lk, Paint.Align.LEFT)
            val gap = ts * .5f
            val iw = ts * 1.25f
            val total = tp.measureText(dateStr) + gap + iw + ts * .2f + tp.measureText(temp)
            var x = W / 2f - total / 2
            val y = H * .22f
            cv.drawText(dateStr, x, y, tp); x += tp.measureText(dateStr) + gap
            if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), x + iw / 2, y - ts * .35f, iw)
            x += iw + ts * .2f
            cv.drawText(temp, x, y, tp)
            // büyük saat
            var size = H * .5f
            var cp = clockPaint(c, size, lk.ink, lk)
            val maxW = W * .8f
            if (cp.measureText(time) > maxW) { size *= maxW / cp.measureText(time); cp = clockPaint(c, size, lk.ink, lk) }
            val fm = cp.fontMetrics
            val cy = H * .63f
            val base = cy - (fm.ascent + fm.descent) / 2
            cv.drawText(time, W / 2f, base, cp)
            if (Combo.rim(c)) {
                val tw = cp.measureText(time)
                val r = RectF(W / 2f - tw / 2 - size * .22f, cy - size * .52f, W / 2f + tw / 2 + size * .22f, cy + size * .52f)
                cv.drawRoundRect(r, size * .18f, size * .18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = size * .018f; color = lk.ink; alpha = 150
                })
            }
        } else {
            val size = min(H * .62f, W * .19f)
            val cp = clockPaint(c, size, lk.ink, lk).apply { textAlign = Paint.Align.LEFT }
            val fm = cp.fontMetrics
            cv.drawText(time, 16 * k, H / 2f - (fm.ascent + fm.descent) / 2, cp)
            val xr = W - 16 * k
            val tp = Combo.text(tf, H * .2f, lk.sub, lk, Paint.Align.RIGHT)
            cv.drawText(dateStr, xr, H * .40f, tp)
            val tp2 = Combo.text(tf, H * .24f, lk.ink, lk, Paint.Align.RIGHT)
            cv.drawText(temp, xr, H * .76f, tp2)
            if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), xr - tp2.measureText(temp) - H * .2f, H * .68f, H * .3f)
        }
        return b
    }
}
