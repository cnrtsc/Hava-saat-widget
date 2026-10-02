package com.caner.sketchweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Day(val date: String, val code: Int, val max: Int, val min: Int)
data class Hour(val time: String, val code: Int, val isDay: Boolean, val temp: Int)
data class Weather(
    val temp: Int, val code: Int, val isDay: Boolean,
    val feels: Int?, val humidity: Int?, val wind: Int?,
    val days: List<Day>, val hours: List<Hour>,
    val sunrise: String?, val sunset: String?,
    val rainChance: Int? = null, val uv: Int? = null, val aqi: Int? = null,
    val rain15: List<Float> = emptyList(), val rain15Start: String? = null
)
data class Place(val name: String, val lat: Double, val lon: Double)

object WeatherRepo {
    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("sketch_weather", Context.MODE_PRIVATE)

    fun city(c: Context): String = prefs(c).getString("city", null) ?: "İstanbul"
    fun city2(c: Context): String = prefs(c).getString("city2", null) ?: ""

    private fun get(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 15000
        try {
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }

    fun hasLocationPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun useLocation(c: Context): Boolean = prefs(c).getBoolean("useLoc", hasLocationPermission(c))

    /** Cihazın bilinen son konumunu alır; izin yoksa ya da konum yoksa null. */
    fun lastKnown(c: Context): Location? {
        if (!hasLocationPermission(c)) return null
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val l = try { lm.getLastKnownLocation(prov) } catch (e: Exception) { null } ?: continue
            if (best == null || l.time > best.time) best = l
        }
        return best
    }

