package com.caner.sketchweather

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.AlarmClock
import android.util.TypedValue
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

abstract class BaseWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Widgets.ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                Widgets.renderAll(app, WeatherRepo.cached(app))
                val last = WeatherRepo.prefs(app).getLong("fetched", 0L)
                if (System.currentTimeMillis() - last > 30 * 60 * 1000L) fetchAsync(app)
            }
            Widgets.ACTION_REFRESH,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                Widgets.renderAll(app, WeatherRepo.cached(app))
                fetchAsync(app)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun fetchAsync(app: Context) {
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
}

class WideWidget : BaseWidget()
class CompactWidget : BaseWidget()
class SquareWidget : BaseWidget()
class GlassWidget : BaseWidget()

object Widgets {
    const val ACTION_REFRESH = "com.caner.sketchweather.REFRESH"
    const val ACTION_TICK = "com.caner.sketchweather.TICK"

    /** Her düzen için: saat, derece, tarih boyutu (sp) ve tarih biçimi. */
    private class Spec(val clock: Float, val temp: Float, val date: Float, val datePattern: String)

    private val SPECS = mapOf(
        R.layout.w_wide0 to Spec(52f, 34f, 14f, "EEEE, d MMMM"),
        R.layout.w_wide1 to Spec(46f, 26f, 12f, "EEEE, d MMMM"),
        R.layout.w_wide2 to Spec(74f, 20f, 13f, "d MMMM, EEEE"),
        R.layout.w_wide3 to Spec(30f, 36f, 11f, "EEE, d MMM"),
        R.layout.w_wide4 to Spec(64f, 16f, 13f, "EEEE, d MMMM"),
        R.layout.w_wide5 to Spec(46f, 30f, 13f, "EEEE, d MMMM"),
        R.layout.w_compact to Spec(34f, 24f, 11f, "EEE, d MMM"),
        R.layout.w_square to Spec(20f, 30f, 11f, "EEE, d MMM"),
        R.layout.w_glass to Spec(42f, 30f, 12f, "d MMMM EEEE"),
        R.layout.w_wide6 to Spec(66f, 30f, 13f, "EEEE"),
        R.layout.w_wide7 to Spec(74f, 30f, 11f, "d MMMM · EEEE"),
        R.layout.w_wide8 to Spec(40f, 48f, 12f, "EEEE"),
        R.layout.w_compact1 to Spec(34f, 22f, 22f, "EEEE, d MMMM"),
        R.layout.w_compact2 to Spec(44f, 16f, 11f, "EEE"),
        R.layout.w_square1 to Spec(32f, 20f, 11f, "EEE"),
        R.layout.w_square2 to Spec(30f, 40f, 11f, "EEE"),
        R.layout.w_square3 to Spec(64f, 20f, 11f, "EEE"),
        R.layout.w_square4 to Spec(30f, 40f, 11f, "EEE")
    )
    private val PALETTE_LAYOUTS = setOf(
        R.layout.w_wide6, R.layout.w_wide7, R.layout.w_wide8, R.layout.w_compact1, R.layout.w_compact2,
        R.layout.w_square1, R.layout.w_square2, R.layout.w_square3, R.layout.w_square4
    )

    private val DAY_IDS = arrayOf(
        intArrayOf(R.id.d1_name, R.id.d1_icon, R.id.d1_temp),
        intArrayOf(R.id.d2_name, R.id.d2_icon, R.id.d2_temp),
        intArrayOf(R.id.d3_name, R.id.d3_icon, R.id.d3_temp)
    )
    private val HOUR_IDS = arrayOf(
        intArrayOf(R.id.h0_time, R.id.h0_icon, R.id.h0_temp),
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
        R.id.city, R.id.hum, R.id.wind, R.id.rise, R.id.sset,
        R.id.d1_name, R.id.d2_name, R.id.d3_name,
        R.id.h1_temp, R.id.h2_temp, R.id.h3_temp, R.id.h4_temp, R.id.h5_temp,
        R.id.q0_name, R.id.q1_name, R.id.q2_name, R.id.q3_name, R.id.q4_name,
        R.id.q0_max, R.id.q1_max, R.id.q2_max, R.id.q3_max, R.id.q4_max
    )
    private val SECONDARY = intArrayOf(
        R.id.now_desc, R.id.feels, R.id.hl, R.id.hl2, R.id.sep, R.id.extra,
        R.id.lab_hum, R.id.lab_wind, R.id.lab_rise, R.id.lab_set,
        R.id.d1_temp, R.id.d2_temp, R.id.d3_temp,
        R.id.h1_time, R.id.h2_time, R.id.h3_time, R.id.h4_time, R.id.h5_time,
        R.id.q0_min, R.id.q1_min, R.id.q2_min, R.id.q3_min, R.id.q4_min
    )

