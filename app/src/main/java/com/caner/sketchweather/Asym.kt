package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Asimetrik, tipografi ağırlıklı widget'lar:
 * Dev saat (taşan rakamlar), Editoryal (kırpılmış dev derece), Dikey (yan yazı + basamaklı saat),
 * Kolaj (yırtık karton + ataçlı etiket + bantlı not).
 */
object Asym {
    const val BIG = 0; const val EDIT = 1; const val VERT = 2; const val COLLAGE = 3
    val ACCENT_NAMES = arrayOf("Renk paleti", "Turuncu", "Mercan", "Limon", "Nane", "Lila", "Gök mavisi", "Beyaz")
    private val ACCENTS = intArrayOf(0, 0xFFFF9F43.toInt(), 0xFFFF6B6B.toInt(), 0xFFF2E85C.toInt(), 0xFF5EE6B5.toInt(), 0xFFB79CFF.toInt(), 0xFF7CC7FF.toInt(), 0xFFFFFFFF.toInt())
    val FLIP_NAMES = arrayOf("Büyük öğe solda", "Büyük öğe sağda")
    private val tr = Locale("tr", "TR")

    fun accentIdx(c: Context) = Cfg.int(c, "asAccent", 1).coerceIn(0, ACCENTS.size - 1)
    fun accent(c: Context): Int = if (accentIdx(c) == 0) Palette.get(c).accentLight else ACCENTS[accentIdx(c)]
    fun flip(c: Context) = Cfg.int(c, "asFlip", 0).coerceIn(0, 1) == 1
    fun bgOpacity(c: Context) = Cfg.int(c, "opacity", 0).coerceIn(0, 100)

    private class Ctx(val c: Context, val W: Float, val H: Float, val flip: Boolean, val ink: Int, val sub: Int, val acc: Int, val shadow: Boolean) {
        fun x(v: Float) = if (flip) W - v else v
        fun al(a: Paint.Align) = if (!flip) a else when (a) { Paint.Align.LEFT -> Paint.Align.RIGHT; Paint.Align.RIGHT -> Paint.Align.LEFT; else -> a }
    }

    private fun paint(tf: Typeface, size: Float, col: Int, shadow: Boolean, al: Paint.Align, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size * Combo.textMul; color = col; textAlign = al; letterSpacing = spacing
            if (shadow) setShadowLayer(size * .08f, 0f, size * .04f, 0x99000000.toInt())
            Ink.shadowOverride(this)
        }

    private fun clockP(x: Ctx, size: Float, col: Int, al: Paint.Align): Paint {
        val p = Lock.clockPaint(x.c, size, col, Combo.Look(0, col, col, col, x.shadow, 0))
        p.textAlign = al
        return p
    }

