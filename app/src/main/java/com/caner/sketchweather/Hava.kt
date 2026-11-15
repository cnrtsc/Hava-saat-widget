package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Saat + konum + hava serisi: her biri ayrı düzen (renk varyantı yok, renkleri kullanıcı seçer).
 * Kural: her öğenin kendi kutusu var. Yazılar kutularına sığacak kadar küçülür, büyütme sınırlıdır;
 * saat, derece, ikon, yazı ve logo boyutları birbirinden bağımsızdır ve birbirine binmez.
 */
object Hava {
    const val KOLON = 0; const val UFUK = 1; const val KOSE = 2; const val CERCEVE = 3; const val IKILI = 4
    const val KARE = 5; const val DIKEY = 6; const val SERIT = 7; const val PANO = 8
    const val AKORDIYON = 9; const val KATLANIR = 10; const val HAP = 11; const val INCE = 12

    private val tr = Locale("tr", "TR")
    private val RED = Color.rgb(169, 4, 50); private val YEL = Color.rgb(253, 185, 18)

    private class Th(val bg: Int, val ink: Int, val sub: Int, val hair: Int, val card: Int)
    private val DARK = Th(Color.rgb(18, 18, 20), Color.WHITE, Color.rgb(160, 160, 168), Color.rgb(52, 52, 58), Color.rgb(36, 36, 40))
    private val LIGHT = Th(Color.rgb(250, 248, 244), Color.rgb(26, 22, 26), Color.rgb(118, 112, 116), Color.rgb(226, 222, 218), Color.rgb(238, 234, 228))
    private val BORDO = Th(Color.rgb(110, 8, 30), Color.WHITE, Color.rgb(255, 200, 208), Color.rgb(150, 40, 64), Color.rgb(132, 20, 44))

