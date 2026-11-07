package com.caner.sketchweather

import android.content.Context
import android.graphics.Typeface

object Style {
    val FONT_NAMES = arrayOf(
        "Sistem", "Outfit", "Manrope", "Poppins", "Bebas Neue",
        "Space Grotesk", "DM Serif", "Josefin Sans", "Urbanist", "El yazısı",
        "Çift çizgi", "Neon çizgi", "Kabartma", "Mono", "Yuvarlak", "Inter",
        "Unbounded", "Syne", "Dar grotesk", "Fraunces", "Space Mono", "Righteous", "Archivo Black",
        "Nokta (Doto)", "Big Shoulders", "JetBrains Mono", "Climate Crisis", "Gloock",
        "Sora", "Lexend", "Jakarta", "DM Sans", "Anton", "Oswald", "Teko", "Orbitron", "Chakra Petch", "Barlow dar"
    )
    private val CLOCK_FONTS = intArrayOf(
        0, R.font.f_outfit_c, R.font.f_manrope_c, R.font.f_poppins_c, R.font.f_bebas_c,
        R.font.f_grotesk_c, R.font.f_serif_c, R.font.f_josefin_c, R.font.f_urbanist_c, R.font.f_hand_c,
        R.font.f_train_c, R.font.f_monoton_c, R.font.f_rampart_c, R.font.f_mono_c, R.font.f_quick_c, R.font.f_inter_c,
        R.font.f_unbounded_c, R.font.f_syne_c, R.font.f_bricolage_c, R.font.f_fraunces_c, R.font.f_spacemono_c, R.font.f_righteous_c, R.font.f_archivo_c,
        R.font.f_doto_c, R.font.f_shoulders_c, R.font.f_jetbrains_c, R.font.f_climate_c, R.font.f_gloock_c,
        R.font.f_sora_c, R.font.f_lexend_c, R.font.f_jakarta_c, R.font.f_dmsans_c, R.font.f_anton_c, R.font.f_oswald_c, R.font.f_teko_c, R.font.f_orbitron_c, R.font.f_chakra_c, R.font.f_barlow_c
    )
    private val TEXT_FONTS = intArrayOf(
        0, R.font.f_outfit_t, R.font.f_manrope_t, R.font.f_poppins_t, R.font.f_bebas_t,
        R.font.f_grotesk_t, R.font.f_serif_t, R.font.f_josefin_t, R.font.f_urbanist_t, R.font.f_hand_t,
        R.font.f_outfit_t, R.font.f_outfit_t, R.font.f_outfit_t, R.font.f_mono_t, R.font.f_quick_t, R.font.f_inter_t,
        R.font.f_unbounded_t, R.font.f_syne_t, R.font.f_bricolage_t, R.font.f_fraunces_t, R.font.f_spacemono_t, R.font.f_syne_t, R.font.f_bricolage_t,
        R.font.f_doto_t, R.font.f_shoulders_t, R.font.f_jetbrains_t, R.font.f_outfit_t, R.font.f_gloock_c,
        R.font.f_sora_t, R.font.f_lexend_t, R.font.f_jakarta_t, R.font.f_dmsans_t, R.font.f_oswald_t, R.font.f_oswald_t, R.font.f_teko_t, R.font.f_chakra_t, R.font.f_chakra_t, R.font.f_barlow_t
    )
    val BG_NAMES = arrayOf("Koyu", "Açık", "Şeffaf", "Renkli (palet)")
    val BGS = intArrayOf(R.drawable.bg_dark, R.drawable.bg_light, R.drawable.bg_none, R.drawable.shape_round)
    val SHAPE_NAMES = arrayOf("Klasik", "Geometrik", "Minimal", "Balon", "Blok", "Ufuk", "Nokta", "Bauhaus", "Retro 70'ler", "Çıkartma", "Organik", "Risograf", "Gerçekçi", "Pencere", "Manzara", "Soyut", "Yazı", "Nokta matris", "Damga", "Kil", "Yapı taşı", "Origami", "Fotogerçekçi", "Modern degrade", "Vintage", "Piksel", "Nesneler (şemsiye, kardan adam)")
    /** Eski kayıtlarda 7 = Gerçekçi idi; artık 12. Bir kez çevrilir. */
    fun migrate(c: Context) {
        val p = WeatherRepo.prefs(c)
        if (p.getInt("iconVer", 0) >= 2) return
        val e = p.edit()
        for ((k, v) in p.all) if (k.endsWith("iconShape") && v == 7) e.putInt(k, 12)
        e.putInt("iconVer", 2).apply()
    }
    /** Kendi stili olan şekiller (stil seçimi yok) */
    fun fixedStyle(shape: Int) = shape >= 7
    val ICON_STYLE_NAMES = arrayOf("Düz", "Origami", "Eskiz", "Suluboya", "Çizgi", "Kil 3D", "Gradyan", "Neon", "Kesik kağıt", "Pastel")
    fun iconShape(c: Context) = Cfg.int(c, "iconShape", 0).coerceIn(0, SHAPE_NAMES.size - 1)
    fun iconStyle(c: Context) = Cfg.int(c, "iconStyle", 6).coerceIn(0, ICON_STYLE_NAMES.size - 1)
    fun row(shape: Int, style: Int): IntArray = if (shape >= 7) IconTable.ROWS[(70 + shape - 7).coerceAtMost(IconTable.ROWS.size - 1)] else IconTable.ROWS[shape * 10 + style]
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
    fun textColor(c: Context): Int? = Ink.get(c, Ink.TEXT) ?: when (textColorIdx(c)) {
        1 -> 0xFFFFFFFF.toInt(); 2 -> 0xFF1A1C22.toInt(); 3 -> 0xFFF3E6CF.toInt()
        4 -> Palette.get(c).accentLight; 5 -> 0xFFA9D4FF.toInt(); 6 -> 0xFFFFB24A.toInt()
        else -> null
    }
    val FX_NAMES = arrayOf("Dolu", "İnce kontur", "Kalın kontur", "Gölgeli", "Neon parlama", "Yumuşak ışık", "Kesik çizgi", "Kalın dolu", "Yarı saydam")
    fun clockFx(c: Context) = get(c, "clockFx", 0, FX_NAMES.size)
    fun opacity(c: Context) = Cfg.int(c, "opacity", 100).coerceIn(0, 100)
    /** Widget içi boyut ayarları (%70–%140) */
    fun clockScale(c: Context) = Cfg.int(c, "sClock", 100).coerceIn(60, 150) / 100f
    fun textScale(c: Context) = Cfg.int(c, "sText", 100).coerceIn(60, 150) / 100f
    fun iconScale(c: Context) = Cfg.int(c, "sIcon", 100).coerceIn(60, 150) / 100f
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
