package com.caner.sketchweather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

abstract class BaseWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Widgets.ACTION_REFRESH,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val app = context.applicationContext
                Widgets.renderAll(app, WeatherRepo.cached(app))
                val pending = goAsync()
                Thread {
                    try {
                        Widgets.renderAll(app, WeatherRepo.fetch(app))
                    } catch (e: Exception) {
                        // ağ yoksa önbellekteki veri kalır
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            else -> super.onReceive(context, intent)
        }
    }
}

class WideWidget : BaseWidget()
class CompactWidget : BaseWidget()
class SquareWidget : BaseWidget()
class GlassWidget : BaseWidget()

object Widgets {
    const val ACTION_REFRESH = "com.caner.sketchweather.REFRESH"

    private val DAY_IDS = arrayOf(
        intArrayOf(R.id.d1_name, R.id.d1_icon, R.id.d1_temp),
        intArrayOf(R.id.d2_name, R.id.d2_icon, R.id.d2_temp),
        intArrayOf(R.id.d3_name, R.id.d3_icon, R.id.d3_temp)
    )
    private val HOUR_IDS = arrayOf(
        intArrayOf(R.id.h1_time, R.id.h1_icon, R.id.h1_temp),
        intArrayOf(R.id.h2_time, R.id.h2_icon, R.id.h2_temp),
        intArrayOf(R.id.h3_time, R.id.h3_icon, R.id.h3_temp),
        intArrayOf(R.id.h4_time, R.id.h4_icon, R.id.h4_temp),
        intArrayOf(R.id.h5_time, R.id.h5_icon, R.id.h5_temp)
    )
    private val Q_IDS = arrayOf(
        intArrayOf(R.id.q0_name, R.id.q0_icon, R.id.q0_max, R.id.q0_min),
        intArrayOf(R.id.q1_name, R.id.q1_icon, R.id.q1_max, R.id.q1_min),
        intArrayOf(R.id.q2_name, R.id.q2_icon, R.id.q2_max, R.id.q2_min),
        intArrayOf(R.id.q3_name, R.id.q3_icon, R.id.q3_max, R.id.q3_min),
        intArrayOf(R.id.q4_name, R.id.q4_icon, R.id.q4_max, R.id.q4_min)
    )
    private val PRIMARY = intArrayOf(
        R.id.hum, R.id.wind, R.id.rise, R.id.sset,
        R.id.q0_name, R.id.q1_name, R.id.q2_name, R.id.q3_name, R.id.q4_name,
        R.id.q0_max, R.id.q1_max, R.id.q2_max, R.id.q3_max, R.id.q4_max,
        R.id.clock, R.id.now_temp, R.id.city, R.id.d1_name, R.id.d2_name, R.id.d3_name,
        R.id.h1_temp, R.id.h2_temp, R.id.h3_temp, R.id.h4_temp, R.id.h5_temp
    )
    private val SECONDARY = intArrayOf(
        R.id.hl2, R.id.lab_hum, R.id.lab_wind, R.id.lab_rise, R.id.lab_set,
        R.id.q0_min, R.id.q1_min, R.id.q2_min, R.id.q3_min, R.id.q4_min,
        R.id.date, R.id.date2, R.id.now_desc, R.id.hl, R.id.sep, R.id.extra,
        R.id.d1_temp, R.id.d2_temp, R.id.d3_temp,
        R.id.h1_time, R.id.h2_time, R.id.h3_time, R.id.h4_time, R.id.h5_time
    )

    fun refreshIntent(c: Context): Intent = Intent(c, WideWidget::class.java).setAction(ACTION_REFRESH)

