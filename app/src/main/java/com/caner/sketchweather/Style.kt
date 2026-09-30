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
    val ICON_SET_NAMES = arrayOf("Yumuşak", "Parlak 3D", "Düz ikonik", "Gerçekçi", "Çizgi", "Neon", "Pastel", "Eskiz", "Suluboya", "Retro piksel", "Gradyan düz", "Kil 3D")
    val ICONS: Array<IntArray> = arrayOf(
        intArrayOf(R.drawable.w_sun, R.drawable.w_moon, R.drawable.w_partly, R.drawable.w_partly_night, R.drawable.w_cloud,
            R.drawable.w_fog, R.drawable.w_drizzle, R.drawable.w_rain, R.drawable.w_snow, R.drawable.w_storm),
        intArrayOf(R.drawable.g_sun, R.drawable.g_moon, R.drawable.g_partly, R.drawable.g_partly_night, R.drawable.g_cloud,
            R.drawable.g_fog, R.drawable.g_drizzle, R.drawable.g_rain, R.drawable.g_snow, R.drawable.g_storm),
        intArrayOf(R.drawable.f_sun, R.drawable.f_moon, R.drawable.f_partly, R.drawable.f_partly_night, R.drawable.f_cloud,
            R.drawable.f_fog, R.drawable.f_drizzle, R.drawable.f_rain, R.drawable.f_snow, R.drawable.f_storm),
        intArrayOf(R.drawable.r_sun, R.drawable.r_moon, R.drawable.r_partly, R.drawable.r_partly_night, R.drawable.r_cloud,
            R.drawable.r_fog, R.drawable.r_drizzle, R.drawable.r_rain, R.drawable.r_snow, R.drawable.r_storm),
        intArrayOf(R.drawable.l_sun, R.drawable.l_moon, R.drawable.l_partly, R.drawable.l_partly_night, R.drawable.l_cloud,
            R.drawable.l_fog, R.drawable.l_drizzle, R.drawable.l_rain, R.drawable.l_snow, R.drawable.l_storm),
        intArrayOf(R.drawable.n_sun, R.drawable.n_moon, R.drawable.n_partly, R.drawable.n_partly_night, R.drawable.n_cloud,
            R.drawable.n_fog, R.drawable.n_drizzle, R.drawable.n_rain, R.drawable.n_snow, R.drawable.n_storm),
        intArrayOf(R.drawable.p_sun, R.drawable.p_moon, R.drawable.p_partly, R.drawable.p_partly_night, R.drawable.p_cloud,
            R.drawable.p_fog, R.drawable.p_drizzle, R.drawable.p_rain, R.drawable.p_snow, R.drawable.p_storm),
        intArrayOf(R.drawable.s_sun, R.drawable.s_moon, R.drawable.s_partly, R.drawable.s_partly_night, R.drawable.s_cloud, R.drawable.s_fog, R.drawable.s_drizzle, R.drawable.s_rain, R.drawable.s_snow, R.drawable.s_storm),
        intArrayOf(R.drawable.c_sun, R.drawable.c_moon, R.drawable.c_partly, R.drawable.c_partly_night, R.drawable.c_cloud, R.drawable.c_fog, R.drawable.c_drizzle, R.drawable.c_rain, R.drawable.c_snow, R.drawable.c_storm),
        intArrayOf(R.drawable.x_sun, R.drawable.x_moon, R.drawable.x_partly, R.drawable.x_partly_night, R.drawable.x_cloud, R.drawable.x_fog, R.drawable.x_drizzle, R.drawable.x_rain, R.drawable.x_snow, R.drawable.x_storm),
        intArrayOf(R.drawable.y_sun, R.drawable.y_moon, R.drawable.y_partly, R.drawable.y_partly_night, R.drawable.y_cloud, R.drawable.y_fog, R.drawable.y_drizzle, R.drawable.y_rain, R.drawable.y_snow, R.drawable.y_storm),
        intArrayOf(R.drawable.k_sun, R.drawable.k_moon, R.drawable.k_partly, R.drawable.k_partly_night, R.drawable.k_cloud, R.drawable.k_fog, R.drawable.k_drizzle, R.drawable.k_rain, R.drawable.k_snow, R.drawable.k_storm)
    )

    fun clockTypeface(c: Context, i: Int): Typeface =
        if (CLOCK_FONTS[i] == 0) Typeface.create("sans-serif-light", Typeface.NORMAL)
        else c.resources.getFont(CLOCK_FONTS[i])

    fun textTypeface(c: Context, i: Int): Typeface =
        if (TEXT_FONTS[i] == 0) Typeface.create("sans-serif", Typeface.NORMAL)
        else c.resources.getFont(TEXT_FONTS[i])

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
    val FX_NAMES = arrayOf("Dolu", "Kontur (çift çizgi)", "Gölgeli")
    fun clockFx(c: Context) = get(c, "clockFx", 0, FX_NAMES.size)
    fun opacity(c: Context) = Cfg.int(c, "opacity", 100).coerceIn(0, 100)
    val ANIM_NAMES = arrayOf("Kapalı", "Her 30 saniyede", "Her 10 saniyede")
    fun anim(c: Context) = get(c, "anim", 1, ANIM_NAMES.size)
    fun animInterval(c: Context): Int = when (anim(c)) { 1 -> 30_000; 2 -> 10_000; else -> 86_400_000 }
    fun iconSet(c: Context) = get(c, "icons", 3, ICONS.size)
    fun icon(c: Context, code: Int, day: Boolean): Int = ICONS[iconSet(c)][WeatherRepo.iconIndex(code, day)]
    fun iconFrom(set: Int, code: Int, day: Boolean): Int = ICONS[set][WeatherRepo.iconIndex(code, day)]
    fun handTypeface(c: Context): Typeface = c.resources.getFont(R.font.f_hand_c)

    val HOME_NAMES = arrayOf("İstanbul", "Londra", "Hamburg / Berlin", "Dubai", "New York", "Tokyo")
    private val HOME_TZ = arrayOf("Europe/Istanbul", "Europe/London", "Europe/Berlin", "Asia/Dubai", "America/New_York", "Asia/Tokyo")
    fun home(c: Context) = get(c, "home", 0, HOME_NAMES.size)
    fun homeName(c: Context): String = HOME_NAMES[home(c)]
    fun homeTz(c: Context): String = HOME_TZ[home(c)]
}
