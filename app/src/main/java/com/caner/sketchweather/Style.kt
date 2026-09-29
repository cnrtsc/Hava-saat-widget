package com.caner.sketchweather

import android.content.Context
import android.graphics.Typeface

object Style {
    val FONT_NAMES = arrayOf(
        "Sistem", "Outfit", "Manrope", "Poppins", "Bebas Neue",
        "Space Grotesk", "DM Serif", "Josefin Sans", "Urbanist"
    )
    private val CLOCK_FONTS = intArrayOf(
        0, R.font.f_outfit_c, R.font.f_manrope_c, R.font.f_poppins_c, R.font.f_bebas_c,
        R.font.f_grotesk_c, R.font.f_serif_c, R.font.f_josefin_c, R.font.f_urbanist_c
    )
    val WIDE_NAMES = arrayOf(
        "Klasik  (saat solda, hava sağda, 3 gün)",
        "Merkez  (konum, ikon, saat ve sıcaklık ortada)",
        "Büyük saat  (dev saat, altta hava şeridi)",
        "Hava + saatlik  (büyük hava, 5 saatlik tahmin)"
    )
    val BG_NAMES = arrayOf("Koyu cam", "Açık", "Şeffaf")
    val BGS = intArrayOf(R.drawable.bg_dark, R.drawable.bg_light, R.drawable.bg_none)
    val ICON_SET_NAMES = arrayOf("Yumuşak", "Parlak 3D", "Düz ikonik", "Gerçekçi")
    val ICONS = arrayOf(
        intArrayOf(R.drawable.w_sun, R.drawable.w_moon, R.drawable.w_partly, R.drawable.w_partly_night, R.drawable.w_cloud,
            R.drawable.w_fog, R.drawable.w_drizzle, R.drawable.w_rain, R.drawable.w_snow, R.drawable.w_storm),
        intArrayOf(R.drawable.g_sun, R.drawable.g_moon, R.drawable.g_partly, R.drawable.g_partly_night, R.drawable.g_cloud,
            R.drawable.g_fog, R.drawable.g_drizzle, R.drawable.g_rain, R.drawable.g_snow, R.drawable.g_storm),
        intArrayOf(R.drawable.f_sun, R.drawable.f_moon, R.drawable.f_partly, R.drawable.f_partly_night, R.drawable.f_cloud,
            R.drawable.f_fog, R.drawable.f_drizzle, R.drawable.f_rain, R.drawable.f_snow, R.drawable.f_storm),
        intArrayOf(R.drawable.r_sun, R.drawable.r_moon, R.drawable.r_partly, R.drawable.r_partly_night, R.drawable.r_cloud,
            R.drawable.r_fog, R.drawable.r_drizzle, R.drawable.r_rain, R.drawable.r_snow, R.drawable.r_storm)
    )

    fun clockTypeface(c: Context, i: Int): Typeface =
        if (CLOCK_FONTS[i] == 0) Typeface.create("sans-serif-light", Typeface.NORMAL)
        else c.resources.getFont(CLOCK_FONTS[i])

    private fun get(c: Context, key: String, def: Int, size: Int): Int =
        WeatherRepo.prefs(c).getInt(key, def).coerceIn(0, size - 1)

    fun font(c: Context) = get(c, "font", 1, FONT_NAMES.size)
    fun bg(c: Context) = get(c, "bg", 0, BGS.size)
    fun wide(c: Context) = get(c, "wide", 0, WIDE_NAMES.size)
    fun iconSet(c: Context) = get(c, "icons", 3, ICONS.size)
    fun icon(c: Context, code: Int, day: Boolean): Int = ICONS[iconSet(c)][WeatherRepo.iconIndex(code, day)]
}
