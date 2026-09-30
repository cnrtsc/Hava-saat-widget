package com.caner.sketchweather

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var locStatus: TextView

    private val fg = Color.rgb(236, 240, 245)
    private val muted = Color.rgb(150, 160, 175)

    private fun refreshWidget() {
        sendBroadcast(Widgets.refreshIntent(this))
    }

    private fun header(text: String, pad: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(muted)
        letterSpacing = 0.08f
        setPadding(0, pad, 0, pad / 3)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = WeatherRepo.prefs(this)
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad * 2)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(22, 25, 31))
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = "Hava & Saat"
            textSize = 30f
            setTextColor(fg)
            typeface = Style.clockTypeface(this@MainActivity, 1)
        })

        // Konum
        root.addView(header("KONUM", pad))
        info = TextView(this).apply {
            text = WeatherRepo.city(this@MainActivity)
            textSize = 18f
            setTextColor(fg)
        }
        root.addView(info)
        val locBtn = Button(this).apply { text = "📍  Anlık konumumu kullan" }
        locStatus = TextView(this).apply { setTextColor(muted) }
        root.addView(locBtn)
        root.addView(locStatus)
        updateLocStatus()
        locBtn.setOnClickListener { askLocation() }
        root.addView(TextView(this).apply {
            text = "ya da şehir seç:"
            setTextColor(muted)
            setPadding(0, pad / 2, 0, 0)
        })
        val input = EditText(this).apply {
            hint = "Şehir ara (örn. Hamburg)"
            setSingleLine()
            setTextColor(fg)
            setHintTextColor(muted)
        }
        val save = Button(this).apply { text = "Şehri kaydet" }
        val status = TextView(this).apply { setTextColor(muted) }
        root.addView(input)
        root.addView(save)
        root.addView(status)
        save.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            status.text = "Aranıyor…"
            Thread {
                val place = try { WeatherRepo.geocode(q) } catch (e: Exception) { null }
                runOnUiThread {
                    if (place == null) {
                        status.text = "Şehir bulunamadı. Yazımı ve internet bağlantısını kontrol et."
                    } else {
                        p.edit()
                            .putBoolean("useLoc", false)
                            .putString("city", place.name)
                            .putFloat("lat", place.lat.toFloat())
                            .putFloat("lon", place.lon.toFloat())
                            .remove("cache")
                            .apply()
                        info.text = place.name
                        status.text = "Kaydedildi."
                        updateLocStatus()
                        refreshWidget()
                    }
                }
            }.start()
        }

        // Yazı tipi
        root.addView(header("SAAT YAZI TİPİ", pad))
        val fonts = RadioGroup(this)
        Style.FONT_NAMES.forEachIndexed { i, name ->
            fonts.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = "12:45   $name"
                textSize = 22f
                setTextColor(fg)
                typeface = Style.clockTypeface(this@MainActivity, i)
                isChecked = i == Style.font(this@MainActivity)
                setPadding(pad / 2, pad / 4, 0, pad / 4)
                setOnClickListener {
                    p.edit().putInt("font", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(fonts)

        // Geniş widget düzeni
        root.addView(header("GENİŞ WİDGET DÜZENİ", pad))
        val lays = RadioGroup(this)
        Style.WIDE_NAMES.forEachIndexed { i, name ->
            lays.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = 16f
                setTextColor(fg)
                isChecked = i == Style.wide(this@MainActivity)
                setPadding(pad / 2, pad / 4, 0, pad / 4)
                setOnClickListener {
                    p.edit().putInt("wide", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(lays)

        // İkon seti
        root.addView(header("HAVA İKONLARI", pad))
        val sets = RadioGroup(this)
        val iconPx = (40 * dp).toInt()
        Style.ICON_SET_NAMES.forEachIndexed { i, name ->
            sets.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = 17f
                setTextColor(fg)
                isChecked = i == Style.iconSet(this@MainActivity)
                setPadding(pad / 2, pad / 4, 0, pad / 4)
                val sample = intArrayOf(0, 2, 7, 9).map { k ->
                    resources.getDrawable(Style.ICONS[i][k], theme).apply { setBounds(0, 0, iconPx, iconPx) }
                }
                val strip = android.graphics.drawable.LayerDrawable(sample.toTypedArray())
                sample.forEachIndexed { k, _ -> strip.setLayerInset(k, k * iconPx, 0, (3 - k) * iconPx, 0) }
                strip.setBounds(0, 0, iconPx * 4, iconPx)
                setCompoundDrawables(null, null, strip, null)
                compoundDrawablePadding = pad
                setOnClickListener {
                    p.edit().putInt("icons", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(sets)

        // Arka plan
        root.addView(header("ARKA PLAN", pad))
        val bgs = RadioGroup(this)
        Style.BG_NAMES.forEachIndexed { i, name ->
            bgs.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = 17f
                setTextColor(fg)
                isChecked = i == Style.bg(this@MainActivity)
                setOnClickListener {
                    p.edit().putInt("bg", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(bgs)

        root.addView(TextView(this).apply {
            text = "\nDört widget var: Şeffaf 5x2, Geniş (4x2), İnce (4x1) ve Kare (2x2). Eklemek için ana ekranda boş bir alana uzun bas → Widget'lar → Hava & Saat.\n\n" +
                "Saate dokun: alarmlar açılır.\nHava durumuna dokun: yenilenir.\nAlt satıra dokun: bu ayarlar açılır."
            setTextColor(muted)
            typeface = Typeface.DEFAULT
        })

        setContentView(scroll)
    }

    private fun updateLocStatus() {
        locStatus.text = if (WeatherRepo.useLocation(this)) {
            if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                "Anlık konum açık. Widget arka planda da güncellensin diye konum iznini \"Her zaman izin ver\" yapman önerilir."
            else "Anlık konum açık; widget bulunduğun yere göre güncellenir."
        } else "Şu an seçili şehir kullanılıyor."
    }

    private fun askLocation() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (!WeatherRepo.hasLocationPermission(this)) {
                locStatus.text = "Konum izni verilmedi."
                return
            }
            WeatherRepo.prefs(this).edit().putBoolean("useLoc", true).putFloat("nameLat", 999f).apply()
            locStatus.text = "Konum alınıyor…"
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val done: (Location?) -> Unit = { loc ->
                Thread {
                    val l = loc ?: WeatherRepo.lastKnown(this)
                    if (l != null) WeatherRepo.saveLocation(this, l)
                    runOnUiThread {
                        info.text = WeatherRepo.city(this)
                        updateLocStatus()
                        if (l == null) locStatus.text = "Konum bulunamadı; telefonun konum servisinin açık olduğundan emin ol."
                        refreshWidget()
                        if (Build.VERSION.SDK_INT >= 29 &&
                            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
                        }
                    }
                }.start()
            }
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                    lm.getCurrentLocation(prov, null, mainExecutor) { done(it) }
                } else done(null)
            } catch (e: SecurityException) {
                done(null)
            }
        } else if (requestCode == 2) {
            updateLocStatus()
        }
    }
}
