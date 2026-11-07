package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Kağıt kartlar: zarf, polaroid, bilet, takvim yaprağı, akordeon, etiket, kart yığını. */
object Cards {
    const val ZARF = 0; const val POLA = 1; const val BILET = 2; const val TAKVIM = 3; const val AKOR = 4; const val ETIKET = 5; const val YIGIN = 6
    val PAPER_NAMES = arrayOf("Tasarımdaki", "Krem", "Beyaz", "Kraft", "Gece", "Buz mavisi", "Pembe", "Nane", "Renk paleti")
    private val PAPERS = intArrayOf(0, Color.rgb(250, 245, 234), Color.rgb(252, 252, 250), Color.rgb(205, 170, 124), Color.rgb(36, 40, 52),
        Color.rgb(222, 234, 246), Color.rgb(250, 224, 230), Color.rgb(220, 240, 226), 0)
    val ALIGN_NAMES = arrayOf("Sola", "Ortaya", "Sağa")
    val ICONPOS_NAMES = arrayOf("Yazının yanında", "Yazının üstünde")
    /** Kartların önerdiği saat yazı tipi (kullanıcı seçmediyse). */
    private val DEF_FONT = intArrayOf(27, 1, 25, 24, 1, 26, 1)
    private val DEF_PAPER = intArrayOf(Color.rgb(250, 245, 234), Color.rgb(252, 251, 248), Color.rgb(246, 238, 222), Color.rgb(252, 250, 246),
        Color.rgb(236, 226, 206), Color.rgb(244, 214, 120), Color.rgb(250, 245, 234))
    private val DEF_ACC = intArrayOf(Color.rgb(196, 110, 60), Color.rgb(226, 96, 60), Color.rgb(226, 96, 60), Color.rgb(200, 62, 54),
        Color.rgb(226, 96, 60), Color.rgb(150, 100, 30), Color.rgb(226, 96, 60))
    private val tr = Locale("tr", "TR")

    fun paperIdx(c: Context) = Cfg.int(c, "cardPaper", 0).coerceIn(0, PAPER_NAMES.size - 1)
    fun align(c: Context) = Cfg.int(c, "cardAlign", 0).coerceIn(0, 2)
    fun iconPos(c: Context) = Cfg.int(c, "cardIcon", 0).coerceIn(0, 1)
    fun tomorrow(c: Context) = Cfg.bool(c, "show_tom", false)

    private fun lum(col: Int) = (.299 * Color.red(col) + .587 * Color.green(col) + .114 * Color.blue(col)) / 255.0
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    private fun shade(col: Int, f: Float) = mix(col, if (f < 0) Color.BLACK else Color.WHITE, kotlin.math.abs(f))