    fun refreshIntent(c: Context): Intent = Intent(c, WideWidget::class.java).setAction(ACTION_REFRESH)

    fun renderAll(c: Context, w: Weather?) {
        val mgr = AppWidgetManager.getInstance(c)
        var any = false
        for (kind in Kind.values()) {
            for (id in mgr.getAppWidgetIds(ComponentName(c, kind.cls))) {
                any = true
                try {
                    mgr.updateAppWidget(id, build(c, kind.layouts[kind.style(c, id)], w))
                } catch (e: Exception) {
                    // tek bir widget hata verse de diğerleri çizilsin
                }
            }
        }
        if (any) scheduleTick(c)
    }

    /** Saati her dakika başında yeniler. */
    fun scheduleTick(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, 10, Intent(c, WideWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val next = (System.currentTimeMillis() / 60000L + 1) * 60000L
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setWindow(AlarmManager.RTC, next, 5000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            }
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC, next, 5000L, pi)
        }
    }

    private fun textBitmap(c: Context, text: String, tf: Typeface, sp: Float, color: Int, shadow: Boolean): Bitmap {
        val dm = c.resources.displayMetrics
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = tf
            textSize = px
            this.color = color
            if (shadow) setShadowLayer(px * 0.07f, 0f, px * 0.03f, 0x80000000.toInt())
        }
        val fm = paint.fontMetrics
        val pad = if (shadow) px * 0.12f else 2f
        val w = ceil(paint.measureText(text) + pad * 2).toInt().coerceAtLeast(1)
        val h = ceil(fm.descent - fm.ascent + pad * 2).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad, pad - fm.ascent, paint)
        bmp.density = dm.densityDpi
        return bmp
    }

    private fun tint(v: RemoteViews, id: Int, color: Int) {
        v.setInt(id, "setColorFilter", color or 0xFF000000.toInt())
        v.setInt(id, "setImageAlpha", android.graphics.Color.alpha(color))
    }

    private fun hm(t: String?): Int? = try {
        if (t == null) null else t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt()
    } catch (e: Exception) { null }

    /** Kadran: koyu daire + günün ilerleyişini gösteren yay (gün doğumu → gün batımı). */
    private fun dialDay(pal: Pal, w: Weather?): Bitmap {
        val n = 600
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = pal.dark
        cv.drawCircle(n / 2f, n / 2f, n / 2f - 4, p)
        val cal = java.util.Calendar.getInstance()
        val nowM = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val rise = hm(w?.sunrise) ?: 7 * 60
        val set = hm(w?.sunset) ?: 19 * 60
        val frac = ((nowM - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
        val r = RectF(40f, 40f, n - 40f, n - 40f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 22f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0x40FFFFFF
        cv.drawArc(r, 135f, 270f, false, p)
        p.color = pal.accentLight
        cv.drawArc(r, 135f, 270f * frac, false, p)
        val ang = Math.toRadians((135.0 + 270.0 * frac))
        p.style = Paint.Style.FILL
        cv.drawCircle((n / 2f + (n / 2f - 40f) * Math.cos(ang)).toFloat(), (n / 2f + (n / 2f - 40f) * Math.sin(ang)).toFloat(), 20f, p)
        return b
    }

    /** Gösterge: açık daire + derecenin gün içindeki en düşük/en yüksek arasındaki yeri. */
    private fun dialGauge(pal: Pal, w: Weather?): Bitmap {
        val n = 600
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = pal.container
        cv.drawCircle(n / 2f, n / 2f, n / 2f - 4, p)
        val d = w?.days?.firstOrNull()
        val frac = if (w != null && d != null && d.max > d.min)
            ((w.temp - d.min).toFloat() / (d.max - d.min)).coerceIn(0f, 1f) else 0.5f
        val r = RectF(44f, 44f, n - 44f, n - 44f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 30f
        p.strokeCap = Paint.Cap.ROUND
        p.color = pal.containerHigh
        cv.drawArc(r, 150f, 240f, false, p)
        p.color = pal.accent
        cv.drawArc(r, 150f, 240f * frac, false, p)
        return b
    }

    private fun build(c: Context, layout: Int, w: Weather?): RemoteViews {
        val v = RemoteViews(c.packageName, layout)
        val spec = SPECS[layout] ?: SPECS.getValue(R.layout.w_wide0)
        val pal = Palette.get(c)
        val paletteStyle = layout in PALETTE_LAYOUTS
        val isGlass = layout == R.layout.w_glass

        var c1: Int
        var c2: Int
        var shadow: Boolean
        if (paletteStyle) {
            c1 = pal.onCont; c2 = pal.onContSub; shadow = false
        } else {
            val bg = if (isGlass) 2 else Style.bg(c)
            when (bg) {
                1 -> { v.setImageViewResource(R.id.bg, R.drawable.bg_light); c1 = 0xFF1B1F24.toInt(); c2 = 0xFF5A636E.toInt(); shadow = false }
                2 -> { v.setImageViewResource(R.id.bg, R.drawable.bg_none); c1 = 0xFFFFFFFF.toInt(); c2 = 0xFFE3E7EE.toInt(); shadow = true }
                3 -> { v.setImageViewResource(R.id.bg, R.drawable.shape_round); tint(v, R.id.bg, pal.container); c1 = pal.onCont; c2 = pal.onContSub; shadow = false }
                else -> { v.setImageViewResource(R.id.bg, R.drawable.bg_dark); c1 = 0xFFFFFFFF.toInt(); c2 = 0xFFC9D1DC.toInt(); shadow = false }
            }
        }
        for (id in PRIMARY) v.setTextColor(id, c1)
        for (id in SECONDARY) v.setTextColor(id, c2)
        for (id in intArrayOf(R.id.divider, R.id.vdiv)) {
            v.setInt(id, "setColorFilter", c1)
            v.setInt(id, "setImageAlpha", 60)
        }
        v.setInt(R.id.pin, "setColorFilter", c1)

        val font = Style.font(c)
        val clockTf = Style.clockTypeface(c, font)
        val textTf = Style.textTypeface(c, font)
        val tr = Locale("tr", "TR")
        val now = Date()
        val hh = SimpleDateFormat("HH", tr).format(now)
        val mm = SimpleDateFormat("mm", tr).format(now)
        val dateStr = SimpleDateFormat(spec.datePattern, tr).format(now)
        val tempStr = if (w != null) "${w.temp}°" else "--°"
        val feelsStr = if (w?.feels != null) "${w.feels}°" else "--°"

        // Varsayılan renklerle metin görselleri
        var clockCol = c1; var dateCol = c2; var tempCol = c1
        var hourCol = c1; var minCol = c1; var feelsCol = c1
        var tempTf = clockTf
        var dateTf = textTf

        when (layout) {
            R.layout.w_wide6 -> {
                tint(v, R.id.cbg1, pal.dark); tint(v, R.id.cbg2, pal.accent)
                tint(v, R.id.cbg3, pal.container); tint(v, R.id.cbg4, pal.dark)
                dateCol = pal.onDarkSub; hourCol = pal.onDark; minCol = pal.accentLight
                tempCol = pal.onAccent; feelsCol = pal.onCont
                v.setTextColor(R.id.feels_lab, pal.onContSub)
                v.setTextColor(R.id.hum, pal.onDark); v.setTextColor(R.id.wind, pal.onDarkSub)
            }
            R.layout.w_wide7 -> {
                tint(v, R.id.bg, pal.container)
                dateCol = pal.onContSub; clockCol = pal.onCont; tempCol = pal.onCont
            }
            R.layout.w_wide8 -> {
                tint(v, R.id.cbg1, pal.container); tint(v, R.id.cbg2, pal.containerHigh); tint(v, R.id.cbg3, pal.accent)
                tempCol = pal.onCont
                for (id in intArrayOf(R.id.h1_time, R.id.h2_time, R.id.h3_time, R.id.h4_time, R.id.h5_time)) v.setTextColor(id, pal.onContSub)
                for (id in intArrayOf(R.id.h1_temp, R.id.h2_temp, R.id.h3_temp, R.id.h4_temp, R.id.h5_temp)) v.setTextColor(id, pal.onCont)
                v.setTextColor(R.id.h0_time, pal.onAccent); v.setTextColor(R.id.h0_temp, pal.onAccent)
            }
            R.layout.w_compact1 -> {
                shadow = true; dateCol = 0xFFFFFFFF.toInt(); tempCol = 0xFFFFFFFF.toInt(); tempTf = textTf
                for (id in intArrayOf(R.id.now_desc, R.id.sep, R.id.hl)) v.setTextColor(id, 0xFFE8ECF2.toInt())
            }
            R.layout.w_compact2 -> {
                tint(v, R.id.cbg1, pal.container); tint(v, R.id.cbg2, pal.accent)
                clockCol = pal.onCont; tempCol = pal.onAccent
            }
            R.layout.w_square1 -> {
                v.setImageViewBitmap(R.id.dial, dialDay(pal, w))
                clockCol = pal.onDark; v.setTextColor(R.id.mini, pal.onDarkSub)
                v.setTextViewText(R.id.mini, "$tempStr · ${WeatherRepo.city(c)}")
            }
            R.layout.w_square2 -> {
                v.setImageViewBitmap(R.id.dial, dialGauge(pal, w))
                tempCol = pal.onCont; v.setTextColor(R.id.hl, pal.onContSub)
            }
            R.layout.w_square3 -> {
                shadow = true; hourCol = pal.accentLight; minCol = 0xFFFFFFFF.toInt()
            }
            R.layout.w_square4 -> {
                tint(v, R.id.cbg1, pal.container)
                tempCol = pal.onCont; v.setTextColor(R.id.hl, pal.onContSub)
            }
        }
        if (layout == R.layout.w_wide7 || layout == R.layout.w_wide8) {
            v.setTextColor(R.id.city, pal.onCont); v.setInt(R.id.pin, "setColorFilter", pal.onCont)
            v.setTextColor(R.id.now_desc, pal.onContSub)
        }

        v.setImageViewBitmap(R.id.clock, textBitmap(c, "$hh:$mm", clockTf, spec.clock, clockCol, shadow))
        v.setImageViewBitmap(R.id.clock_h, textBitmap(c, hh, clockTf, spec.clock, hourCol, shadow))
        v.setImageViewBitmap(R.id.clock_m, textBitmap(c, mm, clockTf, spec.clock, minCol, shadow))
        v.setImageViewBitmap(R.id.date, textBitmap(c, dateStr, dateTf, spec.date, dateCol, shadow))
        v.setImageViewBitmap(R.id.now_temp, textBitmap(c, tempStr, tempTf, spec.temp, tempCol, shadow))
        v.setImageViewBitmap(R.id.feels_img, textBitmap(c, feelsStr, clockTf, 22f, feelsCol, false))

        // Hareketli ikon
        v.setInt(R.id.icon_flip, "setFlipInterval", Style.animInterval(c))

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
        val icon = Style.icon(c, w.code, w.isDay)
        for (id in intArrayOf(R.id.now_icon, R.id.now_icon2, R.id.now_icon3)) v.setImageViewResource(id, icon)
        v.setTextViewText(R.id.now_desc, WeatherRepo.label(w.code))
        w.feels?.let { v.setTextViewText(R.id.feels, "Hissedilen $it°") }
        val today = w.days.firstOrNull()
        if (today != null) {
            v.setTextViewText(R.id.hl, if (layout == R.layout.w_compact1) "En yüksek ${today.max}°" else "${today.max}° / ${today.min}°")
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
            val h = w.hours.getOrNull(k) ?: continue
            v.setTextViewText(HOUR_IDS[k][0], if (k == 0) "Şimdi" else h.time.substring(0, 2))
            v.setImageViewResource(HOUR_IDS[k][1], Style.icon(c, h.code, h.isDay))
            v.setTextViewText(HOUR_IDS[k][2], "${h.temp}°")
        }
        return v
    }
}
