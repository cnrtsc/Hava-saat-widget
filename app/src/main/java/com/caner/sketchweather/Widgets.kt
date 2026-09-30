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
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> Widgets.handle(context, true, goAsync())
            else -> super.onReceive(context, intent)
        }
    }
}

/** Dakika tiki, yenileme, saat/tarih değişimi ve açılış olaylarını karşılar. */
class TimeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val force = intent.action == Widgets.ACTION_REFRESH
        val last = WeatherRepo.prefs(app).getLong("fetched", 0L)
        val stale = System.currentTimeMillis() - last > 30 * 60 * 1000L
        Widgets.handle(app, force || stale, goAsync())
    }
}

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
        R.layout.w_square4 to Spec(30f, 40f, 11f, "EEE"),
        R.layout.w_wide9 to Spec(62f, 30f, 17f, "EEEE, d MMMM"),
        R.layout.w_square5 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_square6 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_combo to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_roundw0 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_roundw1 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_roundw2 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_lock to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_lockc to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_duo to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_mod_pil to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_mod_ay to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_mod_takvim to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_strip to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_graph to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_bentox to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_bentosq to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_dot to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_flip to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_swiss to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_prog to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_term to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_paper to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_sector to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_th_neu to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_square7 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_square8 to Spec(30f, 30f, 11f, "EEE"),
        R.layout.w_compact3 to Spec(30f, 18f, 11f, "EEE")
    )
    private val PALETTE_LAYOUTS = setOf(
        R.layout.w_wide6, R.layout.w_wide7, R.layout.w_wide8, R.layout.w_compact1, R.layout.w_compact2,
        R.layout.w_square1, R.layout.w_square2, R.layout.w_square3, R.layout.w_square4, R.layout.w_square5,
        R.layout.w_square6, R.layout.w_square7, R.layout.w_square8, R.layout.w_combo,
        R.layout.w_roundw0, R.layout.w_roundw1, R.layout.w_roundw2, R.layout.w_lock, R.layout.w_lockc,
        R.layout.w_duo, R.layout.w_mod_pil, R.layout.w_mod_ay, R.layout.w_mod_takvim, R.layout.w_strip, R.layout.w_graph,
        R.layout.w_bentox, R.layout.w_bentosq,
        R.layout.w_th_dot, R.layout.w_th_flip, R.layout.w_th_swiss, R.layout.w_th_prog, R.layout.w_th_term, R.layout.w_th_paper, R.layout.w_th_sector, R.layout.w_th_neu
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

    private val WEATHER_APPS = listOf(
        "com.hihonor.android.totemweather", "com.huawei.android.totemweather", "com.hihonor.weather",
        "com.google.android.apps.weather", "com.miui.weather2", "com.sec.android.daemonapp",
        "com.coloros.weather2", "com.oplus.weather2", "com.accuweather.android"
    )

    /** Hava alanına dokununca: telefondaki hava uygulaması; yoksa web'de hava durumu. */
    fun weatherIntent(c: Context): Intent {
        for (p in WEATHER_APPS) {
            val i = try { c.packageManager.getLaunchIntentForPackage(p) } catch (e: Exception) { null }
            if (i != null) return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=hava+durumu+" + android.net.Uri.encode(WeatherRepo.city(c))))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun refreshIntent(c: Context): Intent = Intent(c, TimeReceiver::class.java).setAction(ACTION_REFRESH)

    fun handle(c: Context, fetch: Boolean, pending: android.content.BroadcastReceiver.PendingResult) {
        val app = c.applicationContext
        renderAll(app, WeatherRepo.cached(app))
        if (!fetch) { pending.finish(); return }
        Thread {
            try {
                renderAll(app, WeatherRepo.fetch(app))
            } catch (e: Exception) {
                // ağ yoksa önbellek kalır
            } finally {
                pending.finish()
            }
        }.start()
    }

    fun renderAll(c: Context, w: Weather?) {
        val mgr = AppWidgetManager.getInstance(c)
        var any = false
        val p = WeatherRepo.prefs(c)
        for (e in Registry.ALL) {
            for (id in mgr.getAppWidgetIds(ComponentName(c, e.cls))) {
                any = true
                // Uygulamadan stil değiştirildiyse onu kullan
                val layout = layoutFor(c, id, e)
                val o = mgr.getAppWidgetOptions(id)
                var wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                var hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                if (wdp <= 0 || hdp <= 0) {
                    wdp = when (e.size) { "s" -> 160; "l" -> 360; else -> 320 }
                    hdp = when (e.size) { "s" -> 160; "c" -> 70; else -> 170 }
                }
                try {
                    mgr.updateAppWidget(id, Cfg.with(id) { build(c, layout, w, wdp, hdp) })
                } catch (ex: Exception) {
                    // bir stil hata verse de diğerleri çizilsin
                }
            }
        }
        if (any) scheduleTick(c)
    }

    fun layoutFor(c: Context, id: Int, e: Entry): Int {
        val ov = Registry.ALL.getOrNull(WeatherRepo.prefs(c).getInt("ov_$id", -1))
        return if (ov != null && ov.size == e.size) ov.layout else e.layout
    }

    /** Uygulama içi canlı önizleme için: widget'ın şu anki ayarlarıyla RemoteViews üretir. */
    fun preview(c: Context, id: Int, wdp: Int, hdp: Int): RemoteViews? {
        val info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id) ?: return null
        val e = Registry.ofClass(info.provider.className) ?: return null
        return Cfg.with(id) { build(c, layoutFor(c, id, e), WeatherRepo.cached(c), wdp, hdp) }
    }

    /** Saati her dakika başında yeniler. */
    fun scheduleTick(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, 10, Intent(c, TimeReceiver::class.java).setAction(ACTION_TICK),
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

    private fun textBitmap(c: Context, text: String, tf: Typeface, sp: Float, color: Int, shadow: Boolean, fx: Int = 0): Bitmap {
        val dm = c.resources.displayMetrics
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = tf
            textSize = px
            this.color = color
            if (shadow) setShadowLayer(px * 0.07f, 0f, px * 0.03f, 0x80000000.toInt())
            if (fx == 1) { style = Paint.Style.STROKE; strokeWidth = px * 0.028f; strokeJoin = Paint.Join.ROUND }
            if (fx == 2) setShadowLayer(px * 0.06f, px * 0.02f, px * 0.04f, 0xA0000000.toInt())
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

    private fun tint(v: RemoteViews, id: Int, color: Int, f: Float = 1f) {
        v.setInt(id, "setColorFilter", color or 0xFF000000.toInt())
        v.setInt(id, "setImageAlpha", (android.graphics.Color.alpha(color) * f).toInt())
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

    private fun face(pal: Pal, n: Int, cv: Canvas, ticks: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = n / 2f
        p.color = pal.container
        cv.drawCircle(cx, cx, cx - 4, p)
        if (!ticks) return
        p.strokeCap = Paint.Cap.ROUND
        for (k in 0 until 60) {
            val a = Math.toRadians(k * 6.0 - 90)
            val major = k % 5 == 0
            p.color = if (major) pal.onCont else pal.onContSub
            p.strokeWidth = if (major) 10f else 4f
            val r1 = if (major) cx - 58 else cx - 44
            val r2 = cx - 30
            cv.drawLine((cx + r1 * Math.cos(a)).toFloat(), (cx + r1 * Math.sin(a)).toFloat(),
                (cx + r2 * Math.cos(a)).toFloat(), (cx + r2 * Math.sin(a)).toFloat(), p)
        }
    }

    private fun txt(tf: Typeface, size: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; color = col; textAlign = Paint.Align.CENTER
    }

    private fun drawIcon(c: Context, cv: Canvas, res: Int, cx: Float, cy: Float, size: Float) {
        try {
            val ic = android.graphics.BitmapFactory.decodeResource(c.resources, res)
            cv.drawBitmap(ic, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        } catch (e: Exception) { }
    }

    /** Analog saat. withWeather: üstte ikon+derece; her iki durumda altta dijital saat. */
    private fun analog(c: Context, pal: Pal, w: Weather?, tf: Typeface, withWeather: Boolean): Bitmap {
        val n = 600
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val cx = n / 2f
        face(pal, n, cv, true)
        val cal = java.util.Calendar.getInstance()
        val digital = String.format(Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        if (withWeather && w != null) {
            drawIcon(c, cv, Style.icon(c, w.code, w.isDay), cx - 44, n * 0.30f, 84f)
            cv.drawText("${w.temp}°", cx + 34, n * 0.30f + 20, txt(tf, 56f, pal.onCont))
        } else {
            val day = SimpleDateFormat("EEE d", Locale("tr", "TR")).format(Date())
            cv.drawText(day, cx, n * 0.33f, txt(tf, 44f, pal.onContSub))
        }
        // dijital saat, altta yuvarlak bir kutuda
        val dp = txt(tf, 50f, pal.onCont)
        val tw = dp.measureText(digital)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.containerHigh }
        cv.drawRoundRect(RectF(cx - tw / 2 - 22, n * 0.66f, cx + tw / 2 + 22, n * 0.66f + 74), 37f, 37f, bp)
        cv.drawText(digital, cx, n * 0.66f + 55, dp)
        val m = cal.get(java.util.Calendar.MINUTE)
        val h = cal.get(java.util.Calendar.HOUR) + m / 60f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        fun hand(angDeg: Double, len: Float, width: Float, col: Int) {
            val a = Math.toRadians(angDeg - 90)
            p.color = col; p.strokeWidth = width
            cv.drawLine(cx, cx, (cx + len * Math.cos(a)).toFloat(), (cx + len * Math.sin(a)).toFloat(), p)
        }
        hand(h * 30.0, cx * 0.50f, 22f, pal.onCont)
        hand(m * 6.0, cx * 0.74f, 14f, pal.accent)
        p.color = pal.accent
        cv.drawCircle(cx, cx, 18f, p)
        return b
    }

    /** Yuvarlak hava: büyük ikon, derece, en yüksek/düşük ve hissedilen; kenarda nem yayı. */
    private fun roundWeather(c: Context, pal: Pal, w: Weather?, tf: Typeface): Bitmap {
        val n = 600
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val cx = n / 2f
        face(pal, n, cv, false)
        val hum = (w?.humidity ?: 0).coerceIn(0, 100)
        val r = RectF(34f, 34f, n - 34f, n - 34f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 16f; strokeCap = Paint.Cap.ROUND }
        p.color = pal.containerHigh; cv.drawArc(r, 120f, 300f, false, p)
        p.color = pal.accent; cv.drawArc(r, 120f, 300f * hum / 100f, false, p)
        if (w == null) {
            cv.drawText("--°", cx, cx + 20, txt(tf, 90f, pal.onCont))
            return b
        }
        drawIcon(c, cv, Style.icon(c, w.code, w.isDay), cx, n * 0.27f, 150f)
        cv.drawText("${w.temp}°", cx + 8, n * 0.60f, txt(tf, 110f, pal.onCont))
        val d = w.days.firstOrNull()
        if (d != null) cv.drawText("Y ${d.max}°  ·  D ${d.min}°", cx, n * 0.71f, txt(tf, 36f, pal.onContSub))
        w.feels?.let { cv.drawText("Hissedilen $it°", cx, n * 0.79f, txt(tf, 32f, pal.onContSub)) }
        cv.drawText("%$hum", cx, n * 0.92f, txt(tf, 30f, pal.accent))
        return b
    }

    /** Yuvarlak gün: güneşin gökyüzündeki yolu, doğuş/batış saatleri ve kalan süre. */
    private fun roundSun(c: Context, pal: Pal, w: Weather?, tf: Typeface): Bitmap {
        val n = 600
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val cx = n / 2f
        face(pal, n, cv, false)
        val cal = java.util.Calendar.getInstance()
        val nowM = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val rise = hm(w?.sunrise) ?: 7 * 60
        val set = hm(w?.sunset) ?: 19 * 60
        val frac = ((nowM - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
        val hy = n * 0.56f
        val R = n * 0.34f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
        p.color = pal.containerHigh
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 18f), 0f)
        cv.drawArc(RectF(cx - R, hy - R, cx + R, hy + R), 180f, 180f, false, p)
        p.pathEffect = null; p.color = pal.accent; p.strokeWidth = 12f
        cv.drawArc(RectF(cx - R, hy - R, cx + R, hy + R), 180f, 180f * frac, false, p)
        p.color = pal.onContSub; p.strokeWidth = 5f
        cv.drawLine(cx - R - 30, hy, cx + R + 30, hy, p)
        val ang = Math.toRadians(180.0 + 180.0 * frac)
        val sx = (cx + R * Math.cos(ang)).toFloat()
        val sy = (hy + R * Math.sin(ang)).toFloat()
        val day = nowM in rise..set
        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (day) 0xFFFFB42A.toInt() else pal.onContSub }
        cv.drawCircle(sx, sy, 28f, sun)
        cv.drawText(w?.sunrise ?: "--:--", cx - R + 10, hy + 58, txt(tf, 36f, pal.onCont))
        cv.drawText(w?.sunset ?: "--:--", cx + R - 10, hy + 58, txt(tf, 36f, pal.onCont))
        val left = if (day) set - nowM else ((rise + 24 * 60 - nowM) % (24 * 60))
        val label = if (day) "Gün batımına" else "Gün doğumuna"
        cv.drawText(label, cx, n * 0.80f, txt(tf, 30f, pal.onContSub))
        cv.drawText("${left / 60} sa ${left % 60} dk", cx, n * 0.87f, txt(tf, 38f, pal.onCont))
        return b
    }

    fun build(c: Context, layout: Int, w: Weather?, wdp: Int = 320, hdp: Int = 170): RemoteViews {
        val v = RemoteViews(c.packageName, layout)
        val spec = SPECS[layout] ?: SPECS.getValue(R.layout.w_wide0)
        val pal = Palette.get(c)
        val opF = Style.opacity(c) / 100f
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
                3 -> { v.setImageViewResource(R.id.bg, R.drawable.shape_round); tint(v, R.id.bg, pal.container, opF); c1 = pal.onCont; c2 = pal.onContSub; shadow = false }
                else -> { v.setImageViewResource(R.id.bg, R.drawable.bg_dark); c1 = 0xFFFFFFFF.toInt(); c2 = 0xFFC9D1DC.toInt(); shadow = false }
            }
        }
        if (!paletteStyle && !isGlass && Style.bg(c) != 2) v.setInt(R.id.bg, "setImageAlpha", (opF * 255).toInt())
        Style.textColor(c)?.let { tc ->
            c1 = tc
            c2 = android.graphics.Color.argb(205, android.graphics.Color.red(tc), android.graphics.Color.green(tc), android.graphics.Color.blue(tc))
            if (!paletteStyle) shadow = shadow || Style.bg(c) == 2
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
        var handFont = false
        var home: String? = null

        when (layout) {
            R.layout.w_wide6 -> {
                tint(v, R.id.cbg1, pal.dark, opF); tint(v, R.id.cbg2, pal.accent, opF)
                tint(v, R.id.cbg3, pal.container, opF); tint(v, R.id.cbg4, pal.dark, opF)
                dateCol = pal.onDarkSub; hourCol = pal.onDark; minCol = pal.accentLight
                tempCol = pal.onAccent; feelsCol = pal.onCont
                v.setTextColor(R.id.feels_lab, pal.onContSub)
                v.setTextColor(R.id.hum, pal.onDark); v.setTextColor(R.id.wind, pal.onDarkSub)
                v.setTextColor(R.id.city, pal.onDark); v.setInt(R.id.pin, "setColorFilter", pal.onDarkSub)
            }
            R.layout.w_wide7 -> {
                tint(v, R.id.bg, pal.container, opF)
                dateCol = pal.onContSub; clockCol = pal.onCont; tempCol = pal.onCont
            }
            R.layout.w_wide8 -> {
                tint(v, R.id.cbg1, pal.container, opF); tint(v, R.id.cbg2, pal.containerHigh, opF); tint(v, R.id.cbg3, pal.accent, opF)
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
                tint(v, R.id.cbg1, pal.container, opF); tint(v, R.id.cbg2, pal.accent, opF)
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
                tint(v, R.id.cbg1, pal.container, opF)
                tempCol = pal.onCont; v.setTextColor(R.id.hl, pal.onContSub)
            }
            R.layout.w_square5 -> v.setImageViewBitmap(R.id.dial, Combo.render(c, w, wdp, hdp, 1))
            R.layout.w_square6 -> v.setImageViewBitmap(R.id.dial, Combo.render(c, w, wdp, hdp, 2))
            R.layout.w_combo -> v.setImageViewBitmap(R.id.dial, Combo.render(c, w, wdp, hdp, 0))
            R.layout.w_roundw0 -> v.setImageViewBitmap(R.id.dial, RoundW.render(c, w, wdp, hdp, 0))
            R.layout.w_roundw1 -> v.setImageViewBitmap(R.id.dial, RoundW.render(c, w, wdp, hdp, 1))
            R.layout.w_roundw2 -> v.setImageViewBitmap(R.id.dial, RoundW.render(c, w, wdp, hdp, 2))
            R.layout.w_lock -> v.setImageViewBitmap(R.id.dial, Lock.render(c, w, wdp, hdp, false))
            R.layout.w_lockc -> v.setImageViewBitmap(R.id.dial, Lock.render(c, w, wdp, hdp, true))
            R.layout.w_duo -> v.setImageViewBitmap(R.id.dial, Mod.render(c, w, wdp, hdp, intArrayOf(Mod.slot(c, "slotL", 0), Mod.slot(c, "slotR", 2))))
            R.layout.w_mod_pil -> v.setImageViewBitmap(R.id.dial, Mod.render(c, w, wdp, hdp, intArrayOf(Mod.slot(c, "slot", 6))))
            R.layout.w_mod_ay -> v.setImageViewBitmap(R.id.dial, Mod.render(c, w, wdp, hdp, intArrayOf(Mod.slot(c, "slot", 7))))
            R.layout.w_mod_takvim -> v.setImageViewBitmap(R.id.dial, Mod.render(c, w, wdp, hdp, intArrayOf(Mod.slot(c, "slot", 8))))
            R.layout.w_strip -> v.setImageViewBitmap(R.id.dial, Extra.strip(c, w, wdp, hdp))
            R.layout.w_graph -> v.setImageViewBitmap(R.id.dial, Extra.graph(c, w, wdp, hdp))
            R.layout.w_bentox -> v.setImageViewBitmap(R.id.dial, Bento.render(c, w, wdp, hdp, false))
            R.layout.w_bentosq -> v.setImageViewBitmap(R.id.dial, Bento.render(c, w, wdp, hdp, true))
            R.layout.w_th_dot -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.DOT))
            R.layout.w_th_flip -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.FLIP))
            R.layout.w_th_swiss -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.SWISS))
            R.layout.w_th_prog -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.PROG))
            R.layout.w_th_term -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.TERM))
            R.layout.w_th_paper -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.PAPER))
            R.layout.w_th_sector -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.SECTOR))
            R.layout.w_th_neu -> v.setImageViewBitmap(R.id.dial, Themes.render(c, w, wdp, hdp, Themes.NEU))
            R.layout.w_square7 -> v.setImageViewBitmap(R.id.dial, roundWeather(c, pal, w, textTf))
            R.layout.w_square8 -> v.setImageViewBitmap(R.id.dial, roundSun(c, pal, w, textTf))
            R.layout.w_wide9 -> {
                val ink = 0xFF2C2A48.toInt()
                clockCol = ink; dateCol = 0xFF55536E.toInt(); tempCol = ink; feelsCol = 0xFF55536E.toInt()
                handFont = true
            }
            R.layout.w_compact3 -> {
                v.setTextViewText(R.id.city2, Style.homeName(c))
                val f = SimpleDateFormat("HH:mm", tr)
                f.timeZone = java.util.TimeZone.getTimeZone(Style.homeTz(c))
                home = f.format(now)
            }
        }
        val bs = Cfg.int(c, "boxShape", 0)
        if (bs > 0 && paletteStyle && layout != R.layout.w_square4) {
            val shapeRes = when (bs) { 1 -> R.drawable.shape_round; 2 -> R.drawable.shape_sharp; else -> R.drawable.shape_pill }
            for (id in intArrayOf(R.id.cbg1, R.id.cbg2, R.id.cbg3, R.id.cbg4)) v.setImageViewResource(id, shapeRes)
            if (layout == R.layout.w_wide7) v.setImageViewResource(R.id.bg, shapeRes)
        }
        if (layout == R.layout.w_wide7 || layout == R.layout.w_wide8) {
            v.setTextColor(R.id.city, pal.onCont); v.setInt(R.id.pin, "setColorFilter", pal.onCont)
            v.setTextColor(R.id.now_desc, pal.onContSub)
        }

        val cTf = if (handFont) Style.handTypeface(c) else clockTf
        if (handFont) { tempTf = cTf; dateTf = cTf }
        val fx = Style.clockFx(c)
        v.setImageViewBitmap(R.id.clock, textBitmap(c, "$hh:$mm", cTf, spec.clock, clockCol, shadow, fx))
        home?.let { v.setImageViewBitmap(R.id.clock2, textBitmap(c, it, clockTf, spec.clock, c1, shadow)) }
        v.setImageViewBitmap(R.id.clock_h, textBitmap(c, hh, clockTf, spec.clock, hourCol, shadow, fx))
        v.setImageViewBitmap(R.id.clock_m, textBitmap(c, mm, clockTf, spec.clock, minCol, shadow, fx))
        v.setImageViewBitmap(R.id.date, textBitmap(c, dateStr, dateTf, spec.date, dateCol, shadow))
        v.setImageViewBitmap(R.id.now_temp, textBitmap(c, tempStr, tempTf, spec.temp, tempCol, shadow))
        if (handFont) v.setImageViewBitmap(R.id.feels_img, textBitmap(c, "hissedilen $feelsStr", cTf, 16f, feelsCol, false))
        else v.setImageViewBitmap(R.id.feels_img, textBitmap(c, feelsStr, clockTf, 22f, feelsCol, false))

        // Hareketli ikon
        v.setInt(R.id.icon_flip, "setFlipInterval", Style.animInterval(c))
        // Bazı ana ekranlar otomatik geçişi durdurduğu için her dakika kareyi kendimiz değiştiriyoruz
        if (Style.anim(c) > 0) v.setDisplayedChild(R.id.icon_flip, java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE) % 3)

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.clock_area, PendingIntent.getActivity(c, 1, alarms, flags))
        v.setOnClickPendingIntent(R.id.weather_area, PendingIntent.getActivity(c, 2, weatherIntent(c), flags))
        val settings = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.days_area, PendingIntent.getActivity(c, 3, settings, flags))
        v.setOnClickPendingIntent(R.id.city_row, PendingIntent.getActivity(c, 4, settings, flags))

        v.setTextViewText(R.id.city, WeatherRepo.city(c))

        if (w == null) {
            v.setTextViewText(R.id.now_desc, "Yükleniyor…")
            return v
        }
        val icon = if (handFont) Style.iconFrom(7, w.code, w.isDay) else Style.icon(c, w.code, w.isDay)
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