    private class X(val c: Context, val w: Weather?, val cv: Canvas, val sx: Float, val sy: Float, val u: Float, val op: Float, val th: Th,
                    val ink: Int, val sub: Int, val temp: Int, val clockTf: Typeface, val tempTf: Typeface, val textTf: Typeface, val boldTf: Typeface,
                    val fakeBold: Boolean, val shadow: Boolean, val isc: Float, val lines: Boolean)

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, form: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(80, 560) * k).toInt(); val Hi = (hdp.coerceIn(30, 560) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = TCanvas(b)
        val W = Wi.toFloat(); val H = Hi.toFloat()
        val (bw, bh) = when (form) {
            KARE -> 170f to 170f; DIKEY -> 170f to 250f; SERIT -> 360f to 90f; PANO -> 360f to 360f; INCE -> 360f to 60f; else -> 360f to 190f
        }
        val sx = W / bw; val sy = H / bh; val u = min(sx, sy)
        val op = Style.opacity(c) / 100f
        val th = when (form) { UFUK, DIKEY, KATLANIR -> BORDO; KOSE, KARE -> LIGHT; else -> DARK }
        val rad = min(22 * u, H / 2)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, W, H), rad, rad, Path.Direction.CW) }
        if (op > .02f && form != AKORDIYON && form != KATLANIR) cv.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = th.bg; alpha = (op * 255).toInt() })
        Ink.setBg(if (op >= .5f) th.bg else null)
        val ink = Ink.get(c, Ink.TEXT) ?: Style.textColor(c) ?: th.ink
        val sub = Ink.get(c, Ink.SUB) ?: th.sub
        val id = Cfg.current(); val pr = WeatherRepo.prefs(c)
        fun has(key: String) = id != null && pr.contains(Cfg.wkey(id, key))
        val defClock = when (form) { KOLON, PANO -> R.font.f_outfit_c; UFUK -> R.font.f_barlowb_c; else -> R.font.f_outfitb_c }
        val clockTf = if (has("font")) Style.clockTypeface(c, Style.font(c)) else c.resources.getFont(defClock)
        val tempTf = if (has("font")) Style.clockTypeface(c, Style.font(c)) else c.resources.getFont(R.font.f_outfit_c)
        val customText = has("font") || has("textFont")
        val textTf = if (customText) Style.textTypeface(c, Style.font(c)) else c.resources.getFont(R.font.f_outfit_t)
        val boldTf = if (customText) textTf else c.resources.getFont(R.font.f_outfitb_c)
        val x = X(c, w, cv, sx, sy, u, op, th, ink, sub, Ink.get(c, Ink.TEMP) ?: ink, clockTf, tempTf, textTf, boldTf, customText,
            op < .45f, Style.iconScale(c), GS.lineStyle(c) != 4)
        cv.save(); cv.clipPath(clip)
        when (form) {
            UFUK -> ufuk(x); KOSE -> kose(x); CERCEVE -> cerceve(x); IKILI -> ikili(x); KARE -> kare(x); DIKEY -> dikey(x)
            SERIT -> serit(x); PANO -> pano(x); AKORDIYON -> akordiyon(x); KATLANIR -> katlanir(x); HAP -> hap(x); INCE -> ince(x)
            else -> kolon(x)
        }
        cv.restore()
        return b
    }

    /* ================= yardımcılar (hepsi dp cinsinden) ================= */
    private fun X.px(v: Float) = v * sx
    private fun X.py(v: Float) = v * sy
    private fun al(a: Char) = when (a) { 'c' -> Paint.Align.CENTER; 'r' -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }

    /** Yazı: kutusuna (maxW) sığar; yazı boyutu ayarı en fazla %120 büyütür ki komşusuna taşmasın. */
    private fun t(x: X, s: String, xd: Float, yd: Float, size: Float, col: Int = x.ink, a: Char = 'l', maxW: Float = 0f, bold: Boolean = false) {
        if (s.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) x.boldTf else x.textTf; textSize = size * x.u * min(Combo.textMul, 1.2f); color = col; textAlign = al(a)
            if (bold && x.fakeBold) isFakeBoldText = true
            if (x.shadow) setShadowLayer(textSize * .08f, 0f, textSize * .04f, 0x99000000.toInt())
            Ink.shadowOverride(this)
        }
        if (maxW > 0) { val m = p.measureText(s); val mx = maxW * x.sx; if (m > mx && m > 0) p.textSize *= mx / m }
        x.cv.drawText(s, x.px(xd), x.py(yd), p)
    }
    /** Saat: yatayda maxW, dikeyde baseline'dan topD'ye kadar olan alana sığar. */
    private fun clock(x: X, xd: Float, baseD: Float, size: Float, maxW: Float, topD: Float, a: Char = 'l') {
        val time = SimpleDateFormat("HH:mm", tr).format(Date())
        val col = Ink.get(x.c, Ink.CLOCK) ?: x.ink
        val p = Lock.clockPaintTf(x.c, x.clockTf, size * x.u, col, Combo.Look(0, col, col, col, x.shadow, 0)).apply { textAlign = al(a) }
        val capMax = (baseD - topD) * x.sy / .74f
        if (p.textSize > capMax) p.textSize = capMax
        val m = Ink.measure(x.c, time, p); val mx = maxW * x.sx
        if (m > mx && m > 0) p.textSize *= mx / m
        Ink.time(x.c, x.cv, time, x.px(xd), x.py(baseD), p)
    }
    /** Derece: sadece Derece boyutu ayarıyla büyür; kutusuna (maxW ve topD) sığar. */
    private fun temp(x: X, xd: Float, baseD: Float, size: Float, maxW: Float, topD: Float, a: Char = 'l') {
        val s = x.w?.let { "${it.temp}°" } ?: "--°"
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = x.tempTf; textSize = size * x.u * Ink.tempScale(x.c); color = x.temp; textAlign = al(a)
            if (x.shadow) setShadowLayer(textSize * .05f, 0f, textSize * .03f, 0x99000000.toInt())
            Ink.shadowOverride(this)
        }
        val capMax = (baseD - topD) * x.sy / .74f
        if (p.textSize > capMax) p.textSize = capMax
        val m = p.measureText(s); val mx = maxW * x.sx
        if (m > mx && m > 0) p.textSize *= mx / m
        x.cv.drawText(s, x.px(xd), x.py(baseD), p)
    }
    /** İkon kutusu: büyütme kutunun en fazla %25 dışına taşar. Çizilen genişlik (dp). */
    private fun icon(x: X, code: Int, day: Boolean, cxd: Float, cyd: Float, boxDp: Float): Float {
        val drawn = min(boxDp * x.isc, boxDp * 1.25f)
        Combo.icon(x.c, x.cv, Style.iconPref(x.c, 22, 0, code, day), x.px(cxd), x.py(cyd), drawn * x.u / x.isc)
        return drawn
    }
    private fun nowIcon(x: X, cxd: Float, cyd: Float, boxDp: Float): Float = x.w?.let { icon(x, it.code, it.isDay, cxd, cyd, boxDp) } ?: boxDp
    /** Logo: boyut ayarı en fazla %125'e kadar büyür. */
    private fun logo(x: X, cxd: Float, cyd: Float, rd: Float) {
        val ls = GS.logoSize(x.c) / 100f
        val drawn = min(rd * ls, rd * 1.25f)
        GS.drawLogo(x.c, x.cv, x.px(cxd), x.py(cyd), drawn * x.u / ls)
    }
    private fun paint(col: Int, x: X) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = (Color.alpha(col) * max(x.op, .0f)).toInt().coerceAtLeast(if (x.op < .02f) 0 else 1) }
    private fun bar(x: X, x0: Float, y0: Float, x1: Float, y1: Float, col: Int) {
        if (!x.lines) return
        x.cv.drawRect(x.px(x0), x.py(y0), max(x.px(x1), x.px(x0) + 1f), max(x.py(y1), x.py(y0) + 1f), Paint().apply { color = col })
    }
    private fun hair(x: X, x0: Float, y0: Float, x1: Float, y1: Float) =
        x.cv.drawRect(x.px(x0), x.py(y0), max(x.px(x1), x.px(x0) + max(1f, .6f * x.u)), max(x.py(y1), x.py(y0) + max(1f, .6f * x.u)), Paint().apply { color = x.th.hair; alpha = (255 * max(x.op, .35f)).toInt() })
    private fun card(x: X, x0: Float, y0: Float, x1: Float, y1: Float, r: Float, col: Int = x.th.card) {
        if (x.op < .02f) return
        x.cv.drawRoundRect(RectF(x.px(x0), x.py(y0), x.px(x1), x.py(y1)), r * x.u, r * x.u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = (255 * x.op).toInt() })
    }
    private fun loc(x: X) = if (Combo.show(x.c, "loc")) WeatherRepo.city(x.c) else ""
    private fun date(x: X, f: String) = if (Combo.show(x.c, "date")) SimpleDateFormat(f, tr).format(Date()) else ""
    private fun feels(x: X) = x.w?.feels?.takeIf { Combo.show(x.c, "feels") }
    private fun rain(x: X) = x.w?.rainChance?.takeIf { Combo.show(x.c, "rain") }
    private fun days(x: X): List<Day> = if (Combo.show(x.c, "fc")) x.w?.days?.drop(1)?.take(3) ?: emptyList() else emptyList()
    private fun dn(d: Day) = try { SimpleDateFormat("EEE", tr).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!!).take(3) } catch (e: Exception) { "" }
    private fun extras(x: X, short: Boolean, sep: String = "  ·  "): String {
        val o = ArrayList<String>()
        feels(x)?.let { o.add(if (short) "His $it°" else "Hissedilen $it°") }
        rain(x)?.let { o.add("Yağış %$it") }
        return o.joinToString(sep)
    }

    /* ================= tasarımlar ================= */

    /** 1 · Kolon: sol kenarda dikey sarı-kırmızı çizgi; solda konum/saat/tarih, sağda ikon-derece ve 3 gün. */
    private fun kolon(x: X) {
        bar(x, 0f, 0f, 4f, 104f, YEL); bar(x, 0f, 104f, 4f, 190f, RED)
        t(x, loc(x), 22f, 36f, 15f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 158f, bold = true)
        logo(x, 198f, 31f, 12f)
        clock(x, 20f, 108f, 64f, 184f, 50f)
        t(x, date(x, "EEEE, d MMMM"), 22f, 134f, 13f, x.sub, maxW = 184f)
        t(x, extras(x, false), 22f, 160f, 12f, x.sub, maxW = 186f)
        hair(x, 222f, 24f, 222f, 166f)
        val dr = nowIcon(x, 256f, 52f, 46f)
        temp(x, 344f, 66f, 40f, 344f - (256f + dr / 2 + 6f), 30f, 'r')
        days(x).forEachIndexed { i, d ->
            val y = 102f + i * 26f
            t(x, dn(d), 236f, y + 5f, 13f, maxW = 30f, bold = true)
            icon(x, d.code, true, 282f, y, 18f)
            t(x, "${d.max}°/${d.min}°", 344f, y + 5f, 13f, x.sub, 'r', 46f)
        }
    }

    /** 2 · Ufuk: saatin altında asimetrik çizgi; sağda ikon-derece, altta tarih/ek bilgi ve 3 gün sütunu. */
    private fun ufuk(x: X) {
        t(x, loc(x), 20f, 34f, 15f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 268f, bold = true)
        logo(x, 336f, 30f, 13f)
        clock(x, 16f, 112f, 72f, 204f, 48f)
        bar(x, 20f, 123f, 150f, 125f, YEL); bar(x, 154f, 123f, 204f, 125f, RED)
        val dr = nowIcon(x, 262f, 80f, 50f)
        val tx = 262f + dr / 2 + 6f
        temp(x, tx, 96f, 34f, 344f - tx, 62f)
        t(x, date(x, "EEEE, d MMMM"), 20f, 150f, 13f, maxW = 200f)
        t(x, extras(x, false), 20f, 170f, 12f, x.sub, maxW = 200f)
        days(x).forEachIndexed { i, d ->
            val cx = 254f + i * 36f
            t(x, dn(d), cx, 140f, 10.5f, x.sub, 'c', 32f)
            icon(x, d.code, true, cx, 155f, 16f)
            t(x, "${d.max}°", cx, 177f, 12f, a = 'c', maxW = 32f)
        }
    }

    /** 3 · Köşe: sağ üst köşede çapraz sarı-kırmızı bant; büyük saat, sağda ikon-derece, altta 3 gün satırı. */
    private fun kose(x: X) {
        if (x.lines) {
            fun band(a: Float, col: Int) = x.cv.drawPath(Path().apply {
                moveTo(x.px(a), 0f); lineTo(x.px(a + 8f), 0f); lineTo(x.px(360f), x.py(98f - (a - 262f))); lineTo(x.px(360f), x.py(90f - (a - 262f))); close()
            }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
            band(262f, YEL); band(276f, RED)
        }
        t(x, loc(x), 20f, 36f, 15f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 210f, bold = true)
        clock(x, 18f, 116f, 70f, 210f, 50f)
        t(x, date(x, "EEEE, d MMMM"), 20f, 142f, 13f, x.sub, maxW = 210f)
        val dr = nowIcon(x, 258f, 112f, 46f)
        val tx = 258f + dr / 2 + 6f
        temp(x, tx, 126f, 36f, 344f - tx, 96f)
        t(x, extras(x, true, " · "), 344f, 152f, 11f, x.sub, 'r', 110f)
        days(x).forEachIndexed { i, d ->
            val x0 = 20f + i * 84f
            t(x, dn(d), x0, 176f, 12.5f, maxW = 28f, bold = true)
            icon(x, d.code, true, x0 + 40f, 171f, 16f)
            t(x, "${d.max}°", x0 + 52f, 176f, 12.5f, x.sub, maxW = 28f)
        }
        logo(x, 336f, 170f, 11f)
    }

    /** 4 · Çerçeve: sol üst sarı, sağ alt kırmızı köşebent; solda hava, ortada konum-saat-tarih-logo, sağda 3 gün. */
    private fun cerceve(x: X) {
        bar(x, 12f, 12f, 60f, 14f, YEL); bar(x, 12f, 12f, 14f, 60f, YEL)
        bar(x, 300f, 176f, 348f, 178f, RED); bar(x, 346f, 130f, 348f, 178f, RED)
        nowIcon(x, 62f, 60f, 46f)
        temp(x, 62f, 122f, 36f, 84f, 92f, 'c')
        t(x, feels(x)?.let { "His $it°" } ?: "", 62f, 148f, 11.5f, x.sub, 'c', 84f)
        t(x, rain(x)?.let { "Yağış %$it" } ?: "", 62f, 165f, 11.5f, x.sub, 'c', 84f)
        hair(x, 116f, 40f, 116f, 150f); hair(x, 244f, 40f, 244f, 150f)
        t(x, loc(x), 180f, 44f, 14f, Ink.get(x.c, Ink.LOC) ?: x.ink, 'c', 112f, bold = true)
        clock(x, 180f, 108f, 54f, 112f, 58f, 'c')
        t(x, date(x, "EEEE, d MMMM"), 180f, 132f, 12f, x.sub, 'c', 112f)
        logo(x, 180f, 160f, 12f)
        days(x).forEachIndexed { i, d ->
            val y = 62f + i * 32f
            t(x, dn(d), 256f, y + 5f, 12.5f, maxW = 28f, bold = true)
            icon(x, d.code, true, 292f, y, 18f)
            t(x, "${d.max}°", 334f, y + 5f, 12.5f, x.sub, 'r', 30f)
        }
    }

    /** 5 · İkili: üstte konum-saat-tarih, ortada iki renkli çizgi, altta iç kart (hava + 3 gün). */
    private fun ikili(x: X) {
        t(x, loc(x), 20f, 32f, 15f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 268f, bold = true)
        logo(x, 336f, 28f, 12f)
        clock(x, 18f, 88f, 52f, 196f, 44f)
        t(x, date(x, "EEEE, d MMMM"), 344f, 88f, 13f, x.sub, 'r', 124f)
        bar(x, 20f, 100f, 182f, 101.5f, YEL); bar(x, 182f, 100f, 344f, 101.5f, RED)
        card(x, 12f, 110f, 348f, 180f, 14f)
        val dr = nowIcon(x, 42f, 145f, 40f)
        val tx = 42f + dr / 2 + 4f
        temp(x, tx, 158f, 32f, 118f - tx, 128f)
        t(x, feels(x)?.let { "Hissedilen $it°" } ?: "", 124f, 140f, 11.5f, x.sub, maxW = 92f)
        t(x, rain(x)?.let { "Yağış %$it" } ?: "", 124f, if (feels(x) != null) 160f else 140f, 11.5f, x.sub, maxW = 92f)
        days(x).forEachIndexed { i, d ->
            val cx = 244f + i * 38f
            t(x, dn(d), cx, 130f, 10.5f, x.sub, 'c', 34f)
            icon(x, d.code, true, cx, 146f, 16f)
            t(x, "${d.max}°", cx, 170f, 12f, a = 'c', maxW = 34f)
        }
    }

    /** 6 · Kare (2x2): konumun yanında kısa dikey çift çizgi; ortada saat, altta hava. */
    private fun kare(x: X) {
        bar(x, 14f, 18f, 16.5f, 36f, YEL); bar(x, 19f, 18f, 21.5f, 36f, RED)
        t(x, loc(x), 28f, 32f, 13f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 98f, bold = true)
        logo(x, 150f, 26f, 10f)
        clock(x, 85f, 94f, 50f, 146f, 46f, 'c')
        t(x, date(x, "EEEE, d MMMM"), 85f, 114f, 11.5f, x.sub, 'c', 146f)
        val dr = nowIcon(x, 30f, 144f, 30f)
        val tx = 30f + dr / 2 + 4f
        temp(x, tx, 153f, 24f, 104f - tx, 128f)
        t(x, feels(x)?.let { "His $it°" } ?: "", 156f, 140f, 10.5f, x.sub, 'r', 50f)
        t(x, rain(x)?.let { "Yağış %$it" } ?: "", 156f, 156f, 10.5f, x.sub, 'r', 50f)
    }

    /** 7 · Dikey (2x3) */
    private fun dikey(x: X) {
        t(x, loc(x), 14f, 32f, 14f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 112f, bold = true)
        t(x, date(x, "EEEE, d MMMM"), 14f, 50f, 11f, x.sub, maxW = 112f)
        logo(x, 148f, 28f, 12f)
        clock(x, 12f, 108f, 56f, 144f, 62f)
        bar(x, 14f, 121f, 100f, 123f, YEL); bar(x, 104f, 121f, 156f, 123f, RED)
        val dr = nowIcon(x, 36f, 152f, 42f)
        val tx = 36f + dr / 2 + 5f
        temp(x, tx, 166f, 34f, 156f - tx, 134f)
        t(x, extras(x, true), 14f, 190f, 11f, x.sub, maxW = 142f)
        days(x).forEachIndexed { i, d ->
            val cx = 33f + i * 52f
            t(x, dn(d), cx, 210f, 10.5f, x.sub, 'c', 46f)
            icon(x, d.code, true, cx, 223f, 16f)
            t(x, "${d.max}°/${d.min}°", cx, 243f, 11f, a = 'c', maxW = 48f)
        }
    }

    /** 8 · Şerit (4x1): logo, konum-tarih, dikey çift çizgi, saat, ikon-derece. */
    private fun serit(x: X) {
        logo(x, 30f, 45f, 13f)
        t(x, loc(x), 52f, 40f, 13f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 84f, bold = true)
        t(x, date(x, "EEE, d MMM"), 52f, 60f, 11f, x.sub, maxW = 84f)
        bar(x, 142f, 22f, 144f, 68f, YEL); bar(x, 147f, 22f, 149f, 68f, RED)
        clock(x, 158f, 62f, 44f, 96f, 22f)
        val dr = nowIcon(x, 274f, 40f, 34f)
        val tx = max(296f, 274f + dr / 2 + 4f)
        temp(x, tx, 54f, 26f, 344f - tx, 30f)
        t(x, rain(x)?.let { "%$it" } ?: (feels(x)?.let { "His $it°" } ?: ""), tx, 72f, 10.5f, x.sub, maxW = 344f - tx)
    }

    /** 9 · Pano (4x4) */
    private fun pano(x: X) {
        t(x, loc(x), 22f, 42f, 18f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 260f, bold = true)
        t(x, date(x, "EEEE, d MMMM"), 22f, 64f, 13f, x.sub, maxW = 260f)
        logo(x, 328f, 44f, 16f)
        clock(x, 18f, 162f, 104f, 324f, 82f)
        bar(x, 22f, 182f, 230f, 185f, YEL); bar(x, 236f, 182f, 338f, 185f, RED)
        val dr = nowIcon(x, 58f, 236f, 66f)
        val tx = 58f + dr / 2 + 6f
        temp(x, tx, 258f, 58f, 214f - tx, 202f)
        t(x, feels(x)?.let { "Hissedilen $it°" } ?: "", 338f, 230f, 14f, x.sub, 'r', 120f)
        t(x, rain(x)?.let { "Yağış %$it" } ?: "", 338f, 254f, 14f, x.sub, 'r', 120f)
        days(x).forEachIndexed { i, d ->
            val x0 = 22f + i * 108f; val cx = x0 + 50f
            card(x, x0, 278f, x0 + 100f, 342f, 14f)
            t(x, dn(d), cx, 298f, 13f, a = 'c', maxW = 88f, bold = true)
            icon(x, d.code, true, cx, 314f, 22f)
            t(x, "${d.max}° / ${d.min}°", cx, 336f, 12f, x.sub, 'c', 88f)
        }
    }

    /** 10 · Akordiyon: üç katlı panel, her kat soldan sağa gölgelenir. */
    private fun akordiyon(x: X) {
        if (x.op > .02f) {
            val a = (255 * x.op).toInt()
            val base = x.th.bg
            fun shade(col: Int, f: Float) = Color.argb(a, (Color.red(col) * f).toInt().coerceIn(0, 255), (Color.green(col) * f).toInt().coerceIn(0, 255), (Color.blue(col) * f).toInt().coerceIn(0, 255))
            fun lift(col: Int, add: Int) = Color.rgb((Color.red(col) + add).coerceAtMost(255), (Color.green(col) + add).coerceAtMost(255), (Color.blue(col) + add).coerceAtMost(255))
            for ((x0, x1, top) in listOf(Triple(0f, 172f, 12), Triple(172f, 264f, 22), Triple(264f, 360f, 14))) {
                x.cv.drawRect(x.px(x0), 0f, x.px(x1), x.py(190f), Paint().apply {
                    shader = LinearGradient(x.px(x0), 0f, x.px(x1), 0f, shade(lift(base, top), 1f), shade(base, .9f), Shader.TileMode.CLAMP)
                })
            }
            x.cv.drawRect(x.px(171f), 0f, x.px(172f), x.py(190f), Paint().apply { color = Color.argb((a * .6f).toInt(), 0, 0, 0) })
            x.cv.drawRect(x.px(263f), 0f, x.px(264f), x.py(190f), Paint().apply { color = Color.argb((a * .6f).toInt(), 0, 0, 0) })
        }
        bar(x, 20f, 22f, 60f, 24f, YEL); bar(x, 64f, 22f, 84f, 24f, RED)
        t(x, loc(x), 20f, 50f, 14f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 140f, bold = true)
        clock(x, 18f, 118f, 50f, 142f, 68f)
        t(x, date(x, "EEEE, d MMMM"), 20f, 142f, 12.5f, x.sub, maxW = 140f)
        nowIcon(x, 218f, 52f, 46f)
        temp(x, 218f, 112f, 34f, 80f, 84f, 'c')
        t(x, feels(x)?.let { "His $it°" } ?: "", 218f, 136f, 11f, x.sub, 'c', 82f)
        t(x, rain(x)?.let { "Yağış %$it" } ?: "", 218f, 152f, 11f, x.sub, 'c', 82f)
        logo(x, 218f, 172f, 10f)
        days(x).forEachIndexed { i, d ->
            val y = 44f + i * 48f
            t(x, dn(d), 276f, y, 12f, maxW = 40f, bold = true)
            icon(x, d.code, true, 330f, y - 4f, 20f)
            t(x, "${d.max}° / ${d.min}°", 276f, y + 18f, 11.5f, x.sub, maxW = 74f)
        }
    }

    /** 11 · Katlanır: ortadan katlanmış kart (üst/alt yarı ayrı gölge), katta sarı-kırmızı uçlar, sağ üstte kıvrık köşe. */
    private fun katlanir(x: X) {
        val a = (255 * x.op).toInt()
        val bg = x.th.bg
        fun f(col: Int, k: Float) = Color.argb(a, (Color.red(col) * k).toInt().coerceIn(0, 255), (Color.green(col) * k).toInt().coerceIn(0, 255), (Color.blue(col) * k).toInt().coerceIn(0, 255))
        if (x.op > .02f) {
            x.cv.drawRect(0f, 0f, x.px(360f), x.py(104f), Paint().apply { shader = LinearGradient(0f, 0f, 0f, x.py(104f), f(bg, 1.12f), f(bg, .9f), Shader.TileMode.CLAMP) })
            x.cv.drawRect(0f, x.py(104f), x.px(360f), x.py(190f), Paint().apply { shader = LinearGradient(0f, x.py(104f), 0f, x.py(190f), f(bg, .76f), f(bg, 1f), Shader.TileMode.CLAMP) })
            x.cv.drawRect(0f, x.py(103f), x.px(360f), x.py(104.5f), Paint().apply { color = f(bg, .5f) })
        }
        bar(x, 0f, 101f, 14f, 107f, YEL); bar(x, 346f, 101f, 360f, 107f, RED)
        // kıvrık köşe: köşeyi boşalt, altını sarı göster
        x.cv.drawPath(Path().apply { moveTo(x.px(318f), 0f); lineTo(x.px(360f), 0f); lineTo(x.px(360f), x.py(42f)); close() },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        x.cv.drawPath(Path().apply { moveTo(x.px(318f), 0f); lineTo(x.px(360f), x.py(42f)); lineTo(x.px(318f), x.py(42f)); close() },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (x.lines) YEL else f(bg, 1.3f) })
        t(x, loc(x), 20f, 34f, 15f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 200f, bold = true)
        t(x, date(x, "EEEE, d MMMM"), 20f, 52f, 12f, x.sub, maxW = 200f)
        clock(x, 18f, 94f, 44f, 200f, 60f)
        logo(x, 282f, 62f, 16f)
        val dr = nowIcon(x, 40f, 146f, 40f)
        val tx = 40f + dr / 2 + 4f
        temp(x, tx, 158f, 30f, 118f - tx, 128f)
        t(x, feels(x)?.let { "Hissedilen $it°" } ?: "", 124f, 140f, 11.5f, x.sub, maxW = 94f)
        t(x, rain(x)?.let { "Yağış %$it" } ?: "", 124f, if (feels(x) != null) 160f else 140f, 11.5f, x.sub, maxW = 94f)
        days(x).forEachIndexed { i, d ->
            val cx = 248f + i * 38f
            t(x, dn(d), cx, 130f, 10.5f, x.sub, 'c', 34f)
            icon(x, d.code, true, cx, 146f, 16f)
            t(x, "${d.max}°", cx, 170f, 12f, a = 'c', maxW = 34f)
        }
    }

    /** 12 · Hap: her bilgi kendi hapında; logo sarı-kırmızı halkalı dairede. */
    private fun hap(x: X) {
        card(x, 12f, 12f, 254f, 96f, 42f)
        bar(x, 30f, 36f, 33f, 72f, YEL)
        t(x, loc(x), 44f, 38f, 12.5f, Ink.get(x.c, Ink.LOC) ?: x.sub, maxW = 196f, bold = true)
        clock(x, 42f, 84f, 42f, 200f, 44f)
        if (x.op > .02f) x.cv.drawCircle(x.px(306f), x.py(54f), 42f * x.u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = x.th.card; alpha = (255 * x.op).toInt() })
        if (x.lines) {
            val r = RectF(x.px(306f) - 40f * x.u, x.py(54f) - 40f * x.u, x.px(306f) + 40f * x.u, x.py(54f) + 40f * x.u)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * x.u }
            x.cv.drawArc(r, 90f, 180f, false, p.apply { color = YEL }); x.cv.drawArc(r, 270f, 180f, false, Paint(p).apply { color = RED })
        }
        logo(x, 306f, 54f, 28f)
        card(x, 12f, 104f, 112f, 140f, 18f)
        val dr = nowIcon(x, 34f, 122f, 26f)
        val tx = 34f + dr / 2 + 4f
        temp(x, tx, 130f, 22f, 106f - tx, 110f)
        card(x, 118f, 104f, 232f, 140f, 18f)
        t(x, date(x, "EEE, d MMM"), 175f, 127f, 12f, a = 'c', maxW = 100f)
        card(x, 238f, 104f, 348f, 140f, 18f)
        t(x, extras(x, true, " · ").replace("Yağış ", ""), 293f, 127f, 12f, x.sub, 'c', 96f)
        days(x).forEachIndexed { i, d ->
            val x0 = 12f + i * 114f
            card(x, x0, 148f, x0 + 108f, 180f, 16f)
            t(x, dn(d), x0 + 14f, 169f, 11.5f, maxW = 28f, bold = true)
            icon(x, d.code, true, x0 + 50f, 164f, 16f)
            t(x, "${d.max}°/${d.min}°", x0 + 96f, 169f, 11f, x.sub, 'r', 38f)
        }
    }

    /** 13 · İnce uzun (4x1 ince): tek satır, altta ince çift renkli çizgi. */
    private fun ince(x: X) {
        logo(x, 24f, 30f, 11f)
        t(x, loc(x), 42f, 27f, 12f, Ink.get(x.c, Ink.LOC) ?: x.ink, maxW = 86f, bold = true)
        t(x, date(x, "EEE, d MMM"), 42f, 44f, 10.5f, x.sub, maxW = 86f)
        clock(x, 142f, 43f, 34f, 90f, 14f)
        val dr = nowIcon(x, 252f, 30f, 28f)
        val tx = max(270f, 252f + dr / 2 + 3f)
        temp(x, tx, 40f, 22f, 306f - tx, 18f)
        t(x, feels(x)?.let { "His $it°" } ?: "", 344f, 27f, 10.5f, x.sub, 'r', 36f)
        t(x, rain(x)?.let { "%$it" } ?: "", 344f, 44f, 10.5f, x.sub, 'r', 36f)
        bar(x, 20f, 55f, 200f, 56.5f, YEL); bar(x, 204f, 55f, 340f, 56.5f, RED)
    }
}
