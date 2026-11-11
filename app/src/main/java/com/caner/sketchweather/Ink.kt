package com.caner.sketchweather

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * Öğe başına özel renkler ve saat yazımı.
 * Renk anahtarları int ARGB tutar; 0 = "otomatik" (tasarımın kendi rengi).
 */
object Ink {
    const val CLOCK = "cClock"; const val MIN = "cMin"; const val TEXT = "cText"; const val SUB = "cSub"
    const val TEMP = "cTemp"; const val LOC = "cLoc"; const val ACC = "cAcc"; const val CLOCK2 = "cClock2"
    val KEYS = arrayOf(CLOCK, MIN, TEXT, SUB, TEMP, LOC, ACC)
    val KEY_NAMES = arrayOf("Saat", "Dakika", "Yazılar", "İkincil yazılar (tarih vb.)", "Sıcaklık", "Konum", "Vurgu")
    val ALL_KEYS = listOf(CLOCK, MIN, TEXT, SUB, TEMP, LOC, ACC, CLOCK2, "clockGrad", "clockStyle", "clockWeight", "clockSpacing",
        "shadowK", "autoContrast", "todColor", "sTemp", "clockTex", "textTex", "texScale", "clockSep", "noLead", "iconFx")

    val STYLE_NAMES = arrayOf("Normal", "Dakika ayrı renk", "İnce ayraç", "Yazıyla")
    val WEIGHT_NAMES = arrayOf("Tasarımdaki", "Kalın", "Daha kalın", "En kalın")
    val SHADOW_NAMES = arrayOf("Tasarımdaki gibi", "Yok", "Hafif", "Orta", "Güçlü")

    /** O an çizilen widget'ın zemin rengi (biliniyorsa); okunurluk düzeltmesi için. */
    private val bgTL = ThreadLocal<Int?>()
    fun setBg(col: Int?) = bgTL.set(col)

    private fun raw(c: Context, key: String): Int? = Cfg.int(c, key, 0).takeIf { it != 0 }

    /** Kullanıcının seçtiği renk (okunurluk düzeltmesiyle) ya da null. */
    fun get(c: Context, key: String): Int? {
        val v = raw(c, key) ?: (if (key == CLOCK) tod(c) else null) ?: return null
        // Otomatik okunurluk sadece tasarımın kendi zemini belirginken: zemin saydamsa kullanıcının seçtiği renk aynen kalır.
        val own = bgTL.get() ?: return v
        return if (autoContrast(c) && Style.opacity(c) >= 50) legible(v, own) else v
    }
    fun or(c: Context, key: String, def: Int): Int = get(c, key) ?: def

    fun grad(c: Context) = Cfg.bool(c, "clockGrad", false)
    fun style(c: Context) = Cfg.int(c, "clockStyle", 0).coerceIn(0, STYLE_NAMES.size - 1)
    fun weight(c: Context) = Cfg.int(c, "clockWeight", 0).coerceIn(0, WEIGHT_NAMES.size - 1)
    fun spacing(c: Context) = Cfg.int(c, "clockSpacing", 0).coerceIn(-10, 40)
    fun shadow(c: Context) = Cfg.int(c, "shadowK", 0).coerceIn(0, SHADOW_NAMES.size - 1)
    fun autoContrast(c: Context) = Cfg.bool(c, "autoContrast", true)
    fun todOn(c: Context) = Cfg.bool(c, "todColor", false)
    val SEP_NAMES = arrayOf(":", "·", "•", "boşluk", "|", ".")
    private val SEPS = arrayOf(":", "·", "•", " ", "|", ".")
    fun sep(c: Context) = Cfg.int(c, "clockSep", 0).coerceIn(0, SEPS.size - 1)
    fun noLead(c: Context) = Cfg.bool(c, "noLead", false)
    fun tempScale(c: Context) = Cfg.int(c, "sTemp", 100).coerceIn(60, 150) / 100f