    private class X(
        val c: Context, val w: Weather?, val W: Float, val H: Float, val paper: Int, val ink: Int, val sub: Int, val acc: Int,
        val temp: Int, val loc: Int, val clockTf: Typeface, val textTf: Typeface, val kind: Int
    ) {
        val tall get() = H / W > .62f
        val s get() = min(W, H)
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt(); val Hi = (hdp.coerceIn(60, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val pi = paperIdx(c)
        val paper = when (pi) { 0 -> DEF_PAPER[kind]; 8 -> Palette.get(c).container; else -> PAPERS[pi] }
        Ink.setBg(paper)
        val dark = lum(paper) < .5
        val ink0 = if (dark) Color.rgb(240, 242, 246) else Color.rgb(34, 32, 40)
        val sub0 = if (dark) Color.rgb(176, 184, 198) else Color.rgb(110, 104, 98)
        val ink = Ink.get(c, Ink.TEXT) ?: ink0
        val sub = Ink.get(c, Ink.SUB) ?: sub0
        val acc = Ink.get(c, Ink.ACC) ?: if (dark) shade(DEF_ACC[kind], .35f) else DEF_ACC[kind]
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        val hasFont = (id != null && pr.contains(Cfg.wkey(id, "font")))
        val fi = if (hasFont) Style.font(c) else DEF_FONT[kind]
        val x = X(c, w, Wi.toFloat(), Hi.toFloat(), paper, ink, sub, acc, Ink.get(c, Ink.TEMP) ?: ink, Ink.get(c, Ink.LOC) ?: ink,
            Style.clockTypeface(c, fi), Style.textTypeface(c, if (hasFont) fi else 1), kind)
        when (kind) {
            ZARF -> envelope(cv, x)
            POLA -> polaroid(cv, x)
            BILET -> ticket(cv, x)
            TAKVIM -> calendar(cv, x)
            AKOR -> accordion(cv, x)
            ETIKET -> tag(cv, x)
            else -> stack(cv, x)
        }
        return b
    }

    /* ---------- yardımcılar ---------- */
    private fun txt(x: X, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT, sp: Float = 0f, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = x.textTf; textSize = size * Combo.textMul; color = col; textAlign = al; letterSpacing = sp; isFakeBoldText = bold
            Ink.shadowOverride(this)
        }
    private fun clockP(x: X, size: Float, col: Int, al: Paint.Align): Paint =
        Lock.clockPaintTf(x.c, x.clockTf, size, col, Combo.Look(0, col, col, col, false, 0)).apply { textAlign = al }
    private fun fit(p: Paint, t: String, maxW: Float): Paint { val m = p.measureText(t); if (m > maxW && m > 0) p.textSize *= maxW / m; return p }
    private fun clockFit(x: X, t: String, size: Float, col: Int, al: Paint.Align, maxW: Float): Paint {
        val p = clockP(x, size, col, al); val m = Ink.measure(x.c, t, p); if (m > maxW && m > 0) p.textSize *= maxW / m; return p
    }
    private fun shadowed(col: Int, blur: Float, dy: Float, a: Int = 90) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col; setShadowLayer(blur, 0f, dy, Color.argb(a, 0, 0, 0))
    }
    private fun now(p: String) = SimpleDateFormat(p, tr).format(Date())
    private fun ic(cv: Canvas, x: X, cx: Float, cy: Float, s: Float) {
        val w = x.w ?: return
        Combo.icon(x.c, cv, Style.iconPref(x.c, 0, 6, w.code, w.isDay), cx, cy, s)
    }
    private fun tempS(x: X) = if (x.w != null) "${x.w.temp}°" else "--°"
    private fun desc(x: X): String {
        val w = x.w ?: return "Yükleniyor…"
        val d = WeatherRepo.label(w.code)
        return if (Combo.show(x.c, "feels") && w.feels != null && w.feels != w.temp) "$d · his ${w.feels}°" else d
    }
    private fun city(x: X) = WeatherRepo.city(x.c)
    private fun showLoc(x: X) = Combo.show(x.c, "loc")
    private fun showDate(x: X) = Combo.show(x.c, "date")
    private fun showDesc(x: X) = Combo.show(x.c, "desc")
    private fun tomorrowLine(x: X): String? {
        if (!tomorrow(x.c)) return null
        val d = x.w?.days?.getOrNull(1) ?: return null
        return "Yarın ${WeatherRepo.label(d.code).lowercase(tr)} ${d.max}° / ${d.min}°"
    }

    /**
     * Genel içerik bloğu: konum, saat, tarih ve hava. Hizalama ve ikon yeri ayarlarına uyar.
     * Geniş alanda iki sütun, dar/uzun alanda alt alta dizilir.
     */
    private fun info(cv: Canvas, x: X, r: RectF) {
        val al = align(x.c); val top = iconPos(x.c) == 1
        val wide = r.width() / r.height() > 1.45f
        val time = now("HH:mm")
        if (wide) {
            val colW = r.width() * .58f
            val left = al != 2
            val cl = if (left) r.left else r.right - colW          // saat sütunu
            val wl = if (left) r.left + colW else r.left            // hava sütunu
            val ww = r.width() - colW
            val ca = when { al == 1 -> Paint.Align.CENTER; left -> Paint.Align.LEFT; else -> Paint.Align.RIGHT }
            val cx = when (ca) { Paint.Align.CENTER -> cl + colW / 2; Paint.Align.LEFT -> cl; else -> cl + colW }
            var y = r.top + r.height() * .2f
            if (showLoc(x)) cv.drawText(city(x).uppercase(tr), cx, y, fit(txt(x, r.height() * .1f, x.loc, ca, .16f, true), city(x).uppercase(tr), colW))
            val cp = clockFit(x, time, r.height() * .44f, x.ink, ca, colW * .96f)
            y = r.top + r.height() * .66f
            Ink.time(x.c, cv, time, cx, y, cp)
            if (showDate(x)) cv.drawText(now("EEEE, d MMMM"), cx, r.top + r.height() * .86f, fit(txt(x, r.height() * .085f, x.sub, ca), now("EEEE, d MMMM"), colW))
            // hava sütunu
            val wx = wl + ww / 2
            if (top) {
                ic(cv, x, wx, r.top + r.height() * (.3f - .1f * (Style.iconScale(x.c) - 1f).coerceAtLeast(0f)), r.height() * .42f)
                cv.drawText(tempS(x), wx, r.top + r.height() * .76f, txt(x, r.height() * .22f * Ink.tempScale(x.c), x.temp, Paint.Align.CENTER).apply { typeface = x.clockTf })
                if (showDesc(x)) cv.drawText(desc(x), wx, r.top + r.height() * .9f, fit(txt(x, r.height() * .075f, x.sub, Paint.Align.CENTER), desc(x), ww))
            } else {
                val isz = r.height() * .34f
                val ie = isz * Style.iconScale(x.c)
                ic(cv, x, wl + ie * .55f, r.top + r.height() * .42f, isz)
                cv.drawText(tempS(x), wl + ww, r.top + r.height() * .52f, txt(x, r.height() * .26f * Ink.tempScale(x.c), x.temp, Paint.Align.RIGHT).apply { typeface = x.clockTf; val m = measureText(tempS(x)); val mx = ww - ie * 1.15f; if (m > mx && mx > 0) textSize *= mx / m })
                if (showDesc(x)) cv.drawText(desc(x), wl + ww, r.top + r.height() * .7f, fit(txt(x, r.height() * .075f, x.sub, Paint.Align.RIGHT), desc(x), ww))
            }
            tomorrowLine(x)?.let { cv.drawText(it, wx, r.bottom - r.height() * .02f, fit(txt(x, r.height() * .065f, x.sub, Paint.Align.CENTER), it, ww)) }
        } else {
            val ca = when (al) { 1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
            val cx = when (al) { 1 -> r.centerX(); 2 -> r.right; else -> r.left }
            var y = r.top + r.height() * .11f
            if (showLoc(x)) cv.drawText(city(x).uppercase(tr), cx, y, fit(txt(x, r.height() * .07f, x.loc, ca, .16f, true), city(x).uppercase(tr), r.width()))
            val isz = r.height() * .26f
            val tp = txt(x, r.height() * .16f * Ink.tempScale(x.c), x.temp, Paint.Align.LEFT).apply { typeface = x.clockTf }
            val tw = tp.measureText(tempS(x))
            if (top) {
                ic(cv, x, cx + when (al) { 0 -> isz / 2; 2 -> -isz / 2; else -> 0f }, r.top + r.height() * .3f, isz)
                tp.textAlign = ca; cv.drawText(tempS(x), cx, r.top + r.height() * .56f, tp)
            } else {
                val ie = isz * Style.iconScale(x.c)
                val rowW = ie + r.width() * .03f + tw
                val sx = when (al) { 1 -> r.centerX() - rowW / 2; 2 -> r.right - rowW; else -> r.left }
                ic(cv, x, sx + ie / 2, r.top + r.height() * .34f, isz)
                cv.drawText(tempS(x), sx + ie + r.width() * .03f, r.top + r.height() * .41f, tp)
                if (showDesc(x)) cv.drawText(desc(x), cx, r.top + r.height() * .54f, fit(txt(x, r.height() * .06f, x.sub, ca), desc(x), r.width()))
            }
            val cp = clockFit(x, time, r.height() * .3f, x.ink, ca, r.width())
            Ink.time(x.c, cv, time, cx, r.top + r.height() * .86f, cp)
            if (showDate(x)) cv.drawText(now("EEE, d MMM"), cx, r.bottom, fit(txt(x, r.height() * .065f, x.sub, ca), now("EEE, d MMM"), r.width()))
        }
    }

    private fun rot(cv: Canvas, deg: Float, cx: Float, cy: Float, block: () -> Unit) { cv.save(); cv.rotate(deg, cx, cy); block(); cv.restore() }

    /* ---------- 1. Zarf ---------- */
    private fun envelope(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        val env = if (paperIdx(x.c) == 0) Color.rgb(214, 196, 168) else shade(x.paper, -.12f)
        val card = if (paperIdx(x.c) == 0) Color.rgb(250, 245, 234) else x.paper
        val m = s * .05f
        val x0 = m; val x1 = W - m; val y0 = H * .26f; val y1 = H - m
        val back = shade(env, -.1f)
        cv.drawRoundRect(RectF(x0, y0, x1, y1), s * .04f, s * .04f, shadowed(back, s * .05f, s * .02f))
        cv.drawPath(Path().apply { moveTo(x0, y0 + 2); lineTo(x1, y0 + 2); lineTo(W / 2, m * .4f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = back })
        // kart
        val cr = RectF(x0 + W * .06f, H * .08f, x1 - W * .06f, y1 - H * .06f)
        cv.drawRoundRect(cr, s * .02f, s * .02f, shadowed(card, s * .03f, s * .01f, 70))
        val pocketTop = y0 + (y1 - y0) * .42f
        val area = RectF(cr.left + cr.width() * .05f, cr.top + s * .06f, cr.right - cr.width() * .05f, pocketTop + (y1 - y0) * .2f)
        // pul
        val st = min(area.height() * .95f, area.width() * .3f)
        val sx = area.right - st * .92f; val sy = cr.top + s * .04f
        rot(cv, -4f, sx + st / 2, sy + st / 2) {
            val pr = RectF(sx, sy, sx + st * .9f, sy + st)
            cv.drawRect(pr, shadowed(Color.WHITE, s * .015f, s * .005f, 60))
            val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card }
            val n = 7; val rr = st * .035f
            for (i in 0..n) { val t = i / n.toFloat(); cv.drawCircle(pr.left + pr.width() * t, pr.top, rr, hole); cv.drawCircle(pr.left + pr.width() * t, pr.bottom, rr, hole)
                cv.drawCircle(pr.left, pr.top + pr.height() * t, rr, hole); cv.drawCircle(pr.right, pr.top + pr.height() * t, rr, hole) }
            val inner = RectF(pr.left + st * .08f, pr.top + st * .08f, pr.right - st * .08f, pr.bottom - st * .08f)
            cv.drawRect(inner, Paint().apply { color = mix(x.acc, Color.WHITE, .78f) })
            ic(cv, x, inner.centerX(), inner.top + inner.height() * .42f, inner.width() * .78f)
            cv.drawText(tempS(x), inner.centerX(), inner.bottom - inner.height() * .07f,
                txt(x, inner.height() * .22f * Ink.tempScale(x.c), if (x.temp == x.ink) Color.rgb(34, 32, 40) else x.temp, Paint.Align.CENTER, bold = true))
        }
        val tw = sx - area.left - s * .04f
        if (showLoc(x)) cv.drawText(city(x).uppercase(tr), area.left, area.top + s * .05f, fit(txt(x, s * .065f, x.loc, sp = .2f), city(x).uppercase(tr), tw))
        val time = now("HH:mm")
        val cp = clockFit(x, time, (pocketTop - area.top) * .9f, x.ink, Paint.Align.LEFT, tw)
        Ink.time(x.c, cv, time, area.left, pocketTop - s * .02f, cp)
        // ön cep
        val vY = y0 + (y1 - y0) * .78f
        val front = Path().apply { moveTo(x0, pocketTop); lineTo(W / 2, vY); lineTo(x1, pocketTop); lineTo(x1, y1 - s * .04f)
            quadTo(x1, y1, x1 - s * .04f, y1); lineTo(x0 + s * .04f, y1); quadTo(x0, y1, x0, y1 - s * .04f); close() }
        cv.drawPath(front, shadowed(env, s * .03f, -s * .008f, 60))
        cv.drawPath(Path().apply { moveTo(x0, pocketTop); lineTo(W / 2, vY); lineTo(x1, pocketTop) },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .006f; color = shade(env, -.22f) })
        val envInk = if (lum(env) > .5) Color.rgb(84, 66, 44) else Color.rgb(240, 232, 220)
        val line = listOfNotNull(if (showDate(x)) now("EEEE, d MMMM") else null, if (showDesc(x)) desc(x) else null).joinToString("  ·  ")
        if (line.isNotEmpty()) cv.drawText(line, W / 2, y1 - s * .07f, fit(txt(x, s * .06f, Ink.get(x.c, Ink.SUB) ?: envInk, Paint.Align.CENTER), line, (x1 - x0) * .86f))
        tomorrowLine(x)?.let { cv.drawText(it, W / 2, y1 - s * .15f, fit(txt(x, s * .05f, Ink.get(x.c, Ink.SUB) ?: envInk, Paint.Align.CENTER), it, (x1 - x0) * .7f)) }
    }

    /* ---------- 2. Polaroid ---------- */
    private val SKY = arrayOf(
        intArrayOf(0xFF60A0EC.toInt(), 0xFFFFC48C.toInt()), intArrayOf(0xFF10183A.toInt(), 0xFF384078.toInt()), intArrayOf(0xFF6EAAE4.toInt(), 0xFFECDCC8.toInt()),
        intArrayOf(0xFF161E42.toInt(), 0xFF464C76.toInt()), intArrayOf(0xFF96A4B8.toInt(), 0xFFD2D8E2.toInt()), intArrayOf(0xFFC4C8CE.toInt(), 0xFFE8E8E6.toInt()),
        intArrayOf(0xFF788AA2.toInt(), 0xFFB6C0CE.toInt()), intArrayOf(0xFF465670.toInt(), 0xFF8292AA.toInt()), intArrayOf(0xFFC4D4E8.toInt(), 0xFFF6F8FC.toInt()),
        intArrayOf(0xFF26283E.toInt(), 0xFF605C78.toInt()))
    private fun sky(x: X): IntArray = SKY[x.w?.let { WeatherRepo.iconIndex(it.code, it.isDay) } ?: 4]

    private fun polaroid(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        val frame = if (paperIdx(x.c) == 0) Color.rgb(252, 251, 248) else x.paper
        rot(cv, 2f, W / 2, H / 2) {
            val fr = RectF(W * .05f, H * .06f, W * .95f, H * .95f)
            cv.drawRoundRect(fr, s * .015f, s * .015f, shadowed(frame, s * .05f, s * .02f, 110))
            val bottom = if (x.tall) fr.height() * .2f else fr.height() * .24f
            val ph = RectF(fr.left + s * .045f, fr.top + s * .045f, fr.right - s * .045f, fr.bottom - bottom)
            val g = sky(x)
            cv.drawRect(ph, Paint().apply { shader = LinearGradient(0f, ph.top, 0f, ph.bottom, g[0], g[1], Shader.TileMode.CLAMP) })
            if (x.w?.isDay != false) cv.drawCircle(ph.left + ph.width() * .22f, ph.top + ph.height() * .3f, ph.height() * .6f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(ph.left + ph.width() * .22f, ph.top + ph.height() * .3f, ph.height() * .6f, 0x66FFF0C8, 0x00FFF0C8, Shader.TileMode.CLAMP) })
            val white = Color.WHITE
            val tcol = Ink.get(x.c, Ink.TEMP) ?: white
            if (x.tall) {
                ic(cv, x, ph.left + ph.width() * .32f, ph.top + ph.height() * .36f, ph.height() * .5f)
                cv.drawText(tempS(x), ph.right - s * .04f, ph.bottom - s * .05f, txt(x, ph.height() * .32f * Ink.tempScale(x.c), tcol, Paint.Align.RIGHT).apply { typeface = x.clockTf; setShadowLayer(8f, 0f, 2f, 0x55000000) })
            } else {
                ic(cv, x, ph.left + ph.height() * .55f, ph.centerY(), ph.height() * .78f)
                cv.drawText(tempS(x), ph.right - s * .05f, ph.bottom - s * .07f, txt(x, ph.height() * .48f * Ink.tempScale(x.c), tcol, Paint.Align.RIGHT).apply { typeface = x.clockTf; setShadowLayer(8f, 0f, 2f, 0x55000000) })
                if (showDesc(x)) cv.drawText(desc(x).uppercase(tr), ph.right - s * .05f, ph.top + s * .1f, fit(txt(x, s * .045f, Color.argb(230, 255, 255, 255), Paint.Align.RIGHT, .14f), desc(x).uppercase(tr), ph.width() * .5f))
            }
            val by = fr.bottom - bottom * .38f
            val time = now("HH:mm")
            if (showLoc(x)) cv.drawText(city(x), ph.left, by, fit(txt(x, bottom * .34f, x.loc), city(x), ph.width() * .5f))
            val right = if (showDate(x) && !x.tall) "$time  ·  ${now("EEE d MMM")}" else time
            val rp = clockFit(x, right, bottom * .3f, x.sub, Paint.Align.RIGHT, ph.width() * .48f)
            Ink.time(x.c, cv, right, ph.right, by, rp)
        }
    }