    /** Konumu kaydeder; şehir adını gerektiğinde yeniden bulur. */
    fun saveLocation(c: Context, loc: Location) {
        val p = prefs(c)
        val oldLat = p.getFloat("nameLat", 999f).toDouble()
        val oldLon = p.getFloat("nameLon", 999f).toDouble()
        val res = FloatArray(1)
        val far = oldLat > 900 || run {
            Location.distanceBetween(oldLat, oldLon, loc.latitude, loc.longitude, res); res[0] > 2000f
        }
        val e = p.edit().putFloat("lat", loc.latitude.toFloat()).putFloat("lon", loc.longitude.toFloat())
        if (far) {
            val a = try {
                @Suppress("DEPRECATION")
                Geocoder(c, Locale("tr", "TR")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            } catch (ex: Exception) { null }
            val name = a?.subLocality ?: a?.locality ?: a?.subAdminArea ?: a?.adminArea
            if (name != null) {
                val big = a?.locality ?: a?.adminArea
                e.putString("city", name).putString("city2", if (big != null && big != name) big else a?.adminArea ?: "")
                    .putFloat("nameLat", loc.latitude.toFloat()).putFloat("nameLon", loc.longitude.toFloat())
            }
        }
        e.apply()
    }

    /** Konumu taze almaya çalışır (en fazla 8 sn); olmazsa bilinen son konum. */
    fun freshLocation(c: Context): Location? {
        if (!hasLocationPermission(c)) return null
        val p = prefs(c)
        val last = p.getLong("locAt", 0L)
        if (System.currentTimeMillis() - last < 20 * 60 * 1000L) return lastKnown(c)
        var got: Location? = null
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                val latch = java.util.concurrent.CountDownLatch(1)
                val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
                lm.getCurrentLocation(prov, null, ex) { l -> got = l; latch.countDown() }
                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                ex.shutdown()
            } catch (e: Exception) { }
        }
        if (got != null) p.edit().putLong("locAt", System.currentTimeMillis()).apply()
        return got ?: lastKnown(c)
    }

    fun fetch(c: Context): Weather {
        if (useLocation(c)) freshLocation(c)?.let { saveLocation(c, it) }
        val p = prefs(c)
        val lat = p.getFloat("lat", 41.0082f)
        val lon = p.getFloat("lon", 28.9784f)
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,is_day,precipitation_probability&forecast_hours=13" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max" +
                "&minutely_15=precipitation&forecast_minutely_15=8&timezone=auto&forecast_days=6",
            lat, lon
        )
        var json = get(url)
        try {
            val aq = get(String.format(Locale.US, "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=european_aqi", lat, lon))
            val aqi = JSONObject(aq).optJSONObject("current")?.optDouble("european_aqi", -1.0) ?: -1.0
            if (aqi >= 0) json = JSONObject(json).put("aqi", aqi).toString()
        } catch (e: Exception) { }
        val w = parse(json)
        p.edit().putString("cache", json).putLong("fetched", System.currentTimeMillis()).apply()
        return w
    }

    private val HOME_COORDS = arrayOf(floatArrayOf(41.0082f, 28.9784f), floatArrayOf(51.5072f, -0.1276f), floatArrayOf(53.5511f, 9.9937f),
        floatArrayOf(25.2048f, 55.2708f), floatArrayOf(40.7128f, -74.006f), floatArrayOf(35.6762f, 139.6503f))

    /** Çift şehir widget'ı için ev şehrinin havası (yarım saatte bir). */
    fun fetchHome(c: Context) {
        val i = Style.home(c)
        val (lat, lon) = Pair(HOME_COORDS[i][0], HOME_COORDS[i][1])
        val url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon)
        try {
            val cur = JSONObject(get(url)).getJSONObject("current")
            prefs(c).edit().putInt("homeTemp", r(cur.getDouble("temperature_2m"))).putInt("homeCode", cur.getInt("weather_code"))
                .putBoolean("homeDay", cur.optInt("is_day", 1) == 1).putInt("homeIdx", i).apply()
        } catch (e: Exception) { }
    }

    fun cached(c: Context): Weather? {
        val s = prefs(c).getString("cache", null) ?: return null
        return try { parse(s).also { last = it } } catch (e: Exception) { null }
    }

    private fun r(x: Double): Int = Math.round(x).toInt()

    private fun parse(s: String): Weather {
        val o = JSONObject(s)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val t = d.getJSONArray("time")
        val wc = d.getJSONArray("weather_code")
        val mx = d.getJSONArray("temperature_2m_max")
        val mn = d.getJSONArray("temperature_2m_min")
        val days = (0 until t.length()).map {
            Day(t.getString(it), wc.getInt(it), r(mx.getDouble(it)), r(mn.getDouble(it)))
        }
        val hours = ArrayList<Hour>()
        val h = o.optJSONObject("hourly")
        if (h != null) {
            val ht = h.getJSONArray("time")
            val hc = h.getJSONArray("weather_code")
            val hd = h.getJSONArray("is_day")
            val hT = h.getJSONArray("temperature_2m")
            for (i in 0 until ht.length()) {
                hours.add(Hour(ht.getString(i).substring(11, 16), hc.getInt(i), hd.getInt(i) == 1, r(hT.getDouble(i))))
            }
        }
        return Weather(
            r(cur.getDouble("temperature_2m")),
            cur.getInt("weather_code"),
            cur.getInt("is_day") == 1,
            if (cur.has("apparent_temperature")) r(cur.getDouble("apparent_temperature")) else null,
            if (cur.has("relative_humidity_2m")) r(cur.getDouble("relative_humidity_2m")) else null,
            if (cur.has("wind_speed_10m")) r(cur.getDouble("wind_speed_10m")) else null,
            days, hours,
            d.optJSONArray("sunrise")?.optString(0)?.takeIf { it.length >= 16 }?.substring(11, 16),
            d.optJSONArray("sunset")?.optString(0)?.takeIf { it.length >= 16 }?.substring(11, 16),
            rainNext(o, d),
            d.optJSONArray("uv_index_max")?.let { if (it.length() > 0 && !it.isNull(0)) r(it.getDouble(0)) else null },
            if (o.has("aqi")) r(o.getDouble("aqi")) else null,
            o.optJSONObject("minutely_15")?.optJSONArray("precipitation")?.let { a -> (0 until a.length()).map { if (a.isNull(it)) 0f else a.getDouble(it).toFloat() } } ?: emptyList(),
            o.optJSONObject("minutely_15")?.optJSONArray("time")?.optString(0)?.takeIf { it.length >= 16 }?.substring(11, 16)
        ).also { last = it }
    }

    @Volatile var last: Weather? = null

    private fun rainNext(o: JSONObject, d: JSONObject): Int? {
        val hp = o.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
        if (hp != null && hp.length() > 0) {
            var m = 0
            for (i in 0 until minOf(6, hp.length())) if (!hp.isNull(i)) m = maxOf(m, hp.getInt(i))
            return m
        }
        val dp = d.optJSONArray("precipitation_probability_max") ?: return null
        return if (dp.length() > 0 && !dp.isNull(0)) dp.getInt(0) else null
    }

    fun aqiLabel(a: Int): String = when {
        a <= 20 -> "Çok iyi"; a <= 40 -> "İyi"; a <= 60 -> "Orta"; a <= 80 -> "Kötü"; else -> "Çok kötü"
    }

    fun uvLabel(u: Int): String = when {
        u <= 2 -> "Düşük"; u <= 5 -> "Orta"; u <= 7 -> "Yüksek"; u <= 10 -> "Çok yüksek"; else -> "Aşırı"
    }

    /** Havaya göre kutu rengi (arka plan, yazı koyu mu?) */
    fun conditionColor(w: Weather?): Pair<Int, Boolean> {
        if (w == null) return Pair(0xFF5A6B85.toInt(), false)
        return when (iconIndex(w.code, w.isDay)) {
            0 -> Pair(0xFFFF9A1F.toInt(), false)
            1 -> Pair(0xFF1E2B66.toInt(), false)
            2 -> Pair(0xFF2F86E8.toInt(), false)
            3 -> Pair(0xFF2A3A86.toInt(), false)
            4 -> Pair(0xFF66788F.toInt(), false)
            5 -> Pair(0xFF8C98A7.toInt(), false)
            6, 7 -> Pair(0xFF2257C9.toInt(), false)
            8 -> Pair(0xFF7FB3E8.toInt(), false)
            else -> Pair(0xFF5B34A8.toInt(), false)
        }
    }

    fun geocode(q: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=tr&name=" +
            URLEncoder.encode(q, "UTF-8")
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return null
        if (arr.length() == 0) return null
        val x = arr.getJSONObject(0)
        return Place(x.getString("name"), x.getDouble("latitude"), x.getDouble("longitude"))
    }

    // 0 güneş, 1 ay, 2 parçalı, 3 parçalı gece, 4 bulut, 5 sis, 6 çisenti, 7 yağmur, 8 kar, 9 fırtına
    fun iconIndex(code: Int, day: Boolean): Int = when (code) {
        0 -> if (day) 0 else 1
        1, 2 -> if (day) 2 else 3
        3 -> 4
        45, 48 -> 5
        in 51..57 -> 6
        in 61..67, in 80..82 -> 7
        in 71..77, 85, 86 -> 8
        in 95..99 -> 9
        else -> 4
    }

    fun label(code: Int): String = when (code) {
        0 -> "Açık"
        1 -> "Az bulutlu"
        2 -> "Parçalı bulutlu"
        3 -> "Kapalı"
        45, 48 -> "Sisli"
        in 51..57 -> "Çisenti"
        in 61..67 -> "Yağmurlu"
        in 71..77 -> "Karlı"
        in 80..82 -> "Sağanak"
        85, 86 -> "Kar sağanağı"
        in 95..99 -> "Fırtına"
        else -> ""
    }
}