    private fun pinAt(c: Context, cv: Canvas, x: Float, base: Float, s: Float, col: Int) {
        try {
            val d = c.getDrawable(R.drawable.pin)!!.mutate(); d.setTint(col)
            d.setBounds(x.toInt(), (base - s * .95f).toInt(), (x + s).toInt(), (base + s * .05f).toInt()); d.draw(cv)
        } catch (e: Exception) { }
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt()
        val Hi = (hdp.coerceIn(60, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val W = Wi.toFloat(); val H = Hi.toFloat()
        val op = bgOpacity(c) / 100f
        val ink = Style.textColor(c) ?: Color.WHITE
        val sub = Ink.get(c, Ink.SUB) ?: Color.argb(205, Color.red(ink), Color.green(ink), Color.blue(ink))
        val x = Ctx(c, W, H, flip(c), ink, sub, Ink.get(c, Ink.ACC) ?: accent(c), op < .35f && kind != COLLAGE)
        if (op > 0f && kind != COLLAGE) {
            val r = min(W, H) * .14f
            cv.drawRoundRect(RectF(6f, 6f, W - 6f, H - 6f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((225 * op).toInt(), 22, 25, 32) })
        }
        when (kind) {
            BIG -> big(cv, x, w)
            EDIT -> editorial(cv, x, w)
            VERT -> vertical(cv, x, w)
            else -> collage(cv, x, w)
        }
        return b
    }

    /** Dikey yazıyı verilen uzunluğa sığdırır. */
    private fun fit(p: Paint, t: String, maxLen: Float): Paint { val m = p.measureText(t); if (m > maxLen) p.textSize *= maxLen / m; return p }

    private val now get() = Date()
    private fun fmt(p: String) = SimpleDateFormat(p, tr).format(now)

    /** Dev saat: saat rakamları alttan taşar, dakika vurgu renginde yanında; konum kenarda dikey. */
    private fun big(cv: Canvas, x: Ctx, w: Weather?) {
        val c = x.c; val W = x.W; val H = x.H
        val cf = Style.clockTypeface(c, Style.font(c)); val tf = Style.textTypeface(c, Style.font(c))
        val hp = clockP(x, H * 1.18f, x.ink, x.al(Paint.Align.LEFT))
        val hh = fmt("HH")
        val hx = x.x(H * .05f)
        cv.drawText(hh, hx, H * 1.0f, hp)
        val hw = hp.measureText(hh)
        val col2 = x.x(H * .05f + hw + H * .08f)
        val mp = clockP(x, H * .5f, Ink.get(c, Ink.MIN) ?: x.acc, x.al(Paint.Align.LEFT))
        cv.drawText(fmt("mm"), col2, H * .48f, mp)
        cv.drawText(fmt("EEE d MMM").uppercase(tr), col2, H * .62f, paint(tf, H * .075f, x.sub, x.shadow, x.al(Paint.Align.LEFT), .12f))
        val icS = H * .3f
        val icCx = if (x.flip) col2 - icS / 2 else col2 + icS / 2
        if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), icCx, H * .8f, icS)
        val tx = if (x.flip) col2 - icS - H * .04f else col2 + icS + H * .04f
        val ex = if (x.flip) H * .1f else W - H * .1f
        val tStr = if (w != null) "${w.temp}°" else "--°"
        val tpp = paint(cf, H * .2f * Ink.tempScale(c), Ink.get(c, Ink.TEMP) ?: x.ink, x.shadow, x.al(Paint.Align.LEFT))
        val room = if (x.flip) tx - (ex + H * .07f) else (ex - H * .07f) - tx
        fit(tpp, tStr, room.coerceAtLeast(H * .1f))
        cv.drawText(tStr, tx, H * .87f, tpp)
        // konum: kenar boyunca dikey
        val city = WeatherRepo.city(c).uppercase(tr)
        val lp = fit(paint(tf, H * .085f, Ink.get(c, Ink.LOC) ?: x.acc, x.shadow, Paint.Align.CENTER, .22f).apply { isFakeBoldText = true }, city, H * .86f)
        cv.save(); cv.rotate(-90f, ex, H / 2); cv.drawText(city, ex, H / 2 + H * .03f, lp); cv.restore()
    }

