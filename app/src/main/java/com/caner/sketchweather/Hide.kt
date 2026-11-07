package com.caner.sketchweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Kullanıcının beğenmeyip gizlediği tasarımlar, ikon setleri ve yazı tipleri. */
object Hide {
    const val DESIGN = "designs"; const val ICON = "icons"; const val FONT = "fonts"
    private fun key(kind: String) = "hide_$kind"
    fun all(c: Context, kind: String): Set<String> = WeatherRepo.prefs(c).getStringSet(key(kind), emptySet()) ?: emptySet()
    fun has(c: Context, kind: String, k: String) = all(c, kind).contains(k)
    fun toggle(c: Context, kind: String, k: String) {
        val s = all(c, kind).toMutableSet(); if (!s.add(k)) s.remove(k)
        WeatherRepo.prefs(c).edit().putStringSet(key(kind), s).apply()
        if (kind == DESIGN) syncComponents(c)
    }
    fun showHidden(c: Context) = WeatherRepo.prefs(c).getBoolean("hide_show", false)
    fun setShowHidden(c: Context, v: Boolean) = WeatherRepo.prefs(c).edit().putBoolean("hide_show", v).apply()

    fun designKey(e: Entry) = e.cls.simpleName

    /**
     * Gizlenen tasarımları ana ekranın widget listesinden de kaldırır.
     * Ekranda kullanılan bir widget'ın bileşeni kapatılmaz (yoksa ekrandaki widget silinirdi).
     */
    fun syncComponents(c: Context) {
        val pm = c.packageManager; val mgr = AppWidgetManager.getInstance(c)
        val hidden = all(c, DESIGN)
        for (e in Registry.ALL) {
            val cn = ComponentName(c, e.cls)
            val inUse = try { mgr.getAppWidgetIds(cn).isNotEmpty() } catch (x: Exception) { true }
            val want = if (hidden.contains(designKey(e)) && !inUse) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            try { if (pm.getComponentEnabledSetting(cn) != want) pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP) } catch (x: Exception) { }
        }
    }
}