    /* ---------- 3. Bilet ---------- */
    private fun ticket(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        rot(cv, -1.5f, W / 2, H / 2) {
            val r = RectF(W * .04f, H * .08f, W * .96f, H * .92f)
            val rad = s * .05f
            val body = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
            val notch = s * .05f
            val vertical = !x.tall
            val cut = if (vertical) r.left + r.width() * .7f else r.top + r.height() * .66f
            val holes = Path().apply {
                if (vertical) { addCircle(cut, r.top, notch, Path.Direction.CW); addCircle(cut, r.bottom, notch, Path.Direction.CW) }
                else { addCircle(r.left, cut, notch, Path.Direction.CW); addCircle(r.right, cut, notch, Path.Direction.CW) }
            }
            body.op(holes, Path.Op.DIFFERENCE)
            cv.drawPath(body, shadowed(x.paper, s * .04f, s * .015f, 100))
            cv.save(); cv.clipPath(body)
            val bandH = r.height() * (if (vertical) .16f else .14f)
            cv.drawRect(r.left, r.top, if (vertical) cut else r.right, r.top + bandH, Paint().apply { color = x.acc })
            cv.restore()
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .008f; color = mix(x.paper, x.sub, .6f); pathEffect = DashPathEffect(floatArrayOf(s * .03f, s * .025f), 0f) }
            if (vertical) cv.drawLine(cut, r.top + notch * 1.2f, cut, r.bottom - notch * 1.2f, dash) else cv.drawLine(r.left + notch * 1.2f, cut, r.right - notch * 1.2f, cut, dash)
            val pad = r.width() * .05f
            val onAcc = if (lum(x.acc) > .6) Color.rgb(30, 30, 30) else Color.WHITE
            cv.drawText("HAVA & SAAT  ·  TEK YÖN", r.left + pad, r.top + bandH * .66f, fit(txt(x, bandH * .42f, onAcc, sp = .16f), "HAVA & SAAT  ·  TEK YÖN", (if (vertical) cut - r.left else r.width()) - pad * 2))
            val mainR = if (vertical) RectF(r.left + pad, r.top + bandH, cut - pad, r.bottom - r.height() * .06f) else RectF(r.left + pad, r.top + bandH, r.right - pad, cut - r.height() * .03f)
            val lab = { t: String, xx: Float, yy: Float -> cv.drawText(t, xx, yy, txt(x, mainR.height() * .075f, x.sub, sp = .2f)) }
            val time = now("HH:mm")
            if (vertical) {
                lab("NEREDE", mainR.left, mainR.top + mainR.height() * .18f)
                if (showLoc(x)) cv.drawText(city(x).uppercase(tr), mainR.left, mainR.top + mainR.height() * .4f, fit(txt(x, mainR.height() * .2f, x.loc, bold = true), city(x).uppercase(tr), mainR.width()))
                lab("SAAT", mainR.left, mainR.top + mainR.height() * .56f)
                Ink.time(x.c, cv, time, mainR.left, mainR.bottom - mainR.height() * .04f, clockFit(x, time, mainR.height() * .36f, x.ink, Paint.Align.LEFT, mainR.width() * .55f))
                if (showDate(x)) {
                    val dx = mainR.left + mainR.width() * .62f
                    lab("TARİH", dx, mainR.top + mainR.height() * .56f)
                    cv.drawText(now("d MMM yyyy").uppercase(tr), dx, mainR.top + mainR.height() * .74f, fit(txt(x, mainR.height() * .11f, x.ink), now("d MMM yyyy").uppercase(tr), mainR.right - dx))
                    cv.drawText(now("EEEE").uppercase(tr), dx, mainR.top + mainR.height() * .9f, fit(txt(x, mainR.height() * .08f, x.sub, sp = .1f), now("EEEE").uppercase(tr), mainR.right - dx))
                }
                val sr = RectF(cut + pad * .6f, r.top + r.height() * .06f, r.right - pad * .6f, r.bottom - r.height() * .06f)
                ic(cv, x, sr.centerX(), sr.top + sr.height() * .26f, min(sr.width() * .8f, sr.height() * .42f))
                cv.drawText(tempS(x), sr.centerX(), sr.top + sr.height() * .68f, txt(x, sr.height() * .22f * Ink.tempScale(x.c), x.temp, Paint.Align.CENTER).apply { typeface = x.clockTf })
                if (showDesc(x)) cv.drawText(desc(x), sr.centerX(), sr.top + sr.height() * .8f, fit(txt(x, sr.height() * .065f, x.sub, Paint.Align.CENTER), desc(x), sr.width()))
                barcode(cv, x, RectF(sr.left + sr.width() * .1f, sr.bottom - sr.height() * .1f, sr.right - sr.width() * .1f, sr.bottom))
            } else {
                if (showLoc(x)) cv.drawText(city(x).uppercase(tr), mainR.left, mainR.top + mainR.height() * .3f, fit(txt(x, mainR.height() * .16f, x.loc, bold = true), city(x).uppercase(tr), mainR.width()))
                Ink.time(x.c, cv, time, mainR.left, mainR.bottom - mainR.height() * .08f, clockFit(x, time, mainR.height() * .42f, x.ink, Paint.Align.LEFT, mainR.width() * .75f))
                if (showDate(x)) cv.drawText(now("d MMM").uppercase(tr), mainR.right, mainR.top + mainR.height() * .3f, txt(x, mainR.height() * .1f, x.sub, Paint.Align.RIGHT))
                val sr = RectF(r.left + pad, cut + r.height() * .03f, r.right - pad, r.bottom - r.height() * .03f)
                ic(cv, x, sr.left + sr.height() * .4f, sr.centerY(), sr.height() * .8f)
                cv.drawText(tempS(x), sr.left + sr.height() * .9f, sr.centerY() + sr.height() * .22f, txt(x, sr.height() * .55f * Ink.tempScale(x.c), x.temp).apply { typeface = x.clockTf })
                barcode(cv, x, RectF(sr.right - sr.width() * .3f, sr.centerY() - sr.height() * .2f, sr.right, sr.centerY() + sr.height() * .2f))
            }
        }
    }

    private fun barcode(cv: Canvas, x: X, r: RectF) {
        val p = Paint().apply { color = x.ink }
        var seed = 7L; var bx = r.left
        fun rnd(): Int { seed = (seed * 16807) % 2147483647; return (seed % 4).toInt() }
        val u = r.width() / 70f
        while (bx < r.right) { val w = (1 + rnd() % 3) * u; cv.drawRect(bx, r.top, min(bx + w, r.right), r.bottom, p); bx += w + (1 + rnd() % 2) * u }
    }

    /* ---------- 4. Takvim yaprağı ---------- */
    private fun calendar(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        val r = RectF(W * .045f, H * .1f, W * .955f, H * .93f)
        val head = r.height() * (if (x.tall) .2f else .24f)
        for ((i, f) in listOf(-.1f, -.05f).withIndex()) {
            val off = (2 - i) * s * .014f
            cv.drawRoundRect(RectF(r.left + off * .3f, r.top + head, r.right - off * .3f, r.bottom + off), s * .02f, s * .02f, shadowed(shade(x.paper, f), s * .02f, s * .01f, 60))
        }
        cv.drawRoundRect(RectF(r.left, r.top + head * .5f, r.right, r.bottom), s * .02f, s * .02f, shadowed(x.paper, s * .03f, s * .01f, 80))
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = x.acc }
        cv.drawRoundRect(RectF(r.left, r.top, r.right, r.top + head), s * .03f, s * .03f, hp)
        cv.drawRect(r.left, r.top + head * .5f, r.right, r.top + head, hp)
        // yırtık kalıntı
        val torn = Path(); var xx = r.left; var seed = 9L
        fun rnd(): Float { seed = (seed * 16807) % 2147483647; return (seed % 1000) / 1000f }
        torn.moveTo(r.left, r.top + head)
        while (xx < r.right) { xx = min(r.right, xx + s * (.02f + rnd() * .025f)); torn.lineTo(xx, r.top + head + s * (.005f + rnd() * .02f)) }
        torn.lineTo(r.right, r.top + head); torn.close()
        cv.drawPath(torn, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shade(x.paper, -.04f) })
        // halkalar
        val n = max(4, (r.width() / (s * .12f)).toInt())
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shade(x.acc, -.55f) }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(184, 188, 196); setShadowLayer(3f, 0f, 1f, 0x55000000) }
        for (i in 0 until n) {
            val hx = r.left + r.width() * (i + .5f) / n
            cv.drawCircle(hx, r.top + head * .22f, s * .016f, hole)
            cv.drawRoundRect(RectF(hx - s * .008f, r.top - s * .04f, hx + s * .008f, r.top + head * .24f), s * .008f, s * .008f, ring)
        }
        val onAcc = if (lum(x.acc) > .6) Color.rgb(40, 30, 30) else Color.WHITE
        val hy = r.top + head * .78f
        val pad = r.width() * .05f
        cv.drawText(now(if (x.tall) "MMMM" else "MMMM yyyy").uppercase(tr), r.left + pad, hy, txt(x, head * .26f, Color.argb(225, Color.red(onAcc), Color.green(onAcc), Color.blue(onAcc)), sp = .18f))
        cv.drawText(now("EEEE").uppercase(tr), r.right - pad, hy, fit(txt(x, head * .26f, onAcc, Paint.Align.RIGHT, .18f, true), now("EEEE").uppercase(tr), r.width() * .45f))
        val body = RectF(r.left + pad, r.top + head + r.height() * .06f, r.right - pad, r.bottom - r.height() * .07f)
        info(cv, x, body)
    }

    /* ---------- 5. Akordeon ---------- */
    private fun accordion(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        val r = RectF(W * .04f, H * .08f, W * .96f, H * .92f)
        val horiz = !x.tall
        val n = 3
        val sk = s * .035f
        // gölge
        cv.drawRect(RectF(r.left, r.top + s * .03f, r.right, r.bottom + s * .02f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0); maskFilter = android.graphics.BlurMaskFilter(s * .04f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
        for (i in 0 until n) {
            val even = i % 2 == 0
            val path = Path()
            if (horiz) {
                val a = r.left + r.width() * i / n; val b = r.left + r.width() * (i + 1) / n
                if (even) { path.moveTo(a, r.top); path.lineTo(b, r.top + sk); path.lineTo(b, r.bottom - sk); path.lineTo(a, r.bottom) }
                else { path.moveTo(a, r.top + sk); path.lineTo(b, r.top); path.lineTo(b, r.bottom); path.lineTo(a, r.bottom - sk) }
                path.close()
                val c1 = if (even) shade(x.paper, .04f) else shade(x.paper, -.12f); val c2 = if (even) shade(x.paper, -.1f) else shade(x.paper, .02f)
                cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(a, 0f, b, 0f, c1, c2, Shader.TileMode.CLAMP) })
            } else {
                val a = r.top + r.height() * i / n; val b = r.top + r.height() * (i + 1) / n
                if (even) { path.moveTo(r.left, a); path.lineTo(r.right, a); path.lineTo(r.right - sk, b); path.lineTo(r.left + sk, b) }
                else { path.moveTo(r.left + sk, a); path.lineTo(r.right - sk, a); path.lineTo(r.right, b); path.lineTo(r.left, b) }
                path.close()
                val c1 = if (even) shade(x.paper, .04f) else shade(x.paper, -.12f); val c2 = if (even) shade(x.paper, -.1f) else shade(x.paper, .02f)
                cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, a, 0f, b, c1, c2, Shader.TileMode.CLAMP) })
            }
        }
        val time = now("HH:mm")
        if (horiz) {
            val pw = r.width() / 3; val c0 = r.left + pw / 2; val c1 = r.left + pw * 1.5f; val c2 = r.left + pw * 2.5f
            if (showLoc(x)) cv.drawText(city(x).uppercase(tr), c0, r.top + r.height() * .26f, fit(txt(x, r.height() * .085f, x.loc, Paint.Align.CENTER, .16f), city(x).uppercase(tr), pw * .86f))
            cv.drawText(now("d"), c0, r.top + r.height() * .62f, txt(x, r.height() * .3f, x.acc, Paint.Align.CENTER).apply { typeface = x.clockTf })
            if (showDate(x)) cv.drawText(now("MMMM · EEE").uppercase(tr), c0, r.top + r.height() * .78f, fit(txt(x, r.height() * .065f, x.sub, Paint.Align.CENTER, .12f), now("MMMM · EEE").uppercase(tr), pw * .86f))
            Ink.time(x.c, cv, time, c1, r.centerY() + r.height() * .1f, clockFit(x, time, r.height() * .3f, x.ink, Paint.Align.CENTER, pw * .86f))
            ic(cv, x, c2, r.top + r.height() * .32f, min(pw * .6f, r.height() * .38f))
            cv.drawText(tempS(x), c2, r.top + r.height() * .72f, txt(x, r.height() * .22f * Ink.tempScale(x.c), x.temp, Paint.Align.CENTER).apply { typeface = x.clockTf })
            if (showDesc(x)) cv.drawText(desc(x), c2, r.top + r.height() * .84f, fit(txt(x, r.height() * .06f, x.sub, Paint.Align.CENTER), desc(x), pw * .86f))
        } else {
            val ph = r.height() / 3
            val loc = if (showLoc(x)) city(x) else ""
            val line1 = listOf(loc, if (showDate(x)) now("d MMM EEE") else "").filter { it.isNotEmpty() }.joinToString("  ·  ")
            cv.drawText(line1, r.centerX(), r.top + ph * .6f, fit(txt(x, ph * .24f, x.loc, Paint.Align.CENTER), line1, r.width() * .86f))
            Ink.time(x.c, cv, time, r.centerX(), r.top + ph * 1.72f, clockFit(x, time, ph * .7f, x.ink, Paint.Align.CENTER, r.width() * .8f))
            val tp = txt(x, ph * .42f * Ink.tempScale(x.c), x.temp).apply { typeface = x.clockTf }
            val tw = tp.measureText(tempS(x)); val isz = ph * .7f
            val sx = r.centerX() - (isz + tw + r.width() * .03f) / 2
            ic(cv, x, sx + isz / 2, r.top + ph * 2.5f, isz)
            cv.drawText(tempS(x), sx + isz + r.width() * .03f, r.top + ph * 2.66f, tp)
        }
    }

    /* ---------- 6. Etiket ---------- */
    private fun tag(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        val pointUp = x.tall
        val r = if (pointUp) RectF(W * .1f, H * .14f, W * .9f, H * .95f) else RectF(W * .1f, H * .1f, W * .96f, H * .9f)
        val cut = s * .16f
        val path = Path().apply {
            if (pointUp) { moveTo(r.left, r.top + cut); lineTo(r.left + cut, r.top); lineTo(r.right - cut, r.top); lineTo(r.right, r.top + cut); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom) }
            else { moveTo(r.left, r.top + cut); lineTo(r.left + cut, r.top); lineTo(r.right, r.top); lineTo(r.right, r.bottom); lineTo(r.left + cut, r.bottom); lineTo(r.left, r.bottom - cut) }
            close()
        }
        val hx = if (pointUp) r.centerX() else r.left + cut * .62f
        val hy = if (pointUp) r.top + cut * .62f else r.centerY()
        val hr = s * .035f
        path.op(Path().apply { addCircle(hx, hy, hr, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        rot(cv, if (pointUp) 3f else -3f, W / 2, H / 2) {
            cv.drawPath(path, shadowed(x.paper, s * .04f, s * .015f, 100))
            cv.drawCircle(hx, hy, hr * 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = hr * .45f; color = shade(x.paper, -.15f) })
            // ip
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .01f; color = Color.rgb(240, 236, 226); setShadowLayer(4f, 1f, 2f, 0x66000000); strokeCap = Paint.Cap.ROUND }
            val sPath = Path().apply { moveTo(hx, hy); if (pointUp) cubicTo(hx - s * .1f, hy - s * .1f, hx + s * .05f, -s * .05f, hx - s * .2f, -s * .1f) else cubicTo(hx - s * .1f, hy - s * .2f, s * .05f, s * .1f, -s * .05f, -s * .05f) }
            cv.drawPath(sPath, sp)
            val body = if (pointUp) RectF(r.left + r.width() * .08f, r.top + cut * 1.5f, r.right - r.width() * .08f, r.bottom - r.height() * .06f)
                else RectF(r.left + cut * 1.35f, r.top + r.height() * .1f, r.right - r.width() * .05f, r.bottom - r.height() * .1f)
            info(cv, x, body)
        }
    }

    /* ---------- 7. Kart yığını ---------- */
    private fun stack(cv: Canvas, x: X) {
        val W = x.W; val H = x.H; val s = x.s
        val back = if (paperIdx(x.c) == 0) Color.rgb(214, 226, 238) else shade(x.paper, -.08f)
        val bInk = if (lum(back) > .5) Color.rgb(40, 52, 70) else Color.rgb(230, 236, 246)
        val bSub = if (lum(back) > .5) Color.rgb(90, 104, 124) else Color.rgb(180, 190, 206)
        val d = x.w?.days?.getOrNull(1)
        if (!x.tall) {
            val br = RectF(W * .25f, H * .06f, W * .97f, H * .86f)
            rot(cv, -5f, W * .85f, H / 2) {
                cv.drawRoundRect(br, s * .04f, s * .04f, shadowed(back, s * .04f, s * .015f, 90))
                val sx = br.right - W * .11f
                cv.drawText("YARIN", sx, br.top + br.height() * .16f, txt(x, s * .05f, bSub, Paint.Align.CENTER, .2f))
                if (d != null) {
                    Combo.icon(x.c, cv, Style.iconPref(x.c, 0, 6, d.code, true), sx, br.top + br.height() * .4f, min(W * .15f, br.height() * .3f))
                    cv.drawText("${d.max}°", sx, br.top + br.height() * .72f, txt(x, s * .13f, bInk, Paint.Align.CENTER).apply { typeface = x.clockTf })
                    cv.drawText("${d.min}°", sx, br.top + br.height() * .86f, txt(x, s * .06f, bSub, Paint.Align.CENTER))
                }
            }
            val fr = RectF(W * .03f, H * .1f, W * .77f, H * .94f)
            rot(cv, 1.5f, fr.centerX(), fr.centerY()) {
                cv.drawRoundRect(fr, s * .04f, s * .04f, shadowed(x.paper, s * .05f, s * .02f, 110))
                info(cv, x, RectF(fr.left + fr.width() * .06f, fr.top + fr.height() * .08f, fr.right - fr.width() * .06f, fr.bottom - fr.height() * .08f))
            }
        } else {
            val br = RectF(W * .08f, H * .03f, W * .94f, H * .7f)
            rot(cv, -4f, W / 2, H * .2f) {
                cv.drawRoundRect(br, s * .05f, s * .05f, shadowed(back, s * .04f, s * .015f, 90))
                val y = br.top + H * .11f
                cv.drawText("YARIN", br.left + W * .07f, y, txt(x, s * .05f, bSub, sp = .2f))
                if (d != null) {
                    cv.drawText("${d.max}° / ${d.min}°", br.right - W * .07f, y, txt(x, s * .06f, bInk, Paint.Align.RIGHT))
                    Combo.icon(x.c, cv, Style.iconPref(x.c, 0, 6, d.code, true), br.right - W * .07f - txt(x, s * .06f, bInk).measureText("${d.max}° / ${d.min}°") - s * .06f, y - s * .02f, s * .09f)
                }
            }
            val fr = RectF(W * .05f, H * .2f, W * .95f, H * .96f)
            rot(cv, 1.5f, fr.centerX(), fr.centerY()) {
                cv.drawRoundRect(fr, s * .05f, s * .05f, shadowed(x.paper, s * .05f, s * .02f, 110))
                info(cv, x, RectF(fr.left + fr.width() * .08f, fr.top + fr.height() * .06f, fr.right - fr.width() * .08f, fr.bottom - fr.height() * .07f))
            }
        }
    }
}
