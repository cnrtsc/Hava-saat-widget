package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Sarı-kırmızı takım teması: beyaz (dalgalı şeritli) ve siyah (çapraz şeritli). Logo kullanıcı yükler. */
object GS {
    const val WHITE = 0; const val BLACK = 1
    private val RED = Color.rgb(176, 18, 44); private val RED_A = Color.rgb(196, 24, 50); private val RED_B = Color.rgb(140, 8, 34)
    private val YEL = Color.rgb(253, 190, 22); private val BORDO = Color.rgb(70, 10, 20)
    private val tr = Locale("tr", "TR")
    val LOGO_BG_NAMES = arrayOf("Beyaz daire içinde", "Dairesiz")

    fun stars(c: Context) = Cfg.bool(c, "gsStars", true)
    fun logoBg(c: Context) = Cfg.int(c, "gsLogoBg", 0).coerceIn(0, 1)
    fun logoFile(c: Context) = File(c.filesDir, "gs_logo.png")
    fun logoSize(c: Context) = Cfg.int(c, "gsLogoSize", 100).coerceIn(50, 170)

    private var logoCache: Bitmap? = null
    private var logoStamp = 0L
    private fun logo(c: Context): Bitmap? {
        val f = logoFile(c)
        if (!f.exists()) return null
        if (logoCache == null || f.lastModified() != logoStamp) { logoCache = BitmapFactory.decodeFile(f.path); logoStamp = f.lastModified() }
        return logoCache
    }