    /** Editoryal: ince çizgiler, büyük harfli başlıklar, kenardan taşan dev derece. */
    private fun editorial(cv: Canvas, x: Ctx, w: Weather?) {
        val c = x.c; val W = x.W; val H = x.H
        val cf = Style.clockTypeface(c, Style.font(c)); val tf = Style.textTypeface(c, Style.font(c))
        val pad = H * .1f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = x.ink; alpha = 140; strokeWidth = H * .008f; if (x.shadow) setShadowLayer(3f, 0f, 1f, 0x88000000.toInt()) }
        cv.drawLine(pad, H * .2f, W - pad, H * .2f, line)
        cv.drawLine(pad, H * .84f, W - pad, H * .84f, line)
        val city = WeatherRepo.city(c).uppercase(tr)
        val cp = paint(tf, H * .075f, Ink.get(c, Ink.LOC) ?: x.ink, x.shadow, x.al(Paint.Align.LEFT), .18f).apply { isFakeBoldText = true }
        val l = x.x(pad)
        pinAt(c, cv, if (x.flip) l - cp.measureText(city) - H * .085f else l, H * .145f, H * .07f, x.acc)
        cv.drawText(city, if (x.flip) l else l + H * .085f, H * .145f, cp)
        cv.drawText(fmt("d.MM · EEEE").uppercase(tr), x.x(W - pad), H * .145f, paint(tf, H * .065f, x.sub, x.shadow, x.al(Paint.Align.RIGHT), .14f))
        // dev derece, kenardan taşar
        val temp = if (w != null) "${w.temp}°" else "--°"
        val tp = paint(cf, H * .86f * Ink.tempScale(c), Ink.get(c, Ink.TEMP) ?: x.ink, x.shadow, x.al(Paint.Align.RIGHT))
        cv.save(); cv.clipRect(0f, H * .2f, W, H * .84f)
        cv.drawText(temp, x.x(W + H * .06f), H * .84f + H * .06f, tp)
        cv.restore()
        // sol sütun
        val desc = if (w != null) WeatherRepo.label(w.code) else "Yükleniyor…"
        val words = desc.split(" ")
        val dp = paint(tf, H * .1f, x.acc, x.shadow, x.al(Paint.Align.LEFT))
        val iconS = H * .2f
        val ix = if (x.flip) l - iconS / 2 else l + iconS / 2
        if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), ix, H * .34f, iconS)
        val tx = if (x.flip) l - iconS - H * .04f else l + iconS + H * .04f
        cv.drawText(words.first(), tx, H * .38f, dp)
        if (words.size > 1) cv.drawText(words.drop(1).joinToString(" "), l, H * .52f, dp)
        Ink.time(c, cv, fmt("HH:mm"), l, H * .78f, clockP(x, H * .22f, x.ink, x.al(Paint.Align.LEFT)))
        val d = w?.days?.firstOrNull()
        val foot = listOfNotNull(d?.let { "Y ${it.max}°  D ${it.min}°" }, w?.rainChance?.let { "Yağmur %$it" }, w?.sunset?.let { "Gün batımı $it" }).joinToString("   ·   ")
        cv.drawText(foot, l, H * .95f, paint(tf, H * .062f, x.sub, x.shadow, x.al(Paint.Align.LEFT), .04f))
    }

    /** Dikey: kenarda dikey konum, basamaklı saat/dakika, köşede ikon. */
    private fun vertical(cv: Canvas, x: Ctx, w: Weather?) {
        val c = x.c; val W = x.W; val H = x.H
        val s = min(W, H); val ox = (W - s) / 2; val oy = (H - s) / 2
        val cf = Style.clockTypeface(c, Style.font(c)); val tf = Style.textTypeface(c, Style.font(c))
        fun X(v: Float) = if (x.flip) ox + s - v else ox + v
        val city = WeatherRepo.city(c).uppercase(tr)
        val lp = fit(paint(tf, s * .085f, Ink.get(c, Ink.LOC) ?: x.acc, x.shadow, Paint.Align.CENTER, .2f).apply { isFakeBoldText = true }, city, s * .86f)
        val ex = X(s * .08f)
        cv.save(); cv.rotate(-90f, ex, oy + s / 2); cv.drawText(city, ex, oy + s / 2 + s * .03f, lp); cv.restore()
        cv.drawText(fmt("HH"), X(s * .17f), oy + s * .46f, clockP(x, s * .4f, x.ink, x.al(Paint.Align.LEFT)))
        cv.drawText(fmt("mm"), X(s * .36f), oy + s * .9f, clockP(x, s * .4f, Ink.get(c, Ink.MIN) ?: x.acc, x.al(Paint.Align.LEFT)))
        if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), X(s * .8f), oy + s * .2f, s * .3f)
        cv.drawText(if (w != null) "${w.temp}°" else "--°", X(s * .8f), oy + s * .47f, paint(cf, s * .13f * Ink.tempScale(c), Ink.get(c, Ink.TEMP) ?: x.ink, x.shadow, Paint.Align.CENTER))
    }

    /** Kolaj: yırtık karton üstünde saat, ataçlı etikette konum, bantlı notta hava. */
    private fun collage(cv: Canvas, x: Ctx, w: Weather?) {
        val c = x.c; val W = x.W; val H = x.H
        val cf = Style.clockTypeface(c, Style.font(c)); val tf = Style.textTypeface(c, Style.font(c))
        val a = (if (bgOpacity(c) == 0) 100 else bgOpacity(c)) / 100f
        val leftW = W * .52f
        val torn = if (!x.flip) RectF(W * .02f, H * .08f, leftW, H * .94f) else RectF(W - leftW, H * .08f, W * .98f, H * .94f)
        val tag = if (!x.flip) RectF(leftW - W * .01f, H * .02f, W * .98f, H * .5f) else RectF(W * .02f, H * .02f, W - leftW + W * .01f, H * .5f)
        val note = if (!x.flip) RectF(leftW + W * .03f, H * .44f, W * .97f, H * .98f) else RectF(W * .03f, H * .44f, W - leftW - W * .03f, H * .98f)
        val (tInk, tSub) = Paper.ink(Paper.TORN); val (cInk, cSub) = Paper.ink(Paper.CLIP); val (nInk, nSub) = Paper.ink(Paper.TAPE)
        // karton + saat
        cv.save(); cv.rotate(-3f, torn.centerX(), torn.centerY())
        Paper.torn(cv, torn, 11, a)
        val ch = torn.height()
        Ink.time(c, cv, fmt("HH:mm"), torn.centerX(), torn.top + ch * .58f, Lock.clockPaint(c, ch * .36f, tInk, Combo.Look(0, tInk, tInk, tInk, false, 0)).apply { textAlign = Paint.Align.CENTER })
        cv.drawText(fmt("EEEE, d MMMM"), torn.centerX(), torn.top + ch * .76f, paint(tf, ch * .085f, tSub, false, Paint.Align.CENTER))
        cv.restore()
        // bantlı not + hava
        cv.save(); cv.rotate(-2f, note.centerX(), note.centerY())
        Paper.taped(cv, note, 5, a, angle = 0f)
        val nh = note.height()
        if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), note.left + nh * .42f, note.top + nh * .55f, nh * .52f)
        cv.drawText(if (w != null) "${w.temp}°" else "--°", note.left + nh * .78f, note.top + nh * .58f, paint(cf, nh * .3f, nInk, false, Paint.Align.LEFT))
        cv.drawText(if (w != null) WeatherRepo.label(w.code) else "", note.left + nh * .8f, note.top + nh * .78f, paint(tf, nh * .11f, nSub, false, Paint.Align.LEFT).also {
            val maxW = note.right - note.left - nh * .95f; val tw = it.measureText(if (w != null) WeatherRepo.label(w.code) else "")
            if (tw > maxW && tw > 0) it.textSize = it.textSize * maxW / tw
        })
        cv.restore()
        // ataçlı etiket + konum
        Paper.clipped(cv, tag, 3, a, angle = 2.5f)
        val th = tag.height()
        val city = WeatherRepo.city(c)
        cv.save(); cv.rotate(2.5f, tag.centerX(), tag.centerY())
        val cp = paint(tf, th * .2f, cInk, false, Paint.Align.LEFT).apply { isFakeBoldText = true }
        val maxW = tag.width() * .7f
        if (cp.measureText(city) > maxW) cp.textSize = cp.textSize * maxW / cp.measureText(city)
        val lx = tag.left + tag.width() * .2f
        pinAt(c, cv, lx - th * .2f, tag.top + th * .56f, th * .17f, 0xFFE2683C.toInt())
        cv.drawText(city, lx, tag.top + th * .56f, cp)
        val c2 = WeatherRepo.city2(c)
        if (c2.isNotEmpty()) cv.drawText(c2, lx, tag.top + th * .78f, paint(tf, th * .12f, cSub, false, Paint.Align.LEFT))
        cv.restore()
    }
}
