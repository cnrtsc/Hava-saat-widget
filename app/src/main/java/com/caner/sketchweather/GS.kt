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
    /* ================= Zarif seri (v26): geniş boşluk, büyük ikon, ince sarı-kırmızı çizgi ================= */
    const val E_SQ_D = 30; const val E_SQ_L = 31; const val E_ROUND = 32; const val E_MINI_D = 33; const val E_MINI_L = 34
    const val E_TOWER_D = 35; const val E_TOWER_L = 36; const val E_EDIT_D = 37; const val E_EDIT_L = 38; const val E_TWO_D = 39; const val E_TWO_L = 40
    private val E_LIGHT = setOf(E_SQ_L, E_MINI_L, E_TOWER_L, E_EDIT_L, E_TWO_L)
    private val GOLD = Color.rgb(214, 170, 74); private val CRIM = Color.rgb(150, 18, 46)
    private val IVORY = Color.rgb(247, 242, 232); private val NIGHT = Color.rgb(16, 16, 18)
    val LINE_NAMES = arrayOf("Tasarımdaki", "Simetrik", "Asimetrik", "Ters asimetrik", "Yok")
    fun lineStyle(c: Context) = Cfg.int(c, "gsLine", 0).coerceIn(0, LINE_NAMES.size - 1)

    /** Sarı-kırmızı çizgi; def = tasarımın varsayılan bölünmesi (0.5 simetrik). */
    private fun rule(cv: Canvas, x: X, l: Float, r: Float, y: Float, h: Float, def: Float) {
        val split = when (lineStyle(x.c)) { 1 -> .5f; 2 -> .22f; 3 -> .78f; 4 -> return; else -> def }
        val m = l + (r - l) * split
        cv.drawRect(l, y, m, y + h, Paint().apply { color = GOLD; alpha = 255 })
        cv.drawRect(m, y, r, y + h, Paint().apply { color = CRIM })
    }
    private fun hairline(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, col: Int, w: Float) = cv.drawLine(x0, y0, x1, y1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeWidth = w })
    private fun capsP(x: X, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) = txt(x.textTf, size, col, al).apply { letterSpacing = .2f }
    /** Saat (ince yazı, kutuya sığar). */
    private fun eClock(cv: Canvas, x: X, t: String, xx: Float, base: Float, size: Float, maxW: Float, al: Paint.Align, col: Int) {
        val p = Lock.clockPaintTf(x.c, x.clockTf, size, col, Combo.Look(0, col, col, col, false, 0)).apply { textAlign = al }
        val m = Ink.measure(x.c, t, p); if (m > maxW && m > 0) p.textSize *= maxW / m
        if (lowOp) p.setShadowLayer(p.textSize * .06f, 0f, p.textSize * .03f, 0x99000000.toInt())
        Ink.time(x.c, cv, t, xx, base, p)
    }
    /** İkon kutusu: büyütme ayarı kutu içinde kalır, diğer öğelere taşmaz. */
    private fun eIcon(cv: Canvas, x: X, cx: Float, cy: Float, box: Float) {
        val w = x.w ?: return
        val drawn = min(box * x.isc, box * 1.25f)
        Combo.icon(x.c, cv, Style.iconPref(x.c, 0, 6, w.code, w.isDay), cx, cy, drawn / x.isc)
    }

    fun render3(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(60, 560) * k).toInt(); val Hi = (hdp.coerceIn(40, 560) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val light = kind in E_LIGHT
        val bgCol = if (light) IVORY else NIGHT
        Ink.setBg(bgCol)
        val ink = Ink.get(c, Ink.TEXT) ?: if (light) Color.rgb(30, 24, 28) else Color.rgb(245, 245, 245)
        val sub = Ink.get(c, Ink.SUB) ?: if (light) Color.rgb(130, 120, 112) else Color.rgb(150, 150, 158)
        val thin = Typeface.create(c.resources.getFont(R.font.f_outfit_c), Typeface.NORMAL)
        val bold = Typeface.create(c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL)
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val clockCol = Ink.get(c, Ink.CLOCK) ?: ink
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), !light, ink, sub, clockCol, Ink.get(c, Ink.TEMP) ?: ink, Ink.get(c, Ink.LOC) ?: sub,
            if (hasFont) Style.clockTypeface(c, Style.font(c)) else thin, boldFor(c, bold), Style.textTypeface(c, if (hasFont) Style.font(c) else 1))
        begin(c)
        val hl = if (light) Color.rgb(222, 214, 204) else Color.rgb(56, 56, 62)
        val accent = if (light) CRIM else GOLD
        val W = x.W; val H = x.H; val s = min(W, H)
        val city = WeatherRepo.city(c)
        val showLoc = Combo.show(c, "loc")
        val tStr = if (w != null) "${w.temp}°" else "--°"
        val desc = descShort(x)
        val time = now("HH:mm")
        fun bg(r: RectF, rad: Float) = pill(cv, x, r, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgCol })
        when (kind) {
            E_SQ_D, E_SQ_L -> {
                val m = s * .043f; bg(RectF(m, m, W - m, H - m), s * .115f)
                val p = s * .13f
                logoAt(cv, x, p + s * .07f, p + s * .07f, s * .068f)
                eIcon(cv, x, W - p - s * .1f, p + s * .08f, s * .2f)
                eClock(cv, x, time, W / 2, H * .59f, s * .27f, W - p * 2, Paint.Align.CENTER, clockCol)
                rule(cv, x, p, W - p, H * .665f, s * .008f, .5f)
                val tp = fitP(txt(x.clockTf, s * .1f * Ink.tempScale(c), x.temp).apply { typeface = bold }, tStr, W * .3f)
                cv.drawText(tStr, p, H * .83f, tp)
                val dx = p + tp.measureText(tStr) + s * .04f
                cv.drawText(desc, dx, H * .83f, fitP(txt(x.textTf, s * .046f, sub), desc, W * .5f - dx))
                if (showLoc) cv.drawText(city.uppercase(tr), W - p, H * .81f, fitP(capsP(x, s * .033f, x.loc, Paint.Align.RIGHT), city.uppercase(tr), W * .4f))
                cv.drawText(dt(x, "EEE d MMM").uppercase(tr), W - p, H * .86f, fitP(capsP(x, s * .033f, sub, Paint.Align.RIGHT), dt(x, "EEE d MMM").uppercase(tr), W * .4f))
            }
            E_ROUND -> {
                val cx = W / 2; val cy = H / 2; val r = s * .446f
                val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((110 * x.op).toInt(), 0, 0, 0); maskFilter = BlurMaskFilter(s * .03f, BlurMaskFilter.Blur.NORMAL) }
                if (x.op > .05f) cv.drawCircle(cx, cy + s * .02f, r, sh)
                cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgCol; alpha = (x.op * 255).toInt() })
                val rr = r - s * .046f; val arc = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
                val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .011f; strokeCap = Paint.Cap.ROUND }
                if (lineStyle(c) != 4) {
                    val split = when (lineStyle(c)) { 2 -> 100f; 3 -> 220f; else -> 160f }
                    ap.color = GOLD; cv.drawArc(arc, 100f, split, false, ap)
                    ap.color = CRIM; cv.drawArc(arc, 100f + split + 20f, 340f - split - 20f + 0f, false, ap)
                }
                logoAt(cv, x, cx, cy - s * .236f, s * .064f)
                eClock(cv, x, time, cx, cy + s * .075f, s * .21f, rr * 1.5f, Paint.Align.CENTER, clockCol)
                val tp = txt(x.clockTf, s * .082f * Ink.tempScale(c), x.temp).apply { typeface = bold }
                val ib = s * .135f; val rowW = ib + s * .02f + tp.measureText(tStr)
                eIcon(cv, x, cx - rowW / 2 + ib / 2, cy + s * .21f, ib)
                cv.drawText(tStr, cx - rowW / 2 + ib + s * .02f, cy + s * .21f + tp.textSize * .36f, tp)
                if (showLoc) cv.drawText(city.uppercase(tr), cx, cy + s * .34f, fitP(capsP(x, s * .032f, x.loc, Paint.Align.CENTER), city.uppercase(tr), rr * 1.1f))
            }
            E_MINI_D, E_MINI_L -> {
                val r = RectF(W * .043f, H * .125f, W * .957f, H * .875f); bg(r, r.height() / 2)
                val lx = r.left + r.height() * .5f
                logoAt(cv, x, lx, H / 2, r.height() * .3f)
                val sep = lx + r.height() * .5f
                hairline(cv, sep, H * .35f, sep, H * .65f, hl, s * .008f)
                val ib = r.height() * .62f; val icx = r.right - r.height() * .48f
                eIcon(cv, x, icx, H / 2, ib)
                eClock(cv, x, time, sep + W * .05f, H / 2 + r.height() * .2f, r.height() * .62f, icx - ib * .6f - sep - W * .08f, Paint.Align.LEFT, clockCol)
            }
            E_TOWER_D, E_TOWER_L -> {
                val m = s * .043f; bg(RectF(m, m, W - m, H - m), s * .115f)
                logoAt(cv, x, W / 2, H * .167f, s * .1f)
                if (showLoc) cv.drawText(city.uppercase(tr), W / 2, H * .3f, fitP(capsP(x, s * .04f, x.loc, Paint.Align.CENTER), city.uppercase(tr), W * .78f))
                val hh = now("HH"); val mm = now("mm")
                eClock(cv, x, hh, W / 2, H * .524f, s * .34f, W * .78f, Paint.Align.CENTER, clockCol)
                eClock(cv, x, mm, W / 2, H * .726f, s * .34f, W * .78f, Paint.Align.CENTER, Ink.get(c, Ink.MIN) ?: accent)
                rule(cv, x, W * .21f, W * .79f, H * .774f, s * .008f, .25f)
                val ib = s * .2f
                eIcon(cv, x, W * .34f, H * .876f, ib)
                val tp = fitP(txt(x.clockTf, s * .1f * Ink.tempScale(c), x.temp).apply { typeface = bold }, tStr, W * .3f)
                cv.drawText(tStr, W * .34f + ib * .62f, H * .876f + tp.textSize * .36f, tp)
            }
            E_EDIT_D, E_EDIT_L -> {
                val sx = W / 1280f; val sy = H / 560f; val u = min(sx, sy)
                bg(RectF(24 * sx, 24 * sy, W - 24 * sx, H - 24 * sy), 56 * u)
                if (showLoc) cv.drawText(city.uppercase(tr), 80 * sx, 112 * sy, fitP(capsP(x, 24 * u, x.loc), city.uppercase(tr), W * .6f))
                cv.drawText(dt(x, "EEEE · d MMMM").uppercase(tr), 80 * sx, 146 * sy, fitP(capsP(x, 20 * u, sub), dt(x, "EEEE · d MMMM").uppercase(tr), W * .6f))
                logoAt(cv, x, W - 130 * sx, 118 * sy, 48 * u)
                eClock(cv, x, time, 72 * sx, 400 * sy, 220 * u, 660 * sx, Paint.Align.LEFT, clockCol)
                val ib = 190 * u
                eIcon(cv, x, 860 * sx, 316 * sy, ib)
                val tx = 860 * sx + ib * .56f
                val tp = fitP(txt(x.clockTf, 96 * u * Ink.tempScale(c), x.temp).apply { typeface = bold }, tStr, W - 80 * sx - tx)
                cv.drawText(tStr, tx, 350 * sy, tp)
                cv.drawText(desc, tx + 4 * sx, 392 * sy, fitP(txt(x.textTf, 26 * u, sub), desc, W - 80 * sx - tx))
                hairline(cv, 80 * sx, 445 * sy, W - 80 * sx, 445 * sy, hl, 2 * u)
                rule(cv, x, 80 * sx, W - 80 * sx, 443 * sy, 4 * u, .18f)
                if (Combo.show(c, "fc")) {
                    val days = w?.days?.drop(1)?.take(3) ?: emptyList()
                    for ((i, d) in days.withIndex()) {
                        val xx = 80 * sx + i * 260 * sx
                        cv.drawText(dayName(d).uppercase(tr), xx, 492 * sy, capsP(x, 18 * u, sub))
                        Combo.icon(c, cv, Style.iconPref(c, 0, 6, d.code, true), xx + 140 * sx, 500 * sy, 40 * u)
                        cv.drawText("${d.max}° / ${d.min}°", xx, 526 * sy, txt(x.textTf, 26 * u, ink))
                    }
                }
            }
            else -> { // E_TWO
                val sx = W / 1280f; val sy = H / 240f; val u = min(sx, sy)
                val r = RectF(24 * sx, 30 * sy, W - 24 * sx, H - 30 * sy); bg(r, r.height() / 2)
                eClock(cv, x, time, 96 * sx, 160 * sy, 104 * u, 300 * sx, Paint.Align.LEFT, clockCol)
                hairline(cv, 420 * sx, 78 * sy, 420 * sx, 162 * sy, hl, 2 * u)
                if (showLoc) cv.drawText(city.uppercase(tr), 464 * sx, 112 * sy, fitP(capsP(x, 22 * u, x.loc), city.uppercase(tr), 300 * sx))
                cv.drawText(dt(x, "EEE d MMMM"), 464 * sx, 160 * sy, fitP(txt(x.textTf, 26 * u, ink), dt(x, "EEE d MMMM"), 300 * sx))
                val ib = 104 * u
                eIcon(cv, x, 860 * sx, H / 2, ib)
                cv.drawText(tStr, 860 * sx + ib * .58f, H / 2 + 52 * u * .36f, fitP(txt(x.clockTf, 52 * u * Ink.tempScale(c), x.temp).apply { typeface = bold }, tStr, 150 * sx))
                if (lineStyle(c) != 4) {
                    cv.drawRect(W - 220 * sx, 86 * sy, W - 215 * sx, 158 * sy, Paint().apply { color = GOLD })
                    cv.drawRect(W - 213 * sx, 86 * sy, W - 208 * sx, 158 * sy, Paint().apply { color = CRIM })
                }
                logoAt(cv, x, W - 120 * sx, H / 2, 46 * u)
            }
        }
        return b
    }
    /* ================= Konsept serisi (v27): ikon kahraman, çapraz, gün halkası, takvim, kemer, yığın, bilet ================= */
    const val F_HERO = 50; const val F_DIAG = 51; const val F_RING = 52; const val F_CAL = 53; const val F_ARCH = 54; const val F_STACK = 55; const val F_TICKET = 56
    private val F_LIGHT = setOf(F_DIAG, F_CAL, F_TICKET)

    fun render4(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(60, 560) * k).toInt(); val Hi = (hdp.coerceIn(40, 560) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val light = kind in F_LIGHT
        Ink.setBg(if (light) IVORY else NIGHT)
        val ink = Ink.get(c, Ink.TEXT) ?: if (light) Color.rgb(30, 24, 28) else Color.rgb(245, 245, 245)
        val sub = Ink.get(c, Ink.SUB) ?: if (light) Color.rgb(140, 130, 122) else Color.rgb(150, 150, 158)
        val thin = Typeface.create(c.resources.getFont(R.font.f_outfit_c), Typeface.NORMAL)
        val bold = Typeface.create(c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL)
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val clockCol = Ink.get(c, Ink.CLOCK) ?: ink
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), !light, ink, sub, clockCol, Ink.get(c, Ink.TEMP) ?: ink, Ink.get(c, Ink.LOC) ?: sub,
            if (hasFont) Style.clockTypeface(c, Style.font(c)) else thin, boldFor(c, bold), Style.textTypeface(c, if (hasFont) Style.font(c) else 1))
        begin(c)
        val W = x.W; val H = x.H; val s = min(W, H)
        val city = WeatherRepo.city(c); val showLoc = Combo.show(c, "loc")
        val tStr = if (w != null) "${w.temp}°" else "--°"
        val desc = descShort(x); val time = now("HH:mm")
        val crimG = { l: Float, t: Float, r: Float, btm: Float -> Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(l, t, l, btm, Color.rgb(170, 22, 52), Color.rgb(120, 10, 34), Shader.TileMode.CLAMP); alpha = (x.op * 255).toInt() } }
        fun solid(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = (x.op * 255).toInt() }
        fun tempP(size: Float, col: Int = x.temp) = txt(x.clockTf, size * Ink.tempScale(c), col).apply { typeface = bold }
        when (kind) {
            F_HERO -> {
                val sx = W / 1280f; val sy = H / 560f; val u = min(sx, sy)
                pill(cv, x, RectF(24 * sx, 24 * sy, W - 24 * sx, H - 24 * sy), 56 * u, solid(NIGHT))
                val cr = min(210 * u, (H - 140 * sy) / 2); val ccx = 70 * sx + cr; val ccy = H / 2
                cv.drawCircle(ccx, ccy, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, ccy - cr, 0f, ccy + cr, Color.rgb(40, 40, 46), Color.rgb(26, 26, 30), Shader.TileMode.CLAMP); alpha = (x.op * 255).toInt() })
                eIcon(cv, x, ccx, ccy, cr * 1.55f)
                val bx = ccx + cr + 76 * sx
                if (lineStyle(c) != 4) { cv.drawRect(bx, H * .21f, bx + 6 * u, H / 2, Paint().apply { color = GOLD }); cv.drawRect(bx, H / 2, bx + 6 * u, H * .79f, Paint().apply { color = CRIM }) }
                val tx = bx + 48 * sx; val maxW = W - 150 * sx - tx
                if (showLoc) cv.drawText(city.uppercase(tr), tx, 150 * sy, fitP(capsP(x, 24 * u, x.loc), city.uppercase(tr), maxW))
                eClock(cv, x, time, tx - 10 * sx, 330 * sy, 170 * u, W - 60 * sx - tx, Paint.Align.LEFT, clockCol)
                val tp = fitP(tempP(64 * u), tStr, 160 * sx); cv.drawText(tStr, tx, 420 * sy, tp)
                val line2 = listOf(desc, dt(x, "EEE d MMMM")).filter { it.isNotEmpty() }.joinToString(" · ")
                val l2x = tx + tp.measureText(tStr) + 30 * sx
                cv.drawText(line2, l2x, 420 * sy, fitP(txt(x.textTf, 28 * u, sub), line2, W - 60 * sx - l2x))
                logoAt(cv, x, W - 120 * sx, 118 * sy, 44 * u)
            }
            F_DIAG -> {
                val m = s * .043f; val r = RectF(m, m, W - m, H - m); val rad = s * .115f
                pill(cv, x, r, rad, solid(IVORY))
                cv.save(); cv.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })
                cv.drawPath(Path().apply { moveTo(0f, 0f); lineTo(W, 0f); lineTo(0f, H * .62f); close() }, crimG(0f, 0f, W, H * .62f))
                if (lineStyle(c) != 4) cv.drawLine(W, 0f, 0f, H * .62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; strokeWidth = s * .014f })
                cv.restore()
                logoAt(cv, x, s * .214f, s * .214f, s * .082f)
                eIcon(cv, x, W - s * .25f, H * .55f, s * .27f)
                eClock(cv, x, time, W - s * .107f, H * .807f, s * .186f, W * .62f, Paint.Align.RIGHT, Ink.get(c, Ink.CLOCK) ?: Color.rgb(30, 24, 28))
                val bl = listOfNotNull(tStr, if (showLoc) city.uppercase(tr) else null).joinToString(" · ")
                cv.drawText(bl, s * .143f, H * .893f, fitP(capsP(x, s * .039f, Ink.get(c, Ink.SUB) ?: Color.rgb(140, 130, 122)), bl, W * .7f))
            }
            F_RING -> {
                val m = s * .043f; pill(cv, x, RectF(m, m, W - m, H - m), s * .115f, solid(NIGHT))
                val cx = W / 2; val cy = H / 2; val r = s * .357f
                val ring = RectF(cx - r, cy - r, cx + r, cy + r)
                val st = s * .018f
                cv.drawArc(ring, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = st; color = Color.rgb(48, 48, 54) })
                val rise = Mod.hm(w?.sunrise) ?: 420; val set = Mod.hm(w?.sunset) ?: 1140
                val cal = java.util.Calendar.getInstance(); val nowM = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                val day = nowM in rise..set
                val f = if (day) (nowM - rise).toFloat() / (set - rise) else ((nowM - set + 1440) % 1440).toFloat() / (1440 - (set - rise))
                val sweep = 360f * f.coerceIn(0.01f, 1f)
                cv.drawArc(ring, -90f, sweep, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = st; strokeCap = Paint.Cap.ROUND
                    shader = android.graphics.SweepGradient(cx, cy, intArrayOf(GOLD, CRIM, GOLD), floatArrayOf(0f, .75f, 1f)).also { g -> val mtx = android.graphics.Matrix(); mtx.setRotate(-90f, cx, cy); g.setLocalMatrix(mtx) }
                })
                val a = Math.toRadians((-90 + sweep).toDouble())
                cv.drawCircle(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), st * 1.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                eIcon(cv, x, cx, cy - s * .154f, s * .23f)
                eClock(cv, x, time, cx, cy + s * .082f, s * .164f, r * 1.4f, Paint.Align.CENTER, clockCol)
                val left = if (day) set - nowM else ((rise - nowM) + 1440) % 1440
                val lbl = "$tStr · " + (if (day) "GÜN BATIMINA " else "GÜN DOĞUMUNA ") + (if (left >= 60) "${left / 60} SA" else "$left DK")
                cv.drawText(lbl, cx, cy + s * .18f, fitP(capsP(x, s * .032f, sub, Paint.Align.CENTER), lbl, r * 1.5f))
            }
            F_CAL -> {
                val m = s * .043f
                for ((i, sh) in listOf(.12f, .06f).withIndex()) {
                    val off = (2 - i) * s * .0125f
                    cv.drawRoundRect(RectF(m + off, s * .071f + off, W - m - off, H - m + off), s * .07f, s * .07f, solid(shade(IVORY, -sh)))
                }
                val r = RectF(m, s * .071f, W - m, H - m)
                pill(cv, x, r, s * .07f, solid(IVORY))
                val hb = s * .304f
                cv.save(); cv.clipPath(Path().apply { addRoundRect(r, s * .07f, s * .07f, Path.Direction.CW) })
                cv.drawRect(r.left, r.top, r.right, hb, crimG(0f, r.top, W, hb))
                if (lineStyle(c) != 4) cv.drawRect(r.left, hb, r.right, hb + s * .011f, Paint().apply { color = GOLD })
                cv.restore()
                val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 192, 200) }
                for (i in 0 until 5) {
                    val rx = W * .196f + i * (W * .608f / 4)
                    cv.drawCircle(rx, s * .12f, s * .016f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 10, 20) })
                    cv.drawRoundRect(RectF(rx - s * .007f, s * .046f, rx + s * .007f, s * .128f), s * .007f, s * .007f, ringP)
                }
                val pad = s * .125f
                cv.drawText(now("EEEE").uppercase(tr), pad, s * .25f, fitP(capsP(x, s * .046f, Color.rgb(255, 230, 232)), now("EEEE").uppercase(tr), W * .45f))
                cv.drawText(now("d MMMM").uppercase(tr), W - pad, s * .25f, fitP(txt(bold, s * .046f, Color.WHITE, Paint.Align.RIGHT).apply { letterSpacing = .1f }, now("d MMMM").uppercase(tr), W * .4f))
                eClock(cv, x, time, pad, H * .607f, s * .214f, W - pad * 2, Paint.Align.LEFT, Ink.get(c, Ink.CLOCK) ?: Color.rgb(30, 24, 28))
                val ib = s * .2f
                eIcon(cv, x, pad + ib / 2, H * .8f, ib)
                cv.drawText(tStr, pad + ib * 1.1f, H * .8f + s * .035f, fitP(tempP(s * .096f, Ink.get(c, Ink.TEMP) ?: Color.rgb(30, 24, 28)), tStr, W * .3f))
                logoAt(cv, x, W - s * .196f, H * .8f, s * .082f)
            }
            F_ARCH -> {
                val m = s * .043f; pill(cv, x, RectF(m, m, W - m, H - m), s * .107f, solid(NIGHT))
                val l = s * .143f; val r = W - l; val top = s * .143f; val base = H * .56f
                val arch = Path().apply { moveTo(l, base); lineTo(l, top + (r - l) / 2); arcTo(RectF(l, top, r, top + (r - l)), 180f, 180f); lineTo(r, base); close() }
                val (cc, _) = WeatherRepo.conditionColor(w)
                cv.drawPath(arch, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, top, 0f, base, shade(cc, -.35f), shade(cc, .15f), Shader.TileMode.CLAMP) })
                if (lineStyle(c) != 4) { cv.drawRect(l, base, (l + r) / 2, base + s * .011f, Paint().apply { color = GOLD }); cv.drawRect((l + r) / 2, base, r, base + s * .011f, Paint().apply { color = CRIM }) }
                eIcon(cv, x, W / 2, (top + base) / 2 + s * .03f, min(r - l, base - top) * .78f)
                eClock(cv, x, time, W / 2, H * .762f, s * .232f, W * .8f, Paint.Align.CENTER, clockCol)
                val l2 = listOf(tStr, desc.uppercase(tr)).filter { it.isNotEmpty() }.joinToString(" · ")
                cv.drawText(l2, W / 2, H * .845f, fitP(capsP(x, s * .043f, sub, Paint.Align.CENTER), l2, W * .8f))
                logoAt(cv, x, W / 2, H * .917f, s * .06f)
            }
            F_STACK -> {
                val sx = W / 1280f; val sy = H / 560f; val u = min(sx, sy)
                cv.save(); cv.rotate(-6f, 980 * sx, 280 * sy)
                val br = RectF(720 * sx, 50 * sy, 1230 * sx, 500 * sy)
                pill(cv, x, br, 48 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, br.top, 0f, br.bottom, GOLD, Color.rgb(230, 190, 90), Shader.TileMode.CLAMP); alpha = (x.op * 255).toInt() })
                val ib = min(230 * u, br.height() * .52f)
                eIcon(cv, x, 1000 * sx, 250 * sy, ib)
                cv.drawText(tStr, 1000 * sx, 440 * sy, fitP(tempP(70 * u, Ink.get(c, Ink.TEMP) ?: Color.rgb(110, 8, 28)).apply { textAlign = Paint.Align.CENTER }, tStr, 220 * sx))
                cv.restore()
                val fr = RectF(40 * sx, 40 * sy, 800 * sx, 520 * sy)
                pill(cv, x, fr, 48 * u, solid(NIGHT))
                if (showLoc) cv.drawText(city.uppercase(tr), 100 * sx, 130 * sy, fitP(capsP(x, 24 * u, x.loc), city.uppercase(tr), 540 * sx))
                cv.drawText(dt(x, "EEEE · d MMMM").uppercase(tr), 100 * sx, 164 * sy, fitP(capsP(x, 20 * u, sub), dt(x, "EEEE · d MMMM").uppercase(tr), 540 * sx))
                eClock(cv, x, time, 90 * sx, 380 * sy, 190 * u, 640 * sx, Paint.Align.LEFT, clockCol)
                rule(cv, x, 100 * sx, 740 * sx, 430 * sy, 4 * u, .3f)
                val d0 = w?.days?.firstOrNull()
                val l3 = listOfNotNull(desc.takeIf { it.isNotEmpty() }, d0?.let { "Y ${it.max}° D ${it.min}°" }).joinToString(" · ")
                cv.drawText(l3, 100 * sx, 480 * sy, fitP(txt(x.textTf, 26 * u, sub), l3, 640 * sx))
                logoAt(cv, x, 720 * sx, 120 * sy, 40 * u)
            }
            else -> { // F_TICKET
                val sx = W / 1280f; val sy = H / 240f; val u = min(sx, sy)
                val r = RectF(24 * sx, 34 * sy, W - 24 * sx, H - 34 * sy); val rad = 28 * u
                val body = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
                val cutX = 300 * sx; val nr = 16 * u
                body.op(Path().apply { addCircle(cutX, r.top, nr, Path.Direction.CW); addCircle(cutX, r.bottom, nr, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((80 * x.op).toInt(), 0, 0, 0); maskFilter = BlurMaskFilter(14 * u, BlurMaskFilter.Blur.NORMAL) }
                if (x.op > .05f) { cv.save(); cv.translate(0f, 8 * u); cv.drawPath(body, sh); cv.restore() }
                cv.drawPath(body, solid(IVORY))
                cv.save(); cv.clipPath(body); cv.drawRect(0f, 0f, cutX, H, crimG(0f, r.top, cutX, r.bottom)); cv.restore()
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; strokeWidth = 3 * u; pathEffect = android.graphics.DashPathEffect(floatArrayOf(7 * u, 7 * u), 0f) }
                cv.drawLine(cutX, r.top + nr * 1.4f, cutX, r.bottom - nr * 1.4f, dp)
                logoAt(cv, x, 162 * sx, H / 2, 54 * u)
                val head = listOfNotNull(if (showLoc) city.uppercase(tr) else null, dt(x, "EEE d MMMM").uppercase(tr).takeIf { it.isNotEmpty() }).joinToString("  ·  ")
                cv.drawText(head, 350 * sx, 98 * sy, fitP(capsP(x, 20 * u, Ink.get(c, Ink.SUB) ?: Color.rgb(140, 130, 122)), head, 470 * sx))
                eClock(cv, x, time, 344 * sx, 176 * sy, 92 * u, 450 * sx, Paint.Align.LEFT, Ink.get(c, Ink.CLOCK) ?: Color.rgb(30, 24, 28))
                val ib = 120 * u
                eIcon(cv, x, 900 * sx, H / 2, ib)
                cv.drawText(tStr, 900 * sx + ib * .6f, H / 2 + 60 * u * .36f, fitP(tempP(60 * u, Ink.get(c, Ink.TEMP) ?: Color.rgb(30, 24, 28)), tStr, W - 170 * sx - (900 * sx + ib * .6f)))
                if (lineStyle(c) != 4) { cv.drawRect(W - 140 * sx, 70 * sy, W - 134 * sx, H - 70 * sy, Paint().apply { color = GOLD }); cv.drawRect(W - 130 * sx, 70 * sy, W - 124 * sx, H - 70 * sy, Paint().apply { color = CRIM }) }
            }
        }
        return b
    }
    private fun shade(col: Int, f: Float): Int {
        val t = if (f < 0) Color.BLACK else Color.WHITE; val a = kotlin.math.abs(f)
        return Color.rgb((Color.red(col) + (Color.red(t) - Color.red(col)) * a).toInt(), (Color.green(col) + (Color.green(t) - Color.green(col)) * a).toInt(), (Color.blue(col) + (Color.blue(t) - Color.blue(col)) * a).toInt())
    }
    /* ================= Pro seri (v28): gerçek widget düzeni ================= */
    const val P_SMALL_D = 60; const val P_SMALL_L = 61; const val P_MED_D = 62; const val P_MED_L = 63; const val P_MED3_D = 64; const val P_MED3_L = 65
    const val P_LARGE_D = 66; const val P_LARGE_L = 67; const val P_STRIP_D = 68; const val P_STRIP_L = 69; const val P_GLANCE = 70
    private val P_LIGHT = setOf(P_SMALL_L, P_MED_L, P_MED3_L, P_LARGE_L, P_STRIP_L)
    private val P_RED = Color.rgb(169, 4, 50); private val P_YEL = Color.rgb(253, 185, 18)

    private class PX(val x: X, val sx: Float, val sy: Float, val u: Float, val ink: Int, val sub: Int, val hair: Int, val track: Int, val chip: Int, val bg: Int)

    fun render5(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(60, 560) * k).toInt(); val Hi = (hdp.coerceIn(40, 560) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val light = kind in P_LIGHT
        val glance = kind == P_GLANCE
        val bgCol = if (light) Color.WHITE else Color.rgb(20, 20, 22)
        Ink.setBg(if (glance) null else bgCol)
        val ink = Ink.get(c, Ink.TEXT) ?: if (light) Color.rgb(24, 22, 26) else Color.rgb(245, 245, 247)
        val sub = Ink.get(c, Ink.SUB) ?: if (glance) Color.rgb(235, 235, 240) else if (light) Color.rgb(120, 116, 122) else Color.rgb(152, 152, 160)
        val thin = Typeface.create(c.resources.getFont(R.font.f_outfit_c), Typeface.NORMAL)
        val bold = Typeface.create(c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL)
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val clockCol = Ink.get(c, Ink.CLOCK) ?: if (glance) Color.WHITE else ink
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), !light, ink, sub, clockCol, Ink.get(c, Ink.TEMP) ?: ink, Ink.get(c, Ink.LOC) ?: ink,
            if (hasFont) Style.clockTypeface(c, Style.font(c)) else if (glance) thin else bold, boldFor(c, bold), Style.textTypeface(c, if (hasFont) Style.font(c) else 1))
        begin(c)
        if (glance) lowOp = true
        val (rw, rh) = when (kind) {
            P_SMALL_D, P_SMALL_L -> 170f to 170f; P_MED_D, P_MED_L -> 350f to 170f; P_MED3_D, P_MED3_L -> 350f to 250f
            P_LARGE_D, P_LARGE_L -> 350f to 350f; P_STRIP_D, P_STRIP_L -> 350f to 80f; else -> 350f to 150f
        }
        val sx = x.W / (rw * k); val sy = x.H / (rh * k); val u = min(sx, sy) * k
        val px = PX(x, sx * k, sy * k, u, ink, sub, if (light) Color.rgb(230, 228, 232) else Color.rgb(52, 52, 58),
            if (light) Color.rgb(236, 234, 238) else Color.rgb(48, 48, 54), if (light) Color.rgb(246, 244, 247) else Color.rgb(32, 32, 36), bgCol)
        if (!glance) {
            val rad = 22 * u
            pill(cv, x, RectF(0f, 0f, x.W, x.H - 3 * u).also { it.inset(2 * u, 2 * u) }, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgCol })
        }
        when (kind) {
            P_SMALL_D, P_SMALL_L -> proSmall(cv, px)
            P_MED_D, P_MED_L -> proMed(cv, px, false)
            P_MED3_D, P_MED3_L -> proMed(cv, px, true)
            P_LARGE_D, P_LARGE_L -> proLarge(cv, px)
            P_STRIP_D, P_STRIP_L -> proStrip(cv, px)
            else -> proGlance(cv, px)
        }
        return b
    }

    // yardımcılar (dp → px: X ekseni sx, Y ekseni sy, boyut u)
    private fun PX.hx(v: Float) = v * sx
    private fun PX.vy(v: Float) = v * sy
    private fun PX.fr(v: Float) = x.W - v * sx
    private fun PX.tx(t: String, xx: Float, yy: Float, size: Float, col: Int, b: Boolean = false, al: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
        if (t.isEmpty()) return
        val p = txt(if (b) x.boldTf else x.textTf, size * u, col, al)
        if (maxW > 0) fitP(p, t, maxW)
        cvRef!!.drawText(t, xx, yy, p)
    }
    private var cvRef: Canvas? = null
    private fun PX.accentBar(xx: Float, yy: Float, wdp: Float, h: Float = 3f) {
        if (lineStyle(x.c) == 4) return
        val split = when (lineStyle(x.c)) { 1 -> .5f; 3 -> .78f; else -> .45f }
        val cv = cvRef!!; val r = h * u / 2
        cv.drawRoundRect(RectF(xx, yy, xx + wdp * u * split, yy + h * u), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P_YEL })
        cv.drawRoundRect(RectF(xx + wdp * u * split, yy, xx + wdp * u, yy + h * u), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P_RED })
    }
    private fun PX.header(showLogo: Boolean = true) {
        val cv = cvRef!!
        if (Combo.show(x.c, "loc")) {
            pin(cv, hx(21f), vy(24f), 5.2f * u, Ink.get(x.c, Ink.LOC) ?: if (x.dark) P_YEL else P_RED, bg)
            tx(WeatherRepo.city(x.c), hx(30f), vy(24f), 14f, x.loc, true, maxW = x.W - hx(30f) - hx(48f))
        }
        if (showLogo) logoAt(cv, x, fr(27f), vy(19f), 11f * u)
    }
    private fun PX.clock(xx: Float, base: Float, size: Float, maxW: Float, al: Paint.Align = Paint.Align.LEFT) {
        val p = Lock.clockPaintTf(x.c, x.clockTf, size * u, x.clock, Combo.Look(0, x.clock, x.clock, x.clock, false, 0)).apply { textAlign = al }
        val m = Ink.measure(x.c, now("HH:mm"), p); if (m > maxW && m > 0) p.textSize *= maxW / m
        if (lowOp) p.setShadowLayer(p.textSize * .05f, 0f, p.textSize * .025f, 0x99000000.toInt())
        Ink.time(x.c, cvRef!!, now("HH:mm"), xx, base, p)
    }
    /** İkon kutusu; büyütme ayarı en fazla %25 taşar. */
    private fun PX.icon(code: Int, day: Boolean, cx: Float, cy: Float, sizeDp: Float) {
        val box = sizeDp * u; val drawn = min(box * x.isc, box * 1.25f)
        Combo.icon(x.c, cvRef!!, Style.iconPref(x.c, 0, 6, code, day), cx, cy, drawn / x.isc)
    }
    private fun PX.temp(): String = x.w?.let { "${it.temp}°" } ?: "--°"
    private fun PX.desc(): String = descShort(x)
    private fun PX.extras(): List<String> {
        val w = x.w ?: return emptyList(); val out = ArrayList<String>()
        if (Combo.show(x.c, "feels") && w.feels != null) out.add("His ${w.feels}°")
        val d0 = w.days.firstOrNull()
        if (Combo.show(x.c, "hl") && d0 != null) out.add("Y ${d0.max}° D ${d0.min}°")
        if (Combo.show(x.c, "rain") && w.rainChance != null) out.add("Yağış %${w.rainChance}")
        return out
    }
    private fun PX.dateStr(long: Boolean = true) = dt(x, if (long) "EEEE, d MMMM" else "EEE d MMM")

    private fun proSmall(cv: Canvas, p: PX) = with(p) {
        cvRef = cv; header()
        clock(hx(15f), vy(84f), 46f, x.W - hx(30f))
        accentBar(hx(16f), vy(94f), 34f)
        tx(dateStr(), hx(16f), vy(113f), 12f, sub, maxW = x.W - hx(32f))
        val w = x.w
        if (w != null) icon(w.code, w.isDay, hx(33f), vy(139f), 36f)
        val tp = fitP(txt(bold(x), 24f * u * Ink.tempScale(x.c), x.temp), temp(), hx(60f))
        cv.drawText(temp(), hx(33f) + 18f * u * min(x.isc, 1.25f) + 4 * u, vy(150f), tp)
        val ex = extras().filter { !it.startsWith("Y ") }.take(2)
        ex.forEachIndexed { i, t -> tx(t, fr(16f), vy(140f + i * 16f), 11f, sub, al = Paint.Align.RIGHT, maxW = hx(64f)) }
    }
    private fun bold(x: X) = x.boldTf.let { if (fakeBoldTf != null) it else Typeface.create(x.c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL) }

    private fun proMed(cv: Canvas, p: PX, three: Boolean) = with(p) {
        cvRef = cv; header()
        val leftW = x.W * .56f
        clock(hx(15f), vy(98f), 56f, leftW - hx(20f))
        accentBar(hx(16f), vy(110f), 36f)
        tx(dateStr(), hx(16f), vy(132f), 13f, sub, maxW = leftW - hx(20f))
        val w = x.w
        val rx = fr(16f)
        val tp = fitP(txt(bold(x), 40f * u * Ink.tempScale(x.c), x.temp, Paint.Align.RIGHT), temp(), hx(90f))
        cv.drawText(temp(), rx, vy(96f), tp)
        val tW = tp.measureText(temp())
        if (w != null) icon(w.code, w.isDay, rx - tW - 10 * u - 32f * u * min(x.isc, 1.25f), vy(82f), 64f)
        tx(desc(), rx, vy(118f), 12f, ink, al = Paint.Align.RIGHT, maxW = x.W - leftW)
        val exY = if (three) vy(136f) else vy(150f)
        tx(extras().joinToString("  ·  "), rx, exY, 11f, sub, al = Paint.Align.RIGHT, maxW = x.W - leftW + hx(30f))
        if (three && Combo.show(x.c, "fc")) {
            cv.drawRect(hx(16f), vy(158f), fr(16f), vy(158f) + max(1f, u * .5f), Paint().apply { color = hair })
            val days = w?.days?.drop(1)?.take(3) ?: emptyList()
            val cw = (x.W - hx(32f)) / 3
            for ((i, d) in days.withIndex()) {
                val cx = hx(16f) + cw * (i + .5f)
                tx(dayName(d), cx, vy(182f), 12f, sub, al = Paint.Align.CENTER)
                icon(d.code, true, cx, vy(204f), 28f)
                tx("${d.max}°  ${d.min}°", cx, vy(234f), 13f, ink, true, Paint.Align.CENTER, cw * .9f)
                if (i > 0) cv.drawRect(hx(16f) + cw * i, vy(172f), hx(16f) + cw * i + max(1f, u * .5f), vy(236f), Paint().apply { color = hair })
            }
        }
    }

    private fun proLarge(cv: Canvas, p: PX) = with(p) {
        cvRef = cv; header()
        clock(hx(15f), vy(108f), 72f, x.W - hx(30f))
        accentBar(hx(16f), vy(122f), 40f)
        tx(dateStr(), hx(16f), vy(146f), 14f, sub, maxW = x.W - hx(32f))
        val box = RectF(hx(16f), vy(166f), fr(16f), vy(236f))
        cv.drawRoundRect(box, 16 * u, 16 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chip; alpha = (x.op * 255).toInt().coerceAtLeast(40) })
        val w = x.w
        if (w != null) icon(w.code, w.isDay, hx(54f), vy(201f), 58f)
        val tp = fitP(txt(bold(x), 40f * u * Ink.tempScale(x.c), x.temp), temp(), hx(70f))
        val tX = hx(54f) + 29f * u * min(x.isc, 1.25f) + 8 * u
        cv.drawText(temp(), tX, vy(214f), tp)
        val ix = max(hx(160f), tX + tp.measureText(temp()) + 14 * u)
        tx(desc(), ix, vy(196f), 14f, ink, true, maxW = box.right - ix - hx(10f))
        tx(extras().joinToString("  ·  "), ix, vy(218f), 11f, sub, maxW = box.right - ix - hx(10f))
        if (!Combo.show(x.c, "fc")) return@with
        val days = w?.days?.drop(1)?.take(3) ?: return@with
        if (days.isEmpty()) return@with
        val lo = days.minOf { it.min }; val hi = days.maxOf { it.max }.coerceAtLeast(lo + 1)
        val bx0 = hx(150f); val bx1 = fr(50f)
        for ((i, d) in days.withIndex()) {
            val y = vy(266f + i * 30f)
            tx(dayName(d), hx(16f), y + 5 * u, 14f, ink)
            icon(d.code, true, hx(92f), y, 26f)
            tx("${d.min}°", bx0 - 10 * u, y + 5 * u, 13f, sub, al = Paint.Align.RIGHT)
            tx("${d.max}°", fr(16f), y + 5 * u, 13f, ink, true, Paint.Align.RIGHT)
            cv.drawRoundRect(RectF(bx0, y - 2 * u, bx1, y + 2 * u), 2 * u, 2 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track })
            val a = bx0 + (bx1 - bx0) * (d.min - lo) / (hi - lo).toFloat(); val bb = bx0 + (bx1 - bx0) * (d.max - lo) / (hi - lo).toFloat()
            cv.drawRoundRect(RectF(a, y - 2 * u, max(bb, a + 4 * u), y + 2 * u), 2 * u, 2 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(a, 0f, bb, 0f, P_YEL, P_RED, Shader.TileMode.CLAMP) })
        }
    }

    private fun proStrip(cv: Canvas, p: PX) = with(p) {
        cvRef = cv
        clock(hx(16f), vy(52f), 34f, hx(100f))
        accentBar(hx(17f), vy(60f), 26f, 2.5f)
        cv.drawRect(hx(122f), vy(22f), hx(122f) + max(1f, u * .5f), vy(58f), Paint().apply { color = hair })
        val w = x.w
        if (w != null) icon(w.code, w.isDay, hx(152f), vy(40f), 36f)
        val tX = hx(152f) + 18f * u * min(x.isc, 1.25f) + 4 * u
        val tp = fitP(txt(bold(x), 22f * u * Ink.tempScale(x.c), x.temp), temp(), hx(40f)); cv.drawText(temp(), tX, vy(48f), tp)
        val lx = max(hx(214f), tX + tp.measureText(temp()) + 12 * u)
        val maxW = fr(44f) - lx
        if (Combo.show(x.c, "loc")) tx(WeatherRepo.city(x.c), lx, vy(36f), 13f, x.loc, true, maxW = maxW)
        val l2 = listOfNotNull(dateStr(false).takeIf { it.isNotEmpty() }, x.w?.rainChance?.takeIf { Combo.show(x.c, "rain") }?.let { "Yağış %$it" }).joinToString(" · ")
        tx(l2, lx, vy(54f), 11f, sub, maxW = maxW)
        logoAt(cv, x, fr(27f), x.H / 2, 11f * u)
    }

    private fun proGlance(cv: Canvas, p: PX) = with(p) {
        cvRef = cv
        clock(hx(16f), vy(74f), 64f, x.W * .8f)
        accentBar(hx(18f), vy(86f), 40f)
        tx(dateStr(), hx(16f), vy(110f), 16f, Color.WHITE, true, maxW = x.W - hx(32f))
        val w = x.w
        if (w != null) icon(w.code, w.isDay, hx(28f), vy(129f), 26f)
        val line = listOfNotNull("${temp()} ${desc()}".trim(), extras().firstOrNull(), if (Combo.show(x.c, "loc")) WeatherRepo.city(x.c) else null).joinToString("  ·  ")
        tx(line, hx(28f) + 13f * u * min(x.isc, 1.25f) + 6 * u, vy(136f), 13f, sub, maxW = x.W - hx(60f))
        logoAt(cv, x, fr(29f), vy(40f), 13f * u)
    }
    /* ================= Örnek serisi (v29): Panel, Altın çizgi, Boya — koyu / açık / bordo ================= */
    const val R_PANEL_D = 80; const val R_PANEL_L = 81; const val R_PANEL_B = 82
    const val R_GOLD_D = 83; const val R_GOLD_L = 84; const val R_GOLD_B = 85
    const val R_PAINT_D = 86; const val R_PAINT_L = 87; const val R_PAINT_B = 88
    private const val SH_REAL = 22; private const val SH_GOLD = 27; private const val SH_INK = 28

    private var bmpCache = HashMap<Int, Bitmap>()
    private fun res(c: Context, id: Int): Bitmap = bmpCache.getOrPut(id) {
        BitmapFactory.decodeResource(c.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }
    /** Dokuyu kartı dolduracak şekilde (center-crop) çizer. */
    private fun cover(cv: Canvas, b: Bitmap, dst: RectF, alpha: Int) {
        val sc = max(dst.width() / b.width, dst.height() / b.height)
        val sw = dst.width() / sc; val sh = dst.height() / sc
        val sx = (b.width - sw) / 2; val sy = (b.height - sh) / 2
        cv.drawBitmap(b, android.graphics.Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()), dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
    }
    private fun iconFor(c: Context, defShape: Int, code: Int, day: Boolean) = Style.iconPref(c, defShape, 0, code, day)

    fun render6(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt(); val Hi = (hdp.coerceIn(60, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val theme = (kind - 80) % 3          // 0 koyu, 1 açık, 2 bordo
        val family = (kind - 80) / 3         // 0 panel, 1 altın, 2 boya
        val light = theme == 1
        val bgCol = when (theme) { 1 -> Color.rgb(250, 248, 244); 2 -> Color.rgb(110, 8, 30); else -> Color.rgb(16, 16, 18) }
        Ink.setBg(bgCol)
        val ink = Ink.get(c, Ink.TEXT) ?: if (light) Color.rgb(26, 22, 26) else Color.WHITE
        val sub = Ink.get(c, Ink.SUB) ?: when (theme) { 1 -> Color.rgb(118, 112, 116); 2 -> Color.rgb(255, 200, 208); else -> Color.rgb(160, 160, 168) }
        val hair = when (theme) { 1 -> Color.rgb(226, 222, 218); 2 -> Color.rgb(150, 40, 64); else -> Color.rgb(56, 56, 62) }
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = id != null && pr.contains(Cfg.wkey(id, "font"))
        val thin = Typeface.create(c.resources.getFont(R.font.f_outfit_c), Typeface.NORMAL)
        val outB = Typeface.create(c.resources.getFont(R.font.f_outfitb_c), Typeface.NORMAL)
        val cond = Typeface.create(c.resources.getFont(R.font.f_barlowsb_c), Typeface.NORMAL)
        val condB = Typeface.create(c.resources.getFont(R.font.f_barlowb_c), Typeface.NORMAL)
        val anton = Typeface.create(c.resources.getFont(R.font.f_anton_c), Typeface.NORMAL)
        val clockTf = if (hasFont) Style.clockTypeface(c, Style.font(c)) else when (family) { 0 -> thin; 1 -> cond; else -> condB }
        val textTf = if (hasFont || (id != null && pr.contains(Cfg.wkey(id, "textFont")))) Style.textTypeface(c, Style.font(c)) else if (family == 0) Style.textTypeface(c, 1) else cond
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), !light, ink, sub, Ink.get(c, Ink.CLOCK) ?: ink, Ink.get(c, Ink.TEMP) ?: ink, Ink.get(c, Ink.LOC) ?: ink,
            clockTf, boldFor(c, if (family == 0) outB else condB), textTf)
        begin(c)
        val W = x.W; val H = x.H
        val sx = W / 360f; val sy = H / 190f; val u = min(sx, sy)
        val card = RectF(0f, 0f, W, H)
        val rad = 22 * u
        val clip = Path().apply { addRoundRect(card, rad, rad, Path.Direction.CW) }
        // zemin
        if (x.op > .02f) {
            cv.save(); cv.clipPath(clip)
            if (family == 0) cv.drawColor(Color.argb((x.op * 255).toInt(), Color.red(bgCol), Color.green(bgCol), Color.blue(bgCol)))
            else {
                val tex = when (family) { 1 -> intArrayOf(R.drawable.tex_smoke_dark, R.drawable.tex_smoke_light, R.drawable.tex_smoke_bordo); else -> intArrayOf(R.drawable.tex_paint_dark, R.drawable.tex_paint_light, R.drawable.tex_paint_bordo) }[theme]
                cover(cv, res(c, tex), card, (x.op * 255).toInt())
                if (family == 2) { // okunurluk perdesi
                    val pc = if (light) Color.rgb(250, 246, 238) else Color.BLACK
                    cv.drawRect(card, Paint().apply { shader = LinearGradient(0f, 0f, W * .55f, 0f, Color.argb(((if (light) 150 else 110) * x.op).toInt(), Color.red(pc), Color.green(pc), Color.blue(pc)), Color.argb(0, Color.red(pc), Color.green(pc), Color.blue(pc)), Shader.TileMode.CLAMP) })
                }
            }
            cv.restore()
        }
        if (family == 2 && !light) lowOp = true   // boya zemininde yazılara gölge
        val city = WeatherRepo.city(c); val showLoc = Combo.show(c, "loc")
        val tStr = if (w != null) "${w.temp}°" else "--°"
        val desc = descShort(x); val time = now("HH:mm")
        fun T(t: String, xx: Float, yy: Float, tf: Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
            if (t.isEmpty()) return
            val p = txt(tf, size * u, col, al); if (maxW > 0) fitP(p, t, maxW); cv.drawText(t, xx, yy, p)
        }
        fun clockAt(xx: Float, base: Float, size: Float, maxW: Float, al: Paint.Align) {
            val p = Lock.clockPaintTf(c, x.clockTf, size * u, x.clock, Combo.Look(0, x.clock, x.clock, x.clock, false, 0)).apply { textAlign = al }
            val m = Ink.measure(c, time, p); if (m > maxW && m > 0) p.textSize *= maxW / m
            if (lowOp) p.setShadowLayer(p.textSize * .05f, 0f, p.textSize * .03f, 0x99000000.toInt())
            Ink.time(c, cv, time, xx, base, p)
        }
        /** İkon kutusu (dp); büyütme kutunun en fazla %25 dışına taşar. */
        fun iconAt(res: Int, cx: Float, cy: Float, boxDp: Float) {
            val box = boxDp * u; val drawn = min(box * x.isc, box * 1.25f)
            Combo.icon(c, cv, res, cx, cy, drawn / x.isc)
        }
        val days = w?.days?.drop(1)?.take(3) ?: emptyList()
        val showFc = Combo.show(c, "fc")
        when (family) {
            0 -> { // PANEL
                if (lineStyle(c) != 4) { cv.save(); cv.clipPath(clip); cv.drawRect(0f, 0f, W * .48f, 4 * u, Paint().apply { color = P_RED }); cv.drawRect(W * .48f, 0f, W, 4 * u, Paint().apply { color = P_YEL }); cv.restore() }
                // sol: ek bilgiler
                val rows = ArrayList<Triple<Int, String, String>>()
                if (Combo.show(c, "feels") && w?.feels != null) rows.add(Triple(0, "Hissedilen", "${w.feels}°"))
                if (w?.humidity != null) rows.add(Triple(2, "Nem", "%${w.humidity}"))
                if (Combo.show(c, "rain") && w?.rainChance != null) rows.add(Triple(3, "Yağmur", "%${w.rainChance}"))
                if (w?.wind != null) rows.add(Triple(1, "Rüzgâr", "${w.wind} km/sa"))
                val colL = 104 * sx
                rows.take(4).forEachIndexed { i, (g, lab, v) ->
                    val y = (24 + i * 33) * sy
                    panelGlyph(cv, g, 22 * sx, y + 6 * sy, u, ink)
                    T(lab, 38 * sx, y + 2 * sy, x.textTf, 9.5f, sub, maxW = colL - 40 * sx)
                    T(v, 38 * sx, y + 17 * sy, x.boldTf, 14f, ink, maxW = colL - 40 * sx)
                    if (i < min(rows.size, 4) - 1) cv.drawRect(14 * sx, y + 25 * sy, colL, y + 25 * sy + max(1f, .6f * u), Paint().apply { color = hair })
                }
                cv.drawRect(114 * sx, 18 * sy, 114 * sx + max(1f, .6f * u), 150 * sy, Paint().apply { color = hair })
                cv.drawRect(246 * sx, 18 * sy, 246 * sx + max(1f, .6f * u), 150 * sy, Paint().apply { color = hair })
                // orta
                val cx = 180 * sx; val midW = 124 * sx
                if (showLoc) T(city, cx, 30 * sy, x.boldTf, 17f, x.loc, Paint.Align.CENTER, midW)
                T(dt(x, "d MMMM EEEE"), cx, 45 * sy, x.textTf, 10.5f, sub, Paint.Align.CENTER, midW)
                if (w != null) iconAt(iconFor(c, SH_REAL, w.code, w.isDay), cx, 82 * sy, min(66f, 54f * sy / u))
                clockAt(cx, 142 * sy, 40f, midW, Paint.Align.CENTER)
                // sağ
                val rx = 342 * sx; val rW = 90 * sx
                val tp = txt(x.clockTf, 44 * u * Ink.tempScale(c), x.temp, Paint.Align.RIGHT); fitP(tp, tStr, rW); cv.drawText(tStr, rx, 56 * sy, tp)
                T(desc, rx, 74 * sy, x.textTf, 11.5f, sub, Paint.Align.RIGHT, rW)
                if (showFc && days.isNotEmpty()) {
                    cv.drawRect(256 * sx, 84 * sy, 346 * sx, 84 * sy + max(1f, .6f * u), Paint().apply { color = hair })
                    days.forEachIndexed { i, d ->
                        val y = (104 + i * 21) * sy
                        T(dayName(d), 258 * sx, y + 4 * sy, x.boldTf, 11.5f, ink, maxW = 28 * sx)
                        iconAt(iconFor(c, SH_REAL, d.code, true), 296 * sx, y, 18f)
                        T("${d.max}° / ${d.min}°", rx, y + 4 * sy, x.textTf, 11f, sub, Paint.Align.RIGHT, 40 * sx)
                    }
                }
                if (lineStyle(c) != 4) {
                    val ly = 171 * sy; val lr = 13 * u * logoSize(c) / 100f
                    cv.drawRect(16 * sx, ly, cx - lr - 6 * u, ly + 1.5f * u, Paint().apply { color = P_RED })
                    cv.drawRect(cx + lr + 6 * u, ly, 344 * sx, ly + 1.5f * u, Paint().apply { color = P_YEL })
                }
                logoAt(cv, x, cx, 171 * sy, 13 * u)
            }
            1 -> { // ALTIN ÇİZGİ
                val shp = if (light) SH_INK else SH_GOLD
                val leftW = 118 * sx
                if (showLoc) T(city.uppercase(tr), 22 * sx, 50 * sy, x.boldTf, 28f, x.loc, maxW = leftW)
                T(desc.uppercase(tr), 23 * sx, 64 * sy, x.textTf, 9.5f, sub, maxW = leftW)
                clockAt(20 * sx, 118 * sy, 52f, leftW + 10 * sx, Paint.Align.LEFT)
                T(dt(x, "d MMMM yyyy, EEEE"), 22 * sx, 138 * sy, x.textTf, 12.5f, ink, maxW = leftW + 10 * sx)
                // orta: ikon kutusu + derece (çakışmaz)
                val ib = 58f; val icx = 160 * sx
                if (w != null) iconAt(iconFor(c, shp, w.code, w.isDay), icx, 86 * sy, ib)
                val tX = icx + ib * u * min(x.isc, 1.25f) / 2 + 4 * u
                val rightCol = if (showFc && days.isNotEmpty()) 262 * sx else 344 * sx
                val tp = txt(x.clockTf, 52 * u * Ink.tempScale(c), x.temp); fitP(tp, tStr, rightCol - tX - 6 * u); cv.drawText(tStr, tX, 104 * sy, tp)
                T(desc, (icx + rightCol) / 2 - 10 * sx, 128 * sy, x.textTf, 13f, ink, Paint.Align.CENTER, rightCol - 140 * sx)
                if (showFc) days.forEachIndexed { i, d ->
                    val y = (46 + i * 28) * sy
                    T(dayName(d), 270 * sx, y + 6 * sy, x.textTf, 15f, ink, maxW = 30 * sx)
                    iconAt(iconFor(c, shp, d.code, true), 307 * sx, y, 22f)
                    T("${d.max}°", 344 * sx, y + 6 * sy, x.textTf, 15f, ink, Paint.Align.RIGHT, 28 * sx)
                }
                val gold = if (light) Color.rgb(190, 140, 50) else Color.rgb(214, 168, 72)
                val lx = W - 30 * sx; val lr = 13 * u * logoSize(c) / 100f
                if (lineStyle(c) != 4) {
                    cv.drawRect(0f, 156 * sy, lx - lr - 4 * u, 156 * sy + 1.5f * u, Paint().apply { color = gold })
                    cv.drawRect(0f, 160 * sy, lx - lr - 4 * u, 160 * sy + 1.5f * u, Paint().apply { color = P_RED })
                    cv.drawRect(lx + lr + 4 * u, 156 * sy, W, 156 * sy + 1.5f * u, Paint().apply { color = gold })
                    cv.drawRect(lx + lr + 4 * u, 160 * sy, W, 160 * sy + 1.5f * u, Paint().apply { color = P_RED })
                }
                logoAt(cv, x, lx, 158 * sy, 13 * u)
            }
            else -> { // BOYA
                val leftW = 128 * sx
                if (showLoc) T(city.uppercase(tr), 16 * sx, 46 * sy, if (fakeBoldTf != null) x.boldTf else anton, 30f, x.loc, maxW = leftW)
                clockAt(14 * sx, 100 * sy, 58f, leftW + 6 * sx, Paint.Align.LEFT)
                T(dt(x, "d MMMM yyyy, EEEE"), 18 * sx, 124 * sy, x.textTf, 13f, ink, maxW = leftW + 10 * sx)
                // altın fırça: derece + ikon sığacak kadar
                val tp = txt(x.clockTf, 34 * u * Ink.tempScale(c), Ink.get(c, Ink.TEMP) ?: Color.rgb(30, 22, 22)).apply { clearShadowLayer() }
                fitP(tp, tStr, 70 * sx)
                val tw = tp.measureText(tStr); val ib = 34 * u * min(x.isc, 1.25f)
                val bw = tw + ib + 40 * u; val bx = 148 * sx; val by = 58 * sy; val bh = 48 * sy
                if (x.op > .02f) cv.drawBitmap(res(c, R.drawable.brush_gold), null, RectF(bx, by, bx + bw + 20 * u, by + bh), Paint(Paint.FILTER_BITMAP_FLAG))
                cv.drawText(tStr, bx + 14 * u, 92 * sy, tp)
                if (w != null) iconAt(iconFor(c, SH_INK, w.code, w.isDay), bx + 14 * u + tw + 10 * u + ib / 2, 80 * sy, 34f)
                T(desc, bx + (bw + 20 * u) / 2, 126 * sy, x.textTf, 13f, ink, Paint.Align.CENTER, 130 * sx)
                if (showFc) days.forEachIndexed { i, d ->
                    val y = (44 + i * 26) * sy
                    T(dayName(d), 292 * sx, y, x.textTf, 17f, ink, maxW = 34 * sx)
                    T("${d.max}°", 346 * sx, y, x.textTf, 17f, ink, Paint.Align.RIGHT, 30 * sx)
                }
                if (lineStyle(c) != 4 && x.op > .02f) { cv.save(); cv.clipPath(clip); cv.drawBitmap(res(c, R.drawable.brush_flag), null, RectF(150 * sx, 110 * sy, 380 * sx, 218 * sy), Paint(Paint.FILTER_BITMAP_FLAG)); cv.restore() }
                logoAt(cv, x, 330 * sx, 152 * sy, 16 * u)
            }
        }
        return b
    }

    private fun panelGlyph(cv: Canvas, g: Int, x: Float, y: Float, u: Float, col: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = 1.6f * u; strokeCap = Paint.Cap.ROUND }
        when (g) {
            0 -> { cv.drawRoundRect(RectF(x - 2.5f * u, y - 9 * u, x + 2.5f * u, y + 3 * u), 2.5f * u, 2.5f * u, p); cv.drawCircle(x, y + 6 * u, 5 * u, p) }
            1 -> { cv.drawLine(x - 9 * u, y - 5 * u, x + 5 * u, y - 5 * u, p); cv.drawLine(x - 9 * u, y, x + 9 * u, y, p); cv.drawLine(x - 9 * u, y + 5 * u, x + 2 * u, y + 5 * u, p) }
            2 -> cv.drawPath(Path().apply { moveTo(x, y - 9 * u); quadTo(x + 7 * u, y, x + 6 * u, y + 3 * u); quadTo(x, y + 11 * u, x - 6 * u, y + 3 * u); quadTo(x - 7 * u, y, x, y - 9 * u); close() }, p)
            else -> { cv.drawArc(RectF(x - 9 * u, y - 9 * u, x + 9 * u, y + 9 * u), 180f, 180f, false, p); cv.drawLine(x, y, x, y + 9 * u, p) }
        }
    }
}