    fun renderAll(c: Context, w: Weather?) {
        val mgr = AppWidgetManager.getInstance(c)
        val font = Style.font(c)
        val kinds = listOf(
            Pair(WideWidget::class.java, Layouts.WIDE[Style.wide(c)][font]),
            Pair(CompactWidget::class.java, Layouts.COMPACT[font]),
            Pair(SquareWidget::class.java, Layouts.SQUARE[font]),
            Pair(GlassWidget::class.java, Layouts.GLASS[font])
        )
        for ((cls, layout) in kinds) {
            val ids = mgr.getAppWidgetIds(ComponentName(c, cls))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(c, layout, w))
        }
    }

    private fun build(c: Context, layout: Int, w: Weather?): RemoteViews {
        val v = RemoteViews(c.packageName, layout)
        val bg = Style.bg(c)
        val isGlass = Layouts.GLASS.contains(layout)
        v.setImageViewResource(R.id.bg, if (isGlass) R.drawable.bg_none else Style.BGS[bg])

        val light = bg == 1 && !isGlass
        val c1 = if (light) 0xFF1B1F24.toInt() else 0xFFFFFFFF.toInt()
        val c2 = if (light) 0xFF5A636E.toInt() else 0xFFC9D1DC.toInt()
        for (id in PRIMARY) v.setTextColor(id, c1)
        for (id in SECONDARY) v.setTextColor(id, c2)
        for (id in intArrayOf(R.id.divider, R.id.vdiv)) {
            v.setInt(id, "setColorFilter", c1)
            v.setInt(id, "setImageAlpha", if (light) 35 else 55)
        }
        v.setInt(R.id.pin, "setColorFilter", c1)

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.clock_area, PendingIntent.getActivity(c, 1, alarms, flags))
        v.setOnClickPendingIntent(R.id.weather_area, PendingIntent.getBroadcast(c, 2, refreshIntent(c), flags))
        val settings = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.days_area, PendingIntent.getActivity(c, 3, settings, flags))
        v.setOnClickPendingIntent(R.id.city_row, PendingIntent.getActivity(c, 4, settings, flags))

        v.setTextViewText(R.id.city, WeatherRepo.city(c))

        if (w == null) {
            v.setTextViewText(R.id.now_desc, "Yükleniyor…")
            return v
        }
        v.setImageViewResource(R.id.now_icon, Style.icon(c, w.code, w.isDay))
        v.setTextViewText(R.id.now_temp, "${w.temp}°")
        v.setTextViewText(R.id.now_desc, WeatherRepo.label(w.code))
        val today = w.days.firstOrNull()
        if (today != null) {
            v.setTextViewText(R.id.hl, "${today.max}° / ${today.min}°")
            v.setTextViewText(R.id.hl2, "Y: ${today.max}°   D: ${today.min}°")
        }
        w.humidity?.let { v.setTextViewText(R.id.hum, "%$it") }
        w.wind?.let { v.setTextViewText(R.id.wind, "$it km/s") }
        w.sunrise?.let { v.setTextViewText(R.id.rise, it) }
        w.sunset?.let { v.setTextViewText(R.id.sset, it) }

        val extras = ArrayList<String>()
        w.feels?.let { extras.add("Hissedilen $it°") }
        w.humidity?.let { extras.add("Nem %$it") }
        w.wind?.let { extras.add("Rüzgâr $it km/s") }
        v.setTextViewText(R.id.extra, extras.joinToString("  ·  "))

        val tr = Locale("tr", "TR")
        for (k in DAY_IDS.indices) {
            val d = w.days.getOrNull(k + 1) ?: continue
            val name = LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
            v.setTextViewText(DAY_IDS[k][0], name)
            v.setImageViewResource(DAY_IDS[k][1], Style.icon(c, d.code, true))
            v.setTextViewText(DAY_IDS[k][2], "${d.max}° ${d.min}°")
        }
        for (k in Q_IDS.indices) {
            val d = w.days.getOrNull(k) ?: continue
            val name = if (k == 0) "Bugün"
                else LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.FULL, tr)
                    .replaceFirstChar { it.titlecase(tr) }
            v.setTextViewText(Q_IDS[k][0], name)
            v.setImageViewResource(Q_IDS[k][1], Style.icon(c, d.code, true))
            v.setTextViewText(Q_IDS[k][2], "${d.max}°")
            v.setTextViewText(Q_IDS[k][3], "${d.min}°")
        }
        for (k in HOUR_IDS.indices) {
            val h = w.hours.getOrNull(k + 1) ?: continue
            v.setTextViewText(HOUR_IDS[k][0], h.time)
            v.setImageViewResource(HOUR_IDS[k][1], Style.icon(c, h.code, h.isDay))
            v.setTextViewText(HOUR_IDS[k][2], "${h.temp}°")
        }
        return v
    }
}
