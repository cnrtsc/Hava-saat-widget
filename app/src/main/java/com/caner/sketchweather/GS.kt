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
                    val clockTf: Typeface, val boldTf: Typeface, val textTf: Typeface) {
        val op = Style.opacity(c) / 100f
        val isc = Style.iconScale(c)
    }
    /** Çizim sırasında geçerli olan yazı ayarları (tek iş parçacığında çizildiği için güvenli). */
    private var lowOp = false
    private var fakeBoldTf: Typeface? = null

    /** Konum/başlık için kalın yazı tipi: kullanıcı yazı tipi seçtiyse o (kalınlaştırılmış), yoksa Outfit Bold. */
    private fun boldFor(c: Context, bold: Typeface): Typeface {
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val custom = id != null && (pr.contains(Cfg.wkey(id, "font")) || pr.contains(Cfg.wkey(id, "textFont")))
        if (!custom) { fakeBoldTf = null; return bold }
        val tf = Style.textTypeface(c, Style.font(c)); fakeBoldTf = tf; return tf
    }
    private fun begin(c: Context) {
        lowOp = Style.opacity(c) < 45
        if (lowOp) Ink.setBg(null)
    }

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
            Ink.get(c, Ink.LOC) ?: ink, if (hasFont) Style.clockTypeface(c, Style.font(c)) else bold, boldFor(c, bold), Style.textTypeface(c, if (hasFont) Style.font(c) else 1))
        begin(c)
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
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(((if (x.dark) 120 else 60) * x.op).toInt(), 0, 0, 0); maskFilter = BlurMaskFilter(m * 1.4f, BlurMaskFilter.Blur.NORMAL) }
        if (x.op > .05f) { cv.save(); cv.translate(0f, m * .6f); cv.drawPath(p, sh); cv.restore() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        if (x.dark) fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(22, 22, 24), Color.rgb(6, 6, 8), Shader.TileMode.CLAMP)
        else fill.color = Color.WHITE
        fill.alpha = (x.op * 255).toInt()
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

    private fun txt(tf: Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size * Combo.textMul; color = col; textAlign = al
        if (fakeBoldTf != null && tf === fakeBoldTf) isFakeBoldText = true
        if (lowOp) setShadowLayer(textSize * .08f, 0f, textSize * .04f, 0x99000000.toInt())
        Ink.shadowOverride(this)
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
        if (Combo.show(x.c, "date")) calIcon(cv, 214 * sx, dateY, 26 * s, x.sub)
        if (Combo.show(x.c, "date")) cv.drawText(now("d MMMM EEEE"), 258 * sx, dateY, fitP(txt(x.textTf, 28 * s, x.sub), now("d MMMM EEEE"), 330 * sx))
        // ayraç
        val divX = (if (x.dark) 612 else 620) * sx
        cv.drawLine(divX, clockY - 136 * sy, divX, clockY + 40 * sy, Paint().apply { color = if (x.dark) YEL else Color.rgb(220, 214, 216); strokeWidth = (if (x.dark) 3 else 2) * s })
        // hava
        val w = x.w
        val iconY = clockY - 78 * sy
        val iE = 98 * s * x.isc
        if (w != null) ic(cv, x, w.code, w.isDay, divX + 43 * sx + iE / 2, iconY, 98 * s)
        val tStr = if (w != null) "${w.temp}°" else "--°"
        val tX = divX + 43 * sx + iE + 20 * sx
        cv.drawText(tStr, tX, iconY + 26 * sy, fitP(txt(x.clockTf, 76 * s * Ink.tempScale(x.c), x.temp), tStr, W - 230 * sx - tX))
        val today = w?.days?.firstOrNull()
        if (today != null) cv.drawText("Y ${today.max}°   D ${today.min}°", tX + 4 * sx, iconY + 66 * sy, fitP(txt(x.textTf, 26 * s, x.sub), "Y ${today.max}°   D ${today.min}°", W - 230 * sx - tX))
        stats(cv, x, divX + 44 * sx, dateY, 26 * s, if (x.dark) Color.rgb(220, 220, 226) else Color.rgb(70, 64, 70),
            if (x.dark) Color.rgb(80, 80, 86) else Color.rgb(220, 214, 216), W - divX - 200 * sx)
        // logo + yıldızlar
        val lx = W - 140 * sx; val ly = if (x.dark) 156 * sy else 186 * sy
        logoAt(cv, x, lx, ly, 64 * s)
        // tahmin
        val days = if (Combo.show(x.c, "fc")) w?.days?.drop(1)?.take(3) ?: emptyList() else emptyList()
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
            val iconS = 50 * s * x.isc; val gap = 12 * s
            val total = dp.measureText(dn) + gap + iconS + gap + hp.measureText(hi) + gap * .8f + lp.measureText(lo)
            var xx = cx - total / 2
            cv.drawText(dn, xx, y, dp); xx += dp.measureText(dn) + gap
            ic(cv, x, d.code, true, xx + iconS / 2, y - 12 * s, 50 * s); xx += iconS + gap
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
        Ink.time(x.c, cv, now("HH:mm"), L, H * .68f, clockP(x, 132 * s, W - L * 2))
        val w = x.w
        val iE = 70 * s * x.isc
        if (w != null) ic(cv, x, w.code, w.isDay, L + iE / 2, H * .83f, 70 * s)
        val tp = txt(x.clockTf, 54 * s * Ink.tempScale(x.c), x.temp)
        val tStr = if (w != null) "${w.temp}°" else "--°"
        val tX0 = L + iE + 10 * s
        cv.drawText(tStr, tX0, H * .87f, tp)
        val desc = w?.let { WeatherRepo.label(it.code) } ?: ""
        val dx = tX0 + tp.measureText(tStr) + 16 * s
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
        if (Combo.show(x.c, "date")) cv.drawText(now("d MMMM EEEE"), L + 36 * s, 144 * s, fitP(txt(x.textTf, 24 * s, x.sub), now("d MMMM EEEE"), W * .3f - L - 50 * s))
        Ink.time(x.c, cv, now("HH:mm"), W * .32f, 160 * s, clockP(x, 112 * s, W * .27f))
        val w = x.w
        weatherIn(cv, x, W * .62f - 35 * s, 118 * s, 70 * s, 60 * s, x.temp, W * .25f)
        logoAt(cv, x, W - 112 * s, H / 2, 58 * s)
    }
    /* ================= Sade seri (v24): her öğenin kendi alanı var ================= */
    const val LINE_D = 10; const val LINE_W = 11; const val SPLIT = 12; const val SCORE = 13; const val JER_W = 14; const val JER_D = 15
    const val CLEAR = 16; const val GRAD = 17; const val CLEAN_D = 18; const val CLEAN_W = 19
    private val DARK_KINDS = setOf(LINE_D, SPLIT, SCORE, JER_D, CLEAR, GRAD, CLEAN_D)

    fun render2(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt(); val Hi = (hdp.coerceIn(40, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val dark = kind in DARK_KINDS
        Ink.setBg(when (kind) { CLEAR -> null; GRAD -> Color.rgb(160, 16, 42); else -> if (dark) Color.rgb(14, 14, 16) else Color.WHITE })
        val ink = Ink.get(c, Ink.TEXT) ?: if (dark) Color.WHITE else Color.rgb(28, 22, 26)
        val sub = Ink.get(c, Ink.SUB) ?: when (kind) { GRAD, SPLIT -> Color.rgb(255, 205, 212); CLEAR -> Color.rgb(228, 230, 236); else -> if (dark) Color.rgb(165, 165, 172) else Color.rgb(120, 112, 116) }
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val bold = Typeface.create(c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL)
        val clockCol = Ink.get(c, Ink.CLOCK) ?: if (dark) Color.WHITE else BORDO
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), dark, ink, sub, clockCol, Ink.get(c, Ink.TEMP) ?: clockCol,
            Ink.get(c, Ink.LOC) ?: ink, if (hasFont) Style.clockTypeface(c, Style.font(c)) else bold, boldFor(c, bold), Style.textTypeface(c, if (hasFont) Style.font(c) else 1))
        begin(c)
        if (kind == CLEAN_D || kind == CLEAN_W) clean(cv, x) else strip2(cv, x, kind)
        return b
    }

    private fun pill(cv: Canvas, x: X, r: RectF, rad: Float, fill: Paint) {
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(((if (x.dark) 110 else 60) * x.op).toInt(), 0, 0, 0); maskFilter = BlurMaskFilter(min(x.W, x.H) * .04f, BlurMaskFilter.Blur.NORMAL) }
        if (x.op > .05f) { cv.save(); cv.translate(0f, min(x.W, x.H) * .025f); cv.drawRoundRect(r, rad, rad, sh); cv.restore() }
        fill.alpha = (x.op * 255).toInt()
        cv.drawRoundRect(r, rad, rad, fill)
    }
    private fun underline(cv: Canvas, r: RectF, rad: Float, h: Float) {
        cv.save(); cv.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })
        cv.drawRect(r.left, r.bottom - h, r.centerX(), r.bottom, Paint().apply { color = YEL })
        cv.drawRect(r.centerX(), r.bottom - h, r.right, r.bottom, Paint().apply { color = RED })
        cv.restore()
    }
    private fun sepLine(cv: Canvas, xx: Float, y0: Float, y1: Float, col: Int, w: Float) = cv.drawLine(xx, y0, xx, y1, Paint().apply { color = col; strokeWidth = w })
    /** Saat: verilen kutuya sığacak şekilde, sol hizalı. */
    private fun clockIn(cv: Canvas, x: X, l: Float, base: Float, size: Float, maxW: Float, center: Boolean = false) {
        val p = clockP(x, size, maxW)
        if (center) { p.textAlign = Paint.Align.CENTER }
        Ink.time(x.c, cv, now("HH:mm"), l, base, p)
    }
    /** İkon + derece; sol kenardan başlar, kullanılan genişliği döndürür. */
    private fun weatherIn(cv: Canvas, x: X, l: Float, cy: Float, iconS: Float, tSize: Float, col: Int, maxW: Float): Float {
        val w = x.w
        val eff = iconS * x.isc   // ikon büyütülünce derece de sağa kayar, üst üste binmez
        if (w != null) ic(cv, x, w.code, w.isDay, l + eff / 2, cy, iconS)
        val t = if (w != null) "${w.temp}°" else "--°"
        val tp = fitP(txt(x.clockTf, tSize * Ink.tempScale(x.c), col), t, max(maxW - eff * 1.05f, tSize))
        cv.drawText(t, l + eff * 1.05f, cy + tp.textSize * .36f, tp)
        return eff * 1.05f + tp.measureText(t)
    }
    private fun placeIn(cv: Canvas, x: X, l: Float, y1: Float, y2: Float, size: Float, maxW: Float, locCol: Int, subCol: Int, second: String, pinCol: Int? = null, hole: Int = Color.BLACK) {
        val city = WeatherRepo.city(x.c)
        var xx = l
        if (pinCol != null) { pin(cv, l + size * .32f, y1 - size * .02f, size * .3f, pinCol, hole); xx += size * .78f }
        if (Combo.show(x.c, "loc")) cv.drawText(city, xx, y1, fitP(txt(x.boldTf, size, locCol), city, maxW - (xx - l)))
        cv.drawText(second, l, y2, fitP(txt(x.textTf, size * .72f, subCol), second, maxW))
    }
    private fun descShort(x: X) = if (!Combo.show(x.c, "desc")) "" else x.w?.let { WeatherRepo.label(it.code) } ?: ""
    private fun dt(x: X, f: String) = if (Combo.show(x.c, "date")) now(f) else ""

    private fun strip2(cv: Canvas, x: X, kind: Int) {
        val W = x.W; val H = x.H
        val sx = W / 1280f; val sy = H / 240f; val s = min(sx, sy)
        val r = RectF(24 * sx, 30 * sy, W - 24 * sx, H - 30 * sy)
        val rad = r.height() / 2
        val cy = H / 2
        val dateLine = listOfNotNull(dt(x, "EEE, d MMMM").takeIf { it.isNotEmpty() }, descShort(x).takeIf { it.isNotEmpty() }).joinToString(" · ")
        when (kind) {
            LINE_D, LINE_W -> {
                pill(cv, x, r, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (x.dark) Color.rgb(14, 14, 16) else Color.WHITE })
                underline(cv, r, rad, 8 * sy)
                val sepC = if (x.dark) Color.rgb(70, 70, 76) else Color.rgb(226, 220, 222)
                logoAt(cv, x, 120 * sx, cy - 4 * sy, 56 * s)
                sepLine(cv, 210 * sx, 74 * sy, 166 * sy, sepC, 2 * s)
                clockIn(cv, x, 250 * sx, 160 * sy, 104 * s, 280 * sx)
                sepLine(cv, 560 * sx, 74 * sy, 166 * sy, sepC, 2 * s)
                weatherIn(cv, x, 600 * sx, 118 * sy, 80 * s, 64 * s, x.temp, 220 * sx)
                placeIn(cv, x, 840 * sx, 110 * sy, 150 * sy, 34 * s, r.right - 840 * sx - rad * .5f, x.loc, x.sub, dateLine,
                    if (x.dark) YEL else RED, if (x.dark) Color.rgb(14, 14, 16) else Color.WHITE)
            }
            SPLIT -> {
                pill(cv, x, r, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 16, 18) })
                cv.save(); cv.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })
                cv.drawPath(Path().apply { moveTo(0f, 0f); lineTo(380 * sx, 0f); lineTo(330 * sx, H); lineTo(0f, H); close() },
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 380 * sx, 0f, Color.rgb(190, 20, 50), Color.rgb(140, 10, 36), Shader.TileMode.CLAMP) })
                cv.drawPath(Path().apply { moveTo(380 * sx, 0f); lineTo(392 * sx, 0f); lineTo(342 * sx, H); lineTo(330 * sx, H); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YEL })
                cv.restore()
                logoAt(cv, x, 120 * sx, cy, 54 * s)
                placeIn(cv, x, 196 * sx, 112 * sy, 148 * sy, 30 * s, 128 * sx, Color.WHITE, Color.rgb(255, 205, 212), dt(x, "EEE d MMM"))
                clockIn(cv, x, 430 * sx, 160 * sy, 108 * s, 400 * sx)
                weatherIn(cv, x, 860 * sx, 118 * sy, 84 * s, 66 * s, x.temp, 210 * sx)
                val d0 = x.w?.days?.firstOrNull()
                if (d0 != null) {
                    cv.drawText("Y ${d0.max}°", 1090 * sx, 112 * sy, txt(x.textTf, 26 * s, Color.rgb(200, 200, 206)))
                    cv.drawText("D ${d0.min}°", 1090 * sx, 148 * sy, txt(x.textTf, 26 * s, Color.rgb(140, 140, 148)))
                }
            }
            SCORE -> {
                val gap = 12 * sx
                val b1 = RectF(24 * sx, 40 * sy, 250 * sx, H - 40 * sy); val b2 = RectF(b1.right + gap, b1.top, 760 * sx, b1.bottom); val b3 = RectF(b2.right + gap, b1.top, W - 24 * sx, b1.bottom)
                val rr = b1.height() / 2; val small = 8 * s
                val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 0, 0, 0); maskFilter = BlurMaskFilter(14 * s, BlurMaskFilter.Blur.NORMAL) }
                cv.drawRect(RectF(b1.left + rr, b1.top + 10 * s, b3.right - rr, b1.bottom + 8 * s), sh)
                cv.drawPath(Path().apply { addRoundRect(b1, floatArrayOf(rr, rr, small, small, small, small, rr, rr), Path.Direction.CW) },
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, b1.top, 0f, b1.bottom, Color.rgb(190, 20, 50), Color.rgb(140, 10, 36), Shader.TileMode.CLAMP) })
                cv.drawRoundRect(b2, small, small, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, b2.top, 0f, b2.bottom, Color.rgb(28, 28, 32), Color.rgb(10, 10, 12), Shader.TileMode.CLAMP) })
                cv.drawPath(Path().apply { addRoundRect(b3, floatArrayOf(small, small, rr, rr, rr, rr, small, small), Path.Direction.CW) },
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, b3.top, 0f, b3.bottom, YEL, Color.rgb(240, 160, 20), Shader.TileMode.CLAMP) })
                logoAt(cv, x, b1.centerX(), cy, 50 * s)
                clockIn(cv, x, b2.centerX(), 158 * sy, 104 * s, b2.width() * .86f, center = true)
                val onY = Ink.get(x.c, Ink.TEMP) ?: Color.rgb(110, 8, 28)
                val used = weatherIn(cv, x, b3.left + 30 * sx, 116 * sy, 72 * s, 58 * s, onY, b3.width() * .5f)
                val lx = b3.left + 30 * sx + used + 22 * sx
                val city = WeatherRepo.city(x.c).uppercase(tr)
                if (Combo.show(x.c, "loc")) cv.drawText(city, lx, 108 * sy, fitP(txt(x.boldTf, 24 * s, Color.rgb(110, 8, 28)).apply { letterSpacing = .08f }, city, b3.right - rr * .4f - lx))
                cv.drawText(descShort(x), lx, 142 * sy, fitP(txt(x.textTf, 24 * s, Color.rgb(140, 60, 40)), descShort(x), b3.right - rr * .4f - lx))
            }
            JER_W, JER_D -> {
                val rad2 = 40 * s
                pill(cv, x, r, rad2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (x.dark) Color.rgb(14, 14, 16) else Color.WHITE })
                cv.save(); cv.clipPath(Path().apply { addRoundRect(r, rad2, rad2, Path.Direction.CW) })
                val sw = 22 * sx
                for (i in 0 until 4) cv.drawRect(r.left + i * sw, r.top, r.left + (i + 1) * sw, r.bottom, Paint().apply { color = if (i % 2 == 0) YEL else RED })
                cv.restore()
                clockIn(cv, x, 150 * sx, 160 * sy, 108 * s, 300 * sx)
                placeIn(cv, x, 480 * sx, 108 * sy, 146 * sy, 32 * s, 300 * sx, x.loc, x.sub, dt(x, "EEEE, d MMMM"))
                weatherIn(cv, x, 790 * sx, 118 * sy, 80 * s, 62 * s, x.temp, 260 * sx)
                logoAt(cv, x, W - 130 * sx, cy, 58 * s)
            }
            CLEAR -> {
                val shadowCol = 0x80000000.toInt()
                logoAt(cv, x, 110 * sx, cy, 60 * s)
                val p = clockP(x, 116 * s, 300 * sx).apply { setShadowLayer(8 * s, 0f, 3 * s, shadowCol) }
                Ink.time(x.c, cv, now("HH:mm"), 200 * sx, 160 * sy, p)
                val cw = Ink.measure(x.c, now("HH:mm"), p)
                cv.drawRoundRect(RectF(206 * sx, 176 * sy, 206 * sx + cw, 184 * sy), 4 * s, 4 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(206 * sx, 0f, 206 * sx + cw, 0f, YEL, RED, Shader.TileMode.CLAMP) })
                val wx = max(206 * sx + cw + 60 * sx, 580 * sx)
                weatherIn(cv, x, wx, 116 * sy, 80 * s, 64 * s, x.temp, 220 * sx)
                placeIn(cv, x, wx + 210 * sx, 108 * sy, 146 * sy, 32 * s, W - wx - 240 * sx, x.loc, x.sub, dateLine)
            }
            else -> { // GRAD
                pill(cv, x, r, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(r.left, 0f, r.right, 0f, Color.rgb(120, 8, 30), Color.rgb(200, 24, 54), Shader.TileMode.CLAMP) })
                val lr = r.height() * .38f; val lcx = r.right - rad
                cv.drawCircle(lcx, cy, lr * 1.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YEL })
                clockIn(cv, x, 84 * sx, 160 * sy, 108 * s, 290 * sx)
                sepLine(cv, 400 * sx, 80 * sy, 160 * sy, Color.argb(90, 255, 255, 255), 2 * s)
                val used = weatherIn(cv, x, 440 * sx, 116 * sy, 76 * s, 62 * s, x.temp, 200 * sx)
                val lx = 440 * sx + used + 40 * sx
                placeIn(cv, x, lx, 108 * sy, 146 * sy, 32 * s, lcx - lr * 1.3f - lx, x.loc, x.sub, dt(x, "EEE, d MMMM"))
                logoAt(cv, x, lcx, cy, lr)
            }
        }
    }

    /** Sade geniş (4x2): solda konum/saat/tarih, sağda logo/hava/tahmin; renk şeridi yalnızca altta. */
    private fun clean(cv: Canvas, x: X) {
        val W = x.W; val H = x.H
        val sx = W / 1280f; val sy = H / 560f; val s = min(sx, sy)
        val r = RectF(24 * sx, 24 * sy, W - 24 * sx, H - 24 * sy); val rad = 56 * s
        pill(cv, x, r, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (x.dark) Color.rgb(14, 14, 16) else Color.WHITE })
        underline(cv, r, rad, 14 * sy)
        val holeC = if (x.dark) Color.rgb(14, 14, 16) else Color.WHITE
        val divX = 760 * sx
        placeIn(cv, x, 80 * sx, 108 * sy, 146 * sy, 42 * s, divX - 140 * sx, x.loc, x.sub, descShort(x), if (x.dark) YEL else RED, holeC)
        clockIn(cv, x, 80 * sx, 360 * sy, 190 * s, divX - 140 * sx)
        cv.drawText(dt(x, "EEEE, d MMMM"), 88 * sx, 420 * sy, fitP(txt(x.textTf, 30 * s, x.sub), dt(x, "EEEE, d MMMM"), divX - 160 * sx))
        sepLine(cv, divX, 70 * sy, 470 * sy, if (x.dark) Color.rgb(60, 60, 66) else Color.rgb(230, 224, 226), 2 * s)
        val rc = (divX + r.right) / 2
        logoAt(cv, x, rc, 150 * sy, 80 * s)
        val used = weatherIn(cv, x, divX + 90 * sx, 330 * sy, 100 * s, 84 * s, x.temp, 260 * sx)
        val d0 = x.w?.days?.firstOrNull()
        if (d0 != null) {
            val hx = divX + 90 * sx + used + 30 * sx
            cv.drawText("Y ${d0.max}°", hx, 324 * sy, txt(x.textTf, 28 * s, x.sub)); cv.drawText("D ${d0.min}°", hx, 362 * sy, txt(x.textTf, 28 * s, x.sub))
        }
        val days = if (Combo.show(x.c, "fc")) x.w?.days?.drop(1)?.take(3) ?: emptyList() else emptyList()
        val cw = (r.right - 40 * sx - (divX + 40 * sx)) / 3
        for ((i, d) in days.withIndex()) {
            val cx = divX + 40 * sx + cw * (i + .5f)
            cv.drawText(dayName(d), cx, 442 * sy, txt(x.textTf, 24 * s, x.sub, Paint.Align.CENTER))
            ic(cv, x, d.code, true, cx, 474 * sy, 40 * s)
            cv.drawText("${d.max}° ${d.min}°", cx, 512 * sy, txt(x.textTf, 24 * s, x.ink, Paint.Align.CENTER))
        }
    }
}
