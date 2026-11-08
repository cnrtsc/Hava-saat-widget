package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Tebeşir tahtası widget'ları: tebeşir ikonlar, el yazısı ve tebeşir dokulu yazılar. */
object Chalk {
    const val SLATE = 0; const val GREEN = 1; const val KRAFT = 2
    const val WIDE = 0; const val SQUARE = 1; const val STRIP = 2
    private val tr = Locale("tr")
    private const val YEL = 0xFFF5C83C.toInt(); private const val RED = 0xFFDC4646.toInt()

    private class Ctx(val c: Context, val cv: Canvas, val W: Float, val H: Float, val u: Float, val sx: Float, val sy: Float,
                      val ink: Int, val sub: Int, val clockTf: Typeface, val textTf: Typeface, val shadow: Boolean, val isc: Float, val shape: Int)

    private val bmpCache = HashMap<Int, Bitmap>()

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, theme: Int, form: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt(); val Hi = (hdp.coerceIn(40, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = TCanvas(b)
        val W = Wi.toFloat(); val H = Hi.toFloat()
        val (bw, bh) = when (form) { SQUARE -> 170f to 170f; STRIP -> 360f to 90f; else -> 360f to 190f }
        val sx = W / bw; val sy = H / bh; val u = min(sx, sy)
        Tex.suggest(c, 1, 1)
        val op = Style.opacity(c) / 100f
        val light = theme == KRAFT
        val bgRes = when (theme) { GREEN -> R.drawable.bg_green; KRAFT -> R.drawable.bg_kraft; else -> R.drawable.bg_slate }
        val rad = 22 * u
        val card = RectF(0f, 0f, W, H)
        if (op > .02f) {
            val clip = Path().apply { addRoundRect(card, rad, rad, Path.Direction.CW) }
            cv.save(); cv.clipPath(clip)
            val bm = bmpCache.getOrPut(bgRes) { BitmapFactory.decodeResource(c.resources, bgRes, BitmapFactory.Options().apply { inScaled = false }) }
            val sc = max(W / bm.width, H / bm.height); val dw = bm.width * sc; val dh = bm.height * sc
            cv.drawBitmap(bm, null, RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (op * 255).toInt() })
            cv.restore()
            Ink.setBg(if (light) Color.rgb(196, 160, 116) else if (theme == GREEN) Color.rgb(34, 66, 52) else Color.rgb(34, 36, 38))
        } else Ink.setBg(null)
        val ink = Ink.get(c, Ink.TEXT) ?: Style.textColor(c) ?: if (light) Color.rgb(40, 34, 30) else Color.rgb(242, 240, 232)
        val sub = Ink.get(c, Ink.SUB) ?: if (light) Color.rgb(92, 74, 56) else Color.rgb(204, 204, 198)
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val hasText = id != null && (pr.contains(Cfg.wkey(id, "textFont")) || hasFont)
        val clockTf = if (hasFont) Style.clockTypeface(c, Style.font(c)) else c.resources.getFont(R.font.f_sketch_c)
        val textTf = if (hasText) Style.textTypeface(c, Style.font(c)) else c.resources.getFont(R.font.f_patrick_c)
        val shape = when (theme) { GREEN -> 29; KRAFT -> 31; else -> 30 }
        val x = Ctx(c, cv, W, H, u, sx, sy, ink, sub, clockTf, textTf, op < .45f, Style.iconScale(c), shape)
        if (op > .02f) frame(x, theme)
        when (form) { SQUARE -> square(x, w); STRIP -> strip(x, w); else -> wide(x, w) }
        return b
    }

