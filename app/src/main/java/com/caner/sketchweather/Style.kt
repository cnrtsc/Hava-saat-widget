package com.caner.sketchweather

import android.content.Context
import android.graphics.Typeface

object Style {
    val FONT_NAMES = arrayOf(
        "Sistem", "Outfit", "Manrope", "Poppins", "Bebas Neue",
        "Space Grotesk", "DM Serif", "Josefin Sans", "Urbanist", "El yazısı",
        "Çift çizgi", "Neon çizgi", "Kabartma", "Mono", "Yuvarlak", "Inter"
    )
    private val CLOCK_FONTS = intArrayOf(
        0, R.font.f_outfit_c, R.font.f_manrope_c, R.font.f_poppins_c, R.font.f_bebas_c,
        R.font.f_grotesk_c, R.font.f_serif_c, R.font.f_josefin_c, R.font.f_urbanist_c, R.font.f_hand_c,
        R.font.f_train_c, R.font.f_monoton_c, R.font.f_rampart_c, R.font.f_mono_c, R.font.f_quick_c, R.font.f_inter_c
    )
    private val TEXT_FONTS = intArrayOf(
        0, R.font.f_outfit_t, R.font.f_manrope_t, R.font.f_poppins_t, R.font.f_bebas_t,
        R.font.f_grotesk_t, R.font.f_serif_t, R.font.f_josefin_t, R.font.f_urbanist_t, R.font.f_hand_t,
        R.font.f_outfit_t, R.font.f_outfit_t, R.font.f_outfit_t, R.font.f_mono_t, R.font.f_quick_t, R.font.f_inter_t
    )
    val BG_NAMES = arrayOf("Koyu", "Açık", "Şeffaf", "Renkli (palet)")
    val BGS = intArrayOf(R.drawable.bg_dark, R.drawable.bg_light, R.drawable.bg_none, R.drawable.shape_round)
    val SHAPE_NAMES = arrayOf("Klasik", "Rozet", "Geometrik", "Minimal", "Gerçekçi")
    val ICON_STYLE_NAMES = arrayOf("Düz", "Origami", "Eskiz", "Suluboya", "Çizgi", "Kil 3D", "Gradyan", "Neon")
    fun iconShape(c: Context) = Cfg.int(c, "iconShape", 0).coerceIn(0, SHAPE_NAMES.size - 1)
    fun iconStyle(c: Context) = Cfg.int(c, "iconStyle", 6).coerceIn(0, ICON_STYLE_NAMES.size - 1)
    fun row(shape: Int, style: Int): IntArray = if (shape >= 4) IconTable.ROWS[32] else IconTable.ROWS[shape * 8 + style]
    fun iconOf(shape: Int, style: Int, code: Int, day: Boolean): Int = row(shape, style)[WeatherRepo.iconIndex(code, day)]
    /** Widget'a özel ikon seçimi yoksa tasarımın önerdiği şekil/stil kullanılır. */
    fun iconPref(c: Context, defShape: Int, defStyle: Int, code: Int, day: Boolean): Int {
        val id = Cfg.current(); val p = WeatherRepo.prefs(c)
        val custom = id != null && (p.contains(Cfg.wkey(id, "iconShape")) || p.contains(Cfg.wkey(id, "iconStyle")))
        return if (custom) icon(c, code, day) else iconOf(defShape, defStyle, code, day)
    }

    fun clockTypeface(c: Context, i: Int): Typeface =
        if (CLOCK_FONTS[i] == 0) Typeface.create("sans-serif-light", Typeface.NORMAL)
        else c.resources.getFont(CLOCK_FONTS[i])

    fun textFontIdx(c: Context) = Cfg.int(c, "textFont", -1).coerceIn(-1, FONT_NAMES.size - 1)
    /** Diğer yazılar için ayrı yazı tipi seçildiyse o, yoksa saat yazı tipinin eşi. */
    fun textTypeface(c: Context, i0: Int): Typeface {
        val t = textFontIdx(c)
        val i = if (t >= 0) t else i0
        return if (TEXT_FONTS[i] == 0) Typeface.create("sans-serif", Typeface.NORMAL) else c.resources.getFont(TEXT_FONTS[i])
    }

    private fun get(c: Context, key: String, def: Int, size: Int): Int =
        Cfg.int(c, key, def).coerceIn(0, size - 1)

    fun font(c: Context) = get(c, "font", 1, FONT_NAMES.size)
    fun bg(c: Context) = get(c, "bg", 2, BGS.size)
    val TEXT_COLOR_NAMES = arrayOf("Otomatik", "Beyaz", "Siyah", "Krem", "Palet rengi", "Açık mavi", "Turuncu")
    fun textColorIdx(c: Context) = get(c, "textColor", 0, TEXT_COLOR_NAMES.size)
    /** 0 = otomatik (null döner) */
    fun textColor(c: Context): Int? = when (textColorIdx(c)) {
        1 -> 0xFFFFFFFF.toInt(); 2 -> 0xFF1A1C22.toInt(); 3 -> 0xFFF3E6CF.toInt()
        4 -> Palette.get(c).accentLight; 5 -> 0xFFA9D4FF.toInt(); 6 -> 0xFFFFB24A.toInt()
        else -> null
    }
    val FX_NAMES = arrayOf("Dolu", "İnce kontur", "Kalın kontur", "Gölgeli", "Neon parlama", "Yumuşak ışık", "Kesik çizgi", "Kalın dolu", "Yarı saydam")
    fun clockFx(c: Context) = get(c, "clockFx", 0, FX_NAMES.size)
    fun opacity(c: Context) = Cfg.int(c, "opacity", 100).coerceIn(0, 100)
    val ANIM_NAMES = arrayOf("Kapalı", "Her 30 saniyede", "Her 10 saniyede")
    fun anim(c: Context) = 0
    fun animInterval(c: Context): Int = when (anim(c)) { 1 -> 30_000; 2 -> 10_000; else -> 86_400_000 }
    fun icon(c: Context, code: Int, day: Boolean): Int = iconOf(iconShape(c), iconStyle(c), code, day)
    fun handTypeface(c: Context): Typeface = c.resources.getFont(R.font.f_hand_c)

    val HOME_NAMES = arrayOf("İstanbul", "Londra", "Hamburg / Berlin", "Dubai", "New York", "Tokyo")
    private val HOME_TZ = arrayOf("Europe/Istanbul", "Europe/London", "Europe/Berlin", "Asia/Dubai", "America/New_York", "Asia/Tokyo")
    fun home(c: Context) = get(c, "home", 0, HOME_NAMES.size)
    fun homeName(c: Context): String = HOME_NAMES[home(c)]
    fun homeTz(c: Context): String = HOME_TZ[home(c)]
}