    /** Saate göre renk: sabah şeftali, öğle güneş sarısı, akşam mercan, gece mavi. */
    fun tod(c: Context): Int? {
        if (!todOn(c)) return null
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 6..10 -> Color.rgb(255, 178, 128)
            in 11..16 -> Color.rgb(255, 214, 92)
            in 17..20 -> Color.rgb(244, 120, 132)
            else -> Color.rgb(146, 174, 255)
        }
    }

    /* ---------- okunurluk ---------- */
    private fun lin(v: Int): Double { val s = v / 255.0; return if (s <= .03928) s / 12.92 else Math.pow((s + .055) / 1.055, 2.4) }
    fun luminance(col: Int) = .2126 * lin(Color.red(col)) + .7152 * lin(Color.green(col)) + .0722 * lin(Color.blue(col))
    fun contrast(a: Int, b: Int): Double { val l1 = luminance(a); val l2 = luminance(b); return (max(l1, l2) + .05) / (min(l1, l2) + .05) }

    private var wallCache: Int? = null
    private var wallAt = 0L
    /** Zemin bilinmiyorsa duvar kağıdının baskın rengi. */
    fun bg(c: Context): Int? {
        bgTL.get()?.let { return it }
        val now = System.currentTimeMillis()
        if (now - wallAt > 60_000) {
            wallAt = now
            wallCache = try { WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() } catch (e: Exception) { null }
        }
        return wallCache
    }

    /** Renk zeminle karışıyorsa açıklığını değiştirerek en az 3:1 kontrasta çıkarır; tonu korur. */
    fun legible(fg: Int, bg: Int?): Int {
        if (bg == null || Color.alpha(bg) < 60) return fg
        if (contrast(fg, bg) >= 3.0) return fg
        val hsv = FloatArray(3); Color.colorToHSV(fg, hsv)
        val darken = luminance(bg) > .4
        var col = fg
        for (i in 1..20) {
            if (darken) { hsv[2] = (hsv[2] - .05f).coerceAtLeast(0f); hsv[1] = (hsv[1] + .02f).coerceAtMost(1f) }
            else { hsv[2] = (hsv[2] + .05f).coerceAtMost(1f); hsv[1] = (hsv[1] - .05f).coerceAtLeast(0f) }
            col = Color.HSVToColor(Color.alpha(fg), hsv)
            if (contrast(col, bg) >= 3.0) break
        }
        return col
    }

    /* ---------- gölge ---------- */
    private val shTL = ThreadLocal<Int>()
    /** Her widget çiziminden önce çağrılır. */
    fun begin(c: Context) { bgTL.set(null); shTL.set(shadow(c)); Tex.begin(c) }
    /** Kullanıcı gölge düzeyi seçtiyse boyaya uygular (tasarımın gölgesini ezer). */
    fun shadowOverride(p: Paint) {
        val k = shTL.get() ?: 0; if (k == 0) return
        if (k == 1) { p.clearShadowLayer(); return }
        val a = intArrayOf(0, 0, 90, 150, 220)[k]; val r = floatArrayOf(0f, 0f, 4f, 7f, 10f)[k]
        p.setShadowLayer(r, 0f, r * .4f, Color.argb(a, 0, 0, 0))
    }

    /* ---------- saat boyası ---------- */
    fun applyClock(c: Context, p: Paint, size: Float) {
        val wgt = weight(c)
        if (wgt > 0 && p.style == Paint.Style.FILL) {
            p.style = Paint.Style.FILL_AND_STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = size * .014f * wgt
        }
        val sp = spacing(c)
        if (sp != 0) p.letterSpacing = sp / 100f
    }

    /* ---------- saat yazımı ---------- */
    private val ONES = arrayOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val TENS = arrayOf("", "on", "yirmi", "otuz", "kırk", "elli")
    private fun num(n: Int): String = if (n < 10) ONES[n] else (TENS[n / 10] + if (n % 10 > 0) " " + ONES[n % 10] else "")
    fun words(hhmm: String): String {
        if (!hhmm.contains(':')) return hhmm
        val h = hhmm.substringBefore(':').trim().toIntOrNull() ?: return hhmm
        val m = hhmm.substringAfter(':').take(2).toIntOrNull() ?: 0
        val h12 = if (h % 12 == 0) 12 else h % 12
        val hs = if (h12 == 12) "on iki" else num(h12)
        return if (m == 0) hs else "$hs ${num(m)}"
    }

    /**
     * "HH:mm" gibi bir saati çizer: iki renk dakika, ince ayraç, yazıyla ve renk geçişi seçeneklerini uygular.
     * Hizalama paint.textAlign'a göre yapılır.
     */
    private class Lay(val p: Paint, val parts: List<Pair<String, Int>>, val gap: Float, val total: Float, val minCol: Int?, val st: Int)

    private fun lay(c: Context, s0: String, p0: Paint): Lay {
        val st = style(c)
        val s = if (noLead(c) && s0.length >= 4 && s0[0] == '0' && s0[2] == ':') s0.substring(1) else s0
        val sp = SEPS[sep(c)]
        val p = Paint(p0)
        val minCol = get(c, MIN) ?: if (st == 1) (get(c, ACC) ?: Palette.get(c).accentLight) else null
        val split = s.contains(':') && (st == 1 || st == 2 || minCol != null)
        val text = if (st == 3) words(s) else s
        val full = p.measureText(s)
        if (st == 3) { val w = p.measureText(text); if (w > full * 1.6f) p.textSize *= full * 1.6f / w }
        val parts: List<Pair<String, Int>> = if (split && st != 3) {
            listOf(s.substringBefore(':') to 0, (if (st == 2) "" else sp) to 1, s.substringAfter(':') to 2)
        } else listOf((if (st == 3) text else text.replace(":", sp)) to 0)
        val gap = if (st == 2 && split) p.textSize * .22f else 0f
        val total = parts.sumOf { p.measureText(it.first).toDouble() }.toFloat() + gap
        return Lay(p, parts, gap, total, minCol, st)
    }

    /** Saat yazısının seçilen stille kaplayacağı genişlik. */
    fun measure(c: Context, s: String, p: Paint): Float = lay(c, s, p).total

    /**
     * "HH:mm" gibi bir saati çizer: iki renk dakika, ince ayraç, yazıyla ve renk geçişi seçeneklerini uygular.
     * Hizalama paint.textAlign'a göre yapılır.
     */
    fun time(c: Context, cv: Canvas, s: String, x: Float, y: Float, p0: Paint) {
        val L = lay(c, s, p0); val p = L.p
        var left = when (p.textAlign) { Paint.Align.CENTER -> x - L.total / 2; Paint.Align.RIGHT -> x - L.total; else -> x }
        p.textAlign = Paint.Align.LEFT
        if (grad(c)) {
            val c1 = p.color; val c2 = get(c, CLOCK2) ?: (get(c, ACC) ?: Palette.get(c).accentLight)
            p.shader = LinearGradient(0f, y - p.textSize * .78f, 0f, y, c1, c2, Shader.TileMode.CLAMP)
        }
        Tex.clock(true)
        try { for ((t, kind) in L.parts) {
            if (kind == 1 && L.st == 2) {
                val lp = Paint(p).apply { shader = null; style = Paint.Style.STROKE; strokeWidth = p.textSize * .03f; alpha = (Color.alpha(p.color) * .55f).toInt() }
                val cx = left + L.gap / 2
                cv.drawLine(cx, y - p.textSize * .68f, cx, y + p.textSize * .02f, lp)
                left += L.gap; continue
            }
            val q = Paint(p)
            if (kind == 2 && L.minCol != null) { q.shader = null; q.color = L.minCol }
            if (kind == 1) q.alpha = (Color.alpha(p.color) * .6f).toInt()
            cv.drawText(t, left, y, q)
            left += q.measureText(t)
        } } finally { Tex.clock(false) }
    }

    /* ---------- hazır paletler ---------- */
    val PRESET_NAMES = arrayOf("Kum", "Gece", "Neon", "Pastel", "Mürekkep", "Orman", "Duvar kağıdından")
    // saat, dakika, yazı, ikincil, sıcaklık, konum, vurgu
    private val PRESETS = arrayOf(
        intArrayOf(0xFFF3E6CF.toInt(), 0xFFE0A86A.toInt(), 0xFFF3E6CF.toInt(), 0xFFD2C0A2.toInt(), 0xFFF3E6CF.toInt(), 0xFFE0A86A.toInt(), 0xFFE0A86A.toInt()),
        intArrayOf(0xFFDCE6FF.toInt(), 0xFF8FA8FF.toInt(), 0xFFDCE6FF.toInt(), 0xFFA4B0CC.toInt(), 0xFFFFFFFF.toInt(), 0xFF8FA8FF.toInt(), 0xFF8FA8FF.toInt()),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF4FD8.toInt(), 0xFFE8FFFB.toInt(), 0xFF7FF5E0.toInt(), 0xFF39F2C8.toInt(), 0xFFFF4FD8.toInt(), 0xFFFF4FD8.toInt()),
        intArrayOf(0xFFFFF4FA.toInt(), 0xFFFFB3C7.toInt(), 0xFFFFF4FA.toInt(), 0xFFD7C8F0.toInt(), 0xFFB9E4D4.toInt(), 0xFFFFB3C7.toInt(), 0xFFB9E4D4.toInt()),
        intArrayOf(0xFF1E2230.toInt(), 0xFFC4452E.toInt(), 0xFF1E2230.toInt(), 0xFF5D6170.toInt(), 0xFF1E2230.toInt(), 0xFFC4452E.toInt(), 0xFFC4452E.toInt()),
        intArrayOf(0xFFF1F5EA.toInt(), 0xFFB5D68A.toInt(), 0xFFF1F5EA.toInt(), 0xFFB8C6AE.toInt(), 0xFFF1F5EA.toInt(), 0xFFB5D68A.toInt(), 0xFFB5D68A.toInt())
    )

    /** Duvar kağıdından 6 renk: baskın renkler ve açık/koyu türevleri. */
    fun wallpaperSwatches(c: Context): IntArray {
        val base = try {
            val wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            listOfNotNull(wc?.primaryColor?.toArgb(), wc?.secondaryColor?.toArgb(), wc?.tertiaryColor?.toArgb())
        } catch (e: Exception) { emptyList() }
        if (base.isEmpty()) return intArrayOf()
        val out = ArrayList<Int>()
        for (b in base) {
            val hsv = FloatArray(3); Color.colorToHSV(b, hsv)
            out.add(Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * .5f).coerceAtMost(.35f), .97f)))
            out.add(Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 1.2f).coerceIn(.35f, .8f), .85f)))
        }
        return out.distinct().toIntArray()
    }

    fun presetColors(c: Context, i: Int): IntArray? {
        if (i < PRESETS.size) return PRESETS[i]
        val w = wallpaperSwatches(c); if (w.isEmpty()) return null
        val light = w[0]; val strong = w.getOrElse(1) { w[0] }; val other = w.getOrElse(3) { strong }
        val sub = Color.argb(215, Color.red(light), Color.green(light), Color.blue(light))
        return intArrayOf(light, strong, light, sub, light, other, strong)
    }

    /* ---------- son kullanılanlar ---------- */
    fun recent(c: Context): IntArray =
        WeatherRepo.prefs(c).getString("recentColors", "")!!.split(',').mapNotNull { it.toLongOrNull()?.toInt() }.toIntArray()
    fun pushRecent(c: Context, col: Int) {
        val l = (listOf(col) + recent(c).filter { it != col }).take(10)
        WeatherRepo.prefs(c).edit().putString("recentColors", l.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }).apply()
    }
}