    /* ---------- yardımcılar ---------- */
    private fun p(x: Ctx, tf: Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size * x.u * Combo.textMul * Style.textScale(x.c); color = col; textAlign = al
        if (x.shadow) setShadowLayer(textSize * .08f, 0f, textSize * .04f, 0xA0000000.toInt())
        Ink.shadowOverride(this)
    }
    private fun fit(pt: Paint, t: String, maxW: Float) { val m = pt.measureText(t); if (m > maxW && m > 0) pt.textSize *= maxW / m }
    private fun T(x: Ctx, t: String, xx: Float, yy: Float, tf: Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f): Float {
        if (t.isEmpty()) return 0f
        val pt = p(x, tf, size, col, al); if (maxW > 0) fit(pt, t, maxW); x.cv.drawText(t, xx, yy, pt); return pt.measureText(t)
    }
    private fun clockPaint(x: Ctx, size: Float, maxW: Float, time: String, al: Paint.Align): Paint {
        val col = Ink.get(x.c, Ink.CLOCK) ?: x.ink
        val pt = Lock.clockPaintTf(x.c, x.clockTf, size * x.u, col, Combo.Look(0, col, col, col, x.shadow, 0)).apply { textAlign = al }
        val m = Ink.measure(x.c, time, pt); if (m > maxW && m > 0) pt.textSize *= maxW / m
        return pt
    }
    private fun now(f: String) = SimpleDateFormat(f, tr).format(Date())
    private fun date(x: Ctx, f: String) = if (Combo.show(x.c, "date")) now(f) else ""
    private fun desc(x: Ctx, w: Weather?) = if (!Combo.show(x.c, "desc")) "" else w?.let { WeatherRepo.label(it.code) } ?: ""
    private fun dayName(d: Day) = try { SimpleDateFormat("EEE", tr).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!!).take(3) } catch (e: Exception) { "" }
    private fun icon(x: Ctx, code: Int, day: Boolean, cx: Float, cy: Float, boxDp: Float) {
        val box = boxDp * x.u; val drawn = min(box * x.isc, box * 1.25f)
        Combo.icon(x.c, x.cv, Style.iconPref(x.c, x.shape, 0, code, day), cx, cy, drawn / x.isc)
    }
    /** Kenar boyu çizilmiş tebeşir çerçeve. */
    private fun frame(x: Ctx, theme: Int) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * x.u; color = if (theme == KRAFT) 0x40281E14 else 0x38FFFFFF }
        val m = 7 * x.u
        x.cv.drawRoundRect(RectF(m, m, x.W - m, x.H - m), 16 * x.u, 16 * x.u, pt)
    }
    /** Elle çekilmiş sarı + kırmızı alt çizgi. */
    private fun underline(x: Ctx, x0: Float, x1: Float, y: Float) {
        if (lineOff(x)) return
        fun wave(col: Int, dy: Float, ph: Float, wd: Float) {
            val path = Path(); val n = 24
            for (i in 0..n) {
                val t = i / n.toFloat(); val xx = x0 + (x1 - x0) * t
                val yy = y + dy + sin(t * 9f + ph) * 1.1f * x.u - t * 1.5f * x.u
                if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            x.cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = wd * x.u; color = col })
        }
        wave(Ink.get(x.c, Ink.ACC) ?: YEL, 0f, 0f, 2.6f); wave(RED, 4.2f * x.u, 1.7f, 2f)
    }
    /** Derece: rakamlar saat yazı tipiyle, ° işareti yazı tipiyle (el yazısı fontlarda ° bozuk görünebiliyor). */
    private fun temp(x: Ctx, w: Weather?, tX: Float, base: Float, size: Float, maxW: Float) {
        val num = if (w != null) "${w.temp}" else "--"
        val tp = clockPaint(x, size * Ink.tempScale(x.c), maxW * .8f, num, Paint.Align.LEFT).apply { color = Ink.get(x.c, Ink.TEMP) ?: color }
        x.cv.drawText(num, tX, base, tp)
        val dp = p(x, x.textTf, 1f, tp.color).apply { textSize = tp.textSize * .62f }
        x.cv.drawText("°", tX + tp.measureText(num) + tp.textSize * .04f, base - tp.textSize * .28f, dp)
    }
    private fun lineOff(x: Ctx) = Cfg.int(x.c, "chalkLine", 0) == 1
    private fun extras(x: Ctx, w: Weather?): String {
        if (w == null) return ""
        val out = ArrayList<String>()
        if (Combo.show(x.c, "feels") && w.feels != null) out.add("His ${w.feels}°")
        val d0 = w.days.firstOrNull()
        if (Combo.show(x.c, "hl") && d0 != null) out.add("${d0.max}° / ${d0.min}°")
        if (Combo.show(x.c, "rain") && w.rainChance != null) out.add("Yağış %${w.rainChance}")
        return out.joinToString("  ·  ")
    }

    /* ---------- 4x2 ---------- */
    private fun wide(x: Ctx, w: Weather?) {
        val sx = x.sx; val sy = x.sy; val u = x.u
        val time = now("HH:mm")
        val leftW = 168 * sx
        if (Combo.show(x.c, "loc")) T(x, WeatherRepo.city(x.c), 20 * sx, 36 * sy, x.textTf, 19f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = leftW)
        val cp = clockPaint(x, 62f, leftW, time, Paint.Align.LEFT)
        Ink.time(x.c, x.cv, time, 18 * sx, 100 * sy, cp)
        val cw = min(Ink.measure(x.c, time, cp), leftW)
        underline(x, 20 * sx, 20 * sx + cw, 111 * sy)
        T(x, date(x, "d MMMM EEEE"), 20 * sx, 136 * sy, x.textTf, 15f, x.sub, maxW = leftW)
        T(x, extras(x, w), 20 * sx, 160 * sy, x.textTf, 12.5f, x.sub, maxW = leftW)
        // ayraç
        x.cv.drawLine(194 * sx, 26 * sy, 193 * sx, 164 * sy, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f * u; color = x.sub; alpha = 90 })
        // sağ: ikon kutusu, derece kutunun dışından başlar
        val icx = 238 * sx; val icy = 58 * sy; val ib = min(66f, 62f * sy / u)
        if (w != null) icon(x, w.code, w.isDay, icx, icy, ib)
        val tX = icx + ib * u * min(x.isc, 1.25f) / 2 + 6 * u
        temp(x, w, tX, 78 * sy, 44f, 344 * sx - tX)
        T(x, desc(x, w), 272 * sx, 108 * sy, x.textTf, 15f, x.ink, Paint.Align.CENTER, 146 * sx)
        if (Combo.show(x.c, "fc") && w != null) {
            w.days.drop(1).take(3).forEachIndexed { i, d ->
                val y = (130 + i * 20) * sy
                T(x, dayName(d), 206 * sx, y + 5 * sy, x.textTf, 14f, x.ink, maxW = 34 * sx)
                icon(x, d.code, true, 254 * sx, y, 20f)
                T(x, "${d.max}° / ${d.min}°", 344 * sx, y + 5 * sy, x.textTf, 14f, x.sub, Paint.Align.RIGHT, 72 * sx)
            }
        }
    }

    /* ---------- 2x2 ---------- */
    private fun square(x: Ctx, w: Weather?) {
        val sx = x.sx; val sy = x.sy; val u = x.u
        val time = now("HH:mm")
        if (Combo.show(x.c, "loc")) T(x, WeatherRepo.city(x.c), 85 * sx, 28 * sy, x.textTf, 16f, Ink.get(x.c, Ink.LOC) ?: x.ink, Paint.Align.CENTER, 140 * sx)
        val ib = 52f; val icx = 52 * sx; val icy = 66 * sy
        if (w != null) icon(x, w.code, w.isDay, icx, icy, ib)
        val tX = icx + ib * u * min(x.isc, 1.25f) / 2 + 5 * u
        temp(x, w, tX, 80 * sy, 36f, 156 * sx - tX)
        val cp = clockPaint(x, 46f, 146 * sx, time, Paint.Align.CENTER)
        Ink.time(x.c, x.cv, time, 85 * sx, 132 * sy, cp)
        val cw = min(Ink.measure(x.c, time, cp), 146 * sx)
        underline(x, 85 * sx - cw / 2, 85 * sx + cw / 2, 141 * sy)
        T(x, date(x, "d MMM EEE"), 85 * sx, 160 * sy, x.textTf, 13f, x.sub, Paint.Align.CENTER, 146 * sx)
    }

    /* ---------- 4x1 ---------- */
    private fun strip(x: Ctx, w: Weather?) {
        val sx = x.sx; val sy = x.sy; val u = x.u
        val time = now("HH:mm")
        val ib = min(48f, 62f * sy / u); val icx = 40 * sx; val icy = 45 * sy
        if (w != null) icon(x, w.code, w.isDay, icx, icy, ib)
        val tX = icx + ib * u * min(x.isc, 1.25f) / 2 + 5 * u
        temp(x, w, tX, 56 * sy, 30f, 140 * sx - tX)
        x.cv.drawLine(146 * sx, 22 * sy, 145 * sx, 68 * sy, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f * u; color = x.sub; alpha = 90 })
        val loc = if (Combo.show(x.c, "loc")) WeatherRepo.city(x.c) else ""
        val dt = date(x, "d MMM EEE")
        val rp = p(x, x.textTf, 15f, x.ink); val rw = min(max(rp.measureText(loc), p(x, x.textTf, 13f, x.sub).measureText(dt)), 100 * sx)
        T(x, loc, 344 * sx, 40 * sy, x.textTf, 15f, Ink.get(x.c, Ink.LOC) ?: x.ink, Paint.Align.RIGHT, 100 * sx)
        T(x, dt, 344 * sx, 62 * sy, x.textTf, 13f, x.sub, Paint.Align.RIGHT, 100 * sx)
        val avail = 344 * sx - rw - 14 * u - 158 * sx
        val cp = clockPaint(x, 46f, avail, time, Paint.Align.LEFT)
        Ink.time(x.c, x.cv, time, 158 * sx, 60 * sy, cp)
        val cw = min(Ink.measure(x.c, time, cp), avail)
        underline(x, 160 * sx, 160 * sx + cw, 69 * sy)
    }
}