    private class X(val c: Context, val w: Weather?, val W: Float, val H: Float, val dark: Boolean,
                    val ink: Int, val sub: Int, val clock: Int, val temp: Int, val loc: Int,
                    val clockTf: Typeface, val boldTf: Typeface, val textTf: Typeface)

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, style: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt(); val Hi = (hdp.coerceIn(40, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val dark = style == BLACK
        val bg = if (dark) Color.rgb(14, 14, 16) else Color.WHITE
        Ink.setBg(bg)
        val ink = Ink.get(c, Ink.TEXT) ?: if (dark) Color.WHITE else Color.rgb(28, 22, 26)
        val sub = Ink.get(c, Ink.SUB) ?: if (dark) Color.rgb(190, 190, 198) else Color.rgb(110, 104, 108)
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val bold = Typeface.create(c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL)
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), dark, ink, sub,
            Ink.get(c, Ink.CLOCK) ?: if (dark) Color.WHITE else BORDO, Ink.get(c, Ink.TEMP) ?: if (dark) Color.WHITE else BORDO,
            Ink.get(c, Ink.LOC) ?: ink, if (hasFont) Style.clockTypeface(c, Style.font(c)) else bold, bold, Style.textTypeface(c, if (hasFont) Style.font(c) else 1))
        val ratio = x.H / x.W
        when {
            ratio < .36f -> strip(cv, x)
            ratio < .72f -> wide(cv, x)
            else -> square(cv, x)
        }
        return b
    }

    /* ---------------- ortak çizimler ---------------- */
    private fun card(cv: Canvas, x: X): Path {
        val m = min(x.W, x.H) * .02f
        val r = RectF(m, m, x.W - m, x.H - m * 1.6f)
        val rad = min(x.W, x.H) * .1f
        val p = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(if (x.dark) 120 else 60, 0, 0, 0); maskFilter = BlurMaskFilter(m * 1.4f, BlurMaskFilter.Blur.NORMAL) }
        cv.save(); cv.translate(0f, m * .6f); cv.drawPath(p, sh); cv.restore()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        if (x.dark) fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(22, 22, 24), Color.rgb(6, 6, 8), Shader.TileMode.CLAMP)
        else fill.color = Color.WHITE
        cv.drawPath(p, fill)
        return p
    }

    private fun wave(cv: Canvas, W: Float, y0: Float, amp: Float, th: Float, paint: Paint) {
        val p = Path(); val step = W / 60
        var xx = 0f
        p.moveTo(0f, y0 + amp * sin(0.0).toFloat())
        while (xx <= W + step) { val yy = y0 + amp * sin(xx / W * PI * 1.4).toFloat() - (xx / W) * (xx / W) * amp * .6f; p.lineTo(xx, yy); xx += step }
        xx = W + step
        while (xx >= -step) { val yy = y0 + th + amp * sin(xx / W * PI * 1.4).toFloat() - (xx / W) * (xx / W) * amp * .6f; p.lineTo(xx, yy); xx -= step }
        p.close(); cv.drawPath(p, paint)
    }

    private fun diag(cv: Canvas, W: Float, H: Float, startX: Float, endY: Float, off: Float, th: Float, col: Int) {
        val p = Path().apply { moveTo(startX + off, 0f); lineTo(startX + off + th, 0f); lineTo(W + th, endY + off * .5f + th * .55f); lineTo(W + th, endY + off * .5f); close() }
        cv.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    }

    private fun logoAt(cv: Canvas, x: X, cx: Float, cy: Float, r0: Float) {
        val r = r0 * logoSize(x.c) / 100f
        val withBg = logoBg(x.c) == 0
        if (withBg) {
            cv.drawCircle(cx, cy + r * .08f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0); maskFilter = BlurMaskFilter(r * .15f, BlurMaskFilter.Blur.NORMAL) })
            cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        val lg = logo(x.c)
        val inner = if (withBg) r * .8f else r
        if (lg != null) {
            val sc = min(inner * 2 / lg.width, inner * 2 / lg.height)
            val dw = lg.width * sc; val dh = lg.height * sc
            cv.drawBitmap(lg, null, RectF(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .04f; color = Color.rgb(170, 170, 178); pathEffect = android.graphics.DashPathEffect(floatArrayOf(r * .1f, r * .08f), 0f) }
            cv.drawCircle(cx, cy, inner, p)
            cv.drawText("LOGO", cx, cy + r * .1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = r * .3f; color = Color.rgb(150, 150, 160); textAlign = Paint.Align.CENTER })
        }
    }

    private fun starRow(cv: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        for (i in 0 until 4) {
            val sx = cx + (i - 1.5f) * r * 2.4f; val sy = cy - (if (i == 1 || i == 2) r * .3f else 0f)
            val path = Path()
            for (j in 0 until 10) {
                val a = -PI / 2 + j * PI / 5; val rr = if (j % 2 == 0) r else r * .42f
                val px = sx + (rr * cos(a)).toFloat(); val py = sy + (rr * sin(a)).toFloat()
                if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); cv.drawPath(path, p)
        }
    }

    private fun txt(tf: Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; color = col; textAlign = al; Ink.shadowOverride(this)
    }
    private fun fitP(p: Paint, t: String, maxW: Float): Paint { val m = p.measureText(t); if (m > maxW && m > 0) p.textSize *= maxW / m; return p }
    private fun clockP(x: X, size: Float, maxW: Float): Paint {
        val p = Lock.clockPaintTf(x.c, x.clockTf, size, x.clock, Combo.Look(0, x.clock, x.clock, x.clock, false, 0)).apply { textAlign = Paint.Align.LEFT }
        val m = Ink.measure(x.c, now("HH:mm"), p); if (m > maxW && m > 0) p.textSize *= maxW / m
        return p
    }
    private fun now(f: String) = SimpleDateFormat(f, tr).format(Date())
    private fun ic(cv: Canvas, x: X, code: Int, day: Boolean, cx: Float, cy: Float, s: Float) = Combo.icon(x.c, cv, Style.iconPref(x.c, 0, 6, code, day), cx, cy, s)

    private fun pin(cv: Canvas, x: Float, y: Float, s: Float, col: Int, hole: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        cv.drawCircle(x, y - s * 1.2f, s, p)
        cv.drawPath(Path().apply { moveTo(x - s * .9f, y - s * .9f); lineTo(x + s * .9f, y - s * .9f); lineTo(x, y + s * .7f); close() }, p)
        cv.drawCircle(x, y - s * 1.2f, s * .4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hole })
    }
    private fun calIcon(cv: Canvas, x: Float, y: Float, s: Float, col: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = s * .12f; strokeCap = Paint.Cap.ROUND }
        cv.drawRoundRect(RectF(x, y - s, x + s * 1.1f, y), s * .18f, s * .18f, p)
        cv.drawLine(x, y - s * .68f, x + s * 1.1f, y - s * .68f, p)
        cv.drawLine(x + s * .3f, y - s * 1.15f, x + s * .3f, y - s * .85f, p); cv.drawLine(x + s * .8f, y - s * 1.15f, x + s * .8f, y - s * .85f, p)
    }
    private fun glyph(cv: Canvas, kind: Int, x: Float, y: Float, s: Float, col: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = s * .1f; strokeCap = Paint.Cap.ROUND }
        when (kind) {
            0 -> { // yağış
                cv.drawArc(RectF(x + s * .07f, y - s * .83f, x + s * .63f, y - s * .27f), 150f, 200f, false, p)
                cv.drawArc(RectF(x + s * .34f, y - s * 1.06f, x + s * 1.06f, y - s * .34f), 180f, 170f, false, p)
                cv.drawLine(x + s * .1f, y - s * .28f, x + s * 1.2f, y - s * .28f, p)
                val bp = Paint(p).apply { color = Color.rgb(60, 150, 230) }
                for (k in 0 until 3) cv.drawLine(x + s * (.4f + k * .3f), y - s * .05f, x + s * (.32f + k * .3f), y + s * .25f, bp)
            }
            1 -> { // rüzgâr
                cv.drawLine(x, y - s * .65f, x + s, y - s * .65f, p); cv.drawLine(x, y - s * .38f, x + s * 1.3f, y - s * .38f, p); cv.drawLine(x, y - s * .1f, x + s * .8f, y - s * .1f, p)
                cv.drawArc(RectF(x + s * .85f, y - s * .95f, x + s * 1.25f, y - s * .55f), 180f, 270f, false, p)
            }
            else -> { // nem
                cv.drawPath(Path().apply { moveTo(x + s * .5f, y - s * 1.15f); quadTo(x + s * .95f, y - s * .45f, x + s * .85f, y - s * .25f)
                    quadTo(x + s * .5f, y + s * .25f, x + s * .15f, y - s * .25f); quadTo(x + s * .05f, y - s * .45f, x + s * .5f, y - s * 1.15f); close() }, p)
            }
        }
    }
    /** Yağış · rüzgâr · nem satırı; çizilen genişliği döndürür. */
    private fun stats(cv: Canvas, x: X, sx: Float, y: Float, s: Float, col: Int, sep: Int, maxW: Float) {
        val w = x.w
        val items = listOf(0 to "%${w?.rainChance ?: 0}", 1 to "${w?.wind ?: 0} km/sa", 2 to "%${w?.humidity ?: 0}")
        val tp = txt(x.textTf, s * .78f, col)
        var total = 0f
        for ((i, it) in items.withIndex()) total += s * 1.45f + tp.measureText(it.second) + if (i < 2) s * .7f else 0f
        val sc = if (total > maxW) maxW / total else 1f
        val ss = s * sc; tp.textSize *= sc
        var xx = sx
        for ((i, it) in items.withIndex()) {
            glyph(cv, it.first, xx, y, ss * .9f, col)
            cv.drawText(it.second, xx + ss * 1.45f, y, tp)
            xx += ss * 1.45f + tp.measureText(it.second) + ss * .7f
            if (i < 2) cv.drawLine(xx - ss * .35f, y - ss, xx - ss * .35f, y + ss * .2f, Paint().apply { color = sep; strokeWidth = 2f })
        }
    }
    private fun dayName(d: Day): String = try {
        SimpleDateFormat("EEE", tr).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!!).take(3)
    } catch (e: Exception) { "" }

    /* ---------------- geniş (4x2) ---------------- */
    private fun wide(cv: Canvas, x: X) {
        val W = x.W; val H = x.H
        val sx = W / 1280f; val sy = H / 560f; val s = min(sx, sy)
        val clip = card(cv, x)
        cv.save(); cv.clipPath(clip)
        if (!x.dark) {
            wave(cv, W, 160 * sy, 14 * s, 20 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YEL })
            wave(cv, W, 182 * sy, 14 * s, 5 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 236, 170) })
            wave(cv, W, 190 * sy, 14 * s, 18 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, W, 0f, RED_A, RED_B, Shader.TileMode.CLAMP) })
        } else {
            diag(cv, W, H, W * .56f, H * .52f, 0f, 72 * s, Color.rgb(150, 10, 34))
            diag(cv, W, H, W * .56f, H * .52f, 80 * s, 60 * s, YEL)
        }
        cv.restore()
        val L = 92 * sx
        // konum
        if (x.dark) cv.drawLine(72 * sx, 72 * sy, 72 * sx, 126 * sy, Paint().apply { color = RED; strokeWidth = 5 * s })
        val px = if (x.dark) 108 * sx else L
        val pyBase = if (x.dark) 108 * sy else 96 * sy
        pin(cv, px, pyBase, 15 * s, if (x.dark) x.loc else Ink.get(x.c, Ink.LOC) ?: Color.rgb(120, 6, 26), if (x.dark) Color.rgb(12, 12, 14) else Color.WHITE)
        val showLoc = Combo.show(x.c, "loc")
        val desc = x.w?.let { WeatherRepo.label(it.code) } ?: "Yükleniyor…"
        if (showLoc) cv.drawText(WeatherRepo.city(x.c), px + 28 * s, pyBase + 2 * sy, fitP(txt(x.boldTf, 48 * s, x.loc), WeatherRepo.city(x.c), W * .45f))
        cv.drawText(desc, px + 32 * s, pyBase + 38 * sy, txt(x.textTf, 26 * s, x.sub))
        // saat
        val clockY = if (x.dark) 336 * sy else 368 * sy
        val cp = clockP(x, 164 * s, ((if (x.dark) 612 else 620) - (if (x.dark) 104 else 110) - 34) * sx)
        Ink.time(x.c, cv, now("HH:mm"), (if (x.dark) 104 else 110) * sx, clockY, cp)
        val dateY = clockY + 44 * sy
        calIcon(cv, 214 * sx, dateY, 26 * s, x.sub)
        cv.drawText(now("d MMMM EEEE"), 258 * sx, dateY, fitP(txt(x.textTf, 28 * s, x.sub), now("d MMMM EEEE"), 330 * sx))
        // ayraç
        val divX = (if (x.dark) 612 else 620) * sx
        cv.drawLine(divX, clockY - 136 * sy, divX, clockY + 40 * sy, Paint().apply { color = if (x.dark) YEL else Color.rgb(220, 214, 216); strokeWidth = (if (x.dark) 3 else 2) * s })
        // hava
        val w = x.w
        val iconY = clockY - 78 * sy
        if (w != null) ic(cv, x, w.code, w.isDay, divX + 92 * sx, iconY, 98 * s)
        val tStr = if (w != null) "${w.temp}°" else "--°"
        cv.drawText(tStr, divX + 162 * sx, iconY + 26 * sy, txt(x.clockTf, 76 * s * Ink.tempScale(x.c), x.temp))
        val today = w?.days?.firstOrNull()
        if (today != null) cv.drawText("Y ${today.max}°   D ${today.min}°", divX + 166 * sx, iconY + 66 * sy, txt(x.textTf, 26 * s, x.sub))
        stats(cv, x, divX + 44 * sx, dateY, 26 * s, if (x.dark) Color.rgb(220, 220, 226) else Color.rgb(70, 64, 70),
            if (x.dark) Color.rgb(80, 80, 86) else Color.rgb(220, 214, 216), W - divX - 200 * sx)
        // logo + yıldızlar
        val lx = W - 140 * sx; val ly = if (x.dark) 156 * sy else 186 * sy
        logoAt(cv, x, lx, ly, 64 * s)
        if (stars(x.c)) starRow(cv, lx, ly - 64 * s * logoSize(x.c) / 100f - 28 * s, 12 * s, if (x.dark) YEL else Color.rgb(120, 6, 26))
        // tahmin
        val days = w?.days?.drop(1)?.take(3) ?: emptyList()
        if (!x.dark) {
            val pill = RectF(70 * sx, H - 132 * sy, W - 70 * sx, H - 52 * sy)
            cv.drawRoundRect(pill, 40 * s, 40 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(pill.left, 0f, pill.right, 0f, Color.rgb(200, 28, 56), RED_B, Shader.TileMode.CLAMP) })
            forecast(cv, x, days, pill.left, pill.right, H - 80 * sy, s, Color.WHITE, Color.rgb(255, 205, 210), Color.argb(110, 255, 255, 255), pill.top + 14 * sy, pill.bottom - 14 * sy)
        } else {
            val lineY = 420 * sy
            cv.drawRect(72 * sx, lineY, W - 72 * sx, lineY + 3 * s, Paint().apply { shader = LinearGradient(72 * sx, 0f, W - 72 * sx, 0f, YEL, RED, Shader.TileMode.CLAMP) })
            forecast(cv, x, days, 72 * sx, W - 72 * sx, 486 * sy, s, Color.WHITE, Color.rgb(150, 150, 156), Color.rgb(70, 70, 76), 450 * sy, 500 * sy)
        }
    }

    private fun forecast(cv: Canvas, x: X, days: List<Day>, l: Float, r: Float, y: Float, s: Float, hiCol: Int, loCol: Int, sepCol: Int, sepT: Float, sepB: Float) {
        if (days.isEmpty()) return
        val cw = (r - l) / days.size
        for ((i, d) in days.withIndex()) {
            val x0 = l + i * cw; val cx = x0 + cw / 2
            val dp = txt(x.boldTf, 28 * s, hiCol); val hp = txt(x.boldTf, 28 * s, hiCol); val lp = txt(x.textTf, 28 * s, loCol)
            val dn = dayName(d); val hi = "${d.max}°"; val lo = "${d.min}°"
            val iconS = 50 * s; val gap = 12 * s
            val total = dp.measureText(dn) + gap + iconS + gap + hp.measureText(hi) + gap * .8f + lp.measureText(lo)
            var xx = cx - total / 2
            cv.drawText(dn, xx, y, dp); xx += dp.measureText(dn) + gap
            ic(cv, x, d.code, true, xx + iconS / 2, y - 12 * s, iconS); xx += iconS + gap
            cv.drawText(hi, xx, y, hp); xx += hp.measureText(hi) + gap * .8f
            cv.drawText(lo, xx, y, lp)
            if (i > 0) cv.drawLine(x0, sepT, x0, sepB, Paint().apply { color = sepCol; strokeWidth = 2f })
        }
    }

    /* ---------------- kare (2x2) ---------------- */
    private fun square(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = min(W, H) / 560f
        val clip = card(cv, x)
        cv.save(); cv.clipPath(clip)
        if (!x.dark) {
            wave(cv, W, H * .35f, 12 * s, 16 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YEL })
            wave(cv, W, H * .35f + 22 * s, 12 * s, 14 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, W, 0f, RED_A, RED_B, Shader.TileMode.CLAMP) })
        } else {
            diag(cv, W, H, W * .4f, H * .5f, 0f, 36 * s * 1.4f, Color.rgb(150, 10, 34))
            diag(cv, W, H, W * .4f, H * .5f, 46 * s * 1.4f, 32 * s * 1.4f, YEL)
        }
        cv.restore()
        val L = 60 * s * (W / H).coerceAtMost(1.4f)
        pin(cv, L + 14 * s, 100 * s, 13 * s, x.loc, if (x.dark) Color.rgb(12, 12, 14) else Color.WHITE)
        if (Combo.show(x.c, "loc")) cv.drawText(WeatherRepo.city(x.c), L + 38 * s, 102 * s, fitP(txt(x.boldTf, 40 * s, x.loc), WeatherRepo.city(x.c), W * .5f))
        logoAt(cv, x, W - 112 * s, 130 * s, 58 * s)
        if (stars(x.c)) starRow(cv, W - 112 * s, 130 * s - 58 * s * logoSize(x.c) / 100f - 20 * s, 9 * s, if (x.dark) YEL else Color.rgb(120, 6, 26))
        Ink.time(x.c, cv, now("HH:mm"), L, H * .68f, clockP(x, 132 * s, W - L * 2))
        val w = x.w
        if (w != null) ic(cv, x, w.code, w.isDay, L + 36 * s, H * .83f, 70 * s)
        val tp = txt(x.clockTf, 54 * s * Ink.tempScale(x.c), x.temp)
        val tStr = if (w != null) "${w.temp}°" else "--°"
        cv.drawText(tStr, L + 80 * s, H * .87f, tp)
        val desc = w?.let { WeatherRepo.label(it.code) } ?: ""
        val dx = L + 80 * s + tp.measureText(tStr) + 16 * s
        cv.drawText(desc, dx, H * .87f, fitP(txt(x.textTf, 28 * s, x.sub), desc, W - dx - L * .6f))
    }

    /* ---------------- ince şerit (4x1) ---------------- */
    private fun strip(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = H / 240f; val sx = W / 1280f
        val clip = card(cv, x)
        cv.save(); cv.clipPath(clip)
        if (x.dark) {
            cv.drawPath(Path().apply { moveTo(W - 330 * s, H); lineTo(W - 290 * s, H); lineTo(W - 170 * s, 0f); lineTo(W - 210 * s, 0f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 10, 34) })
            cv.drawPath(Path().apply { moveTo(W - 280 * s, H); lineTo(W - 246 * s, H); lineTo(W - 126 * s, 0f); lineTo(W - 160 * s, 0f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YEL })
        } else {
            val bot = H - min(W, H) * .032f
            cv.drawRect(0f, bot - 18 * s, W, bot - 9 * s, Paint().apply { color = YEL })
            cv.drawRect(0f, bot - 9 * s, W, bot, Paint().apply { color = RED })
        }
        cv.restore()
        val L = 70 * sx
        pin(cv, L + 12 * s, 102 * s, 12 * s, x.loc, if (x.dark) Color.rgb(12, 12, 14) else Color.WHITE)
        val city = WeatherRepo.city(x.c)
        if (Combo.show(x.c, "loc")) cv.drawText(city, L + 34 * s, 104 * s, fitP(txt(x.boldTf, 34 * s, x.loc), city, W * .3f - L - 50 * s))
        cv.drawText(now("d MMMM EEEE"), L + 36 * s, 144 * s, fitP(txt(x.textTf, 24 * s, x.sub), now("d MMMM EEEE"), W * .3f - L - 50 * s))
        Ink.time(x.c, cv, now("HH:mm"), W * .32f, 160 * s, clockP(x, 112 * s, W * .27f))
        val w = x.w
        if (w != null) ic(cv, x, w.code, w.isDay, W * .62f, 118 * s, 70 * s)
        cv.drawText(if (w != null) "${w.temp}°" else "--°", W * .62f + 44 * s, 140 * s, txt(x.clockTf, 60 * s * Ink.tempScale(x.c), x.temp))
        logoAt(cv, x, W - 112 * s, H / 2, 58 * s)
    }
}
