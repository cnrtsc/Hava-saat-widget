package com.caner.sketchweather

import android.app.Activity
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

        // Şehir
        root.addView(header("ŞEHİR", pad))
        val info = TextView(this).apply {
            text = WeatherRepo.city(this@MainActivity)
            textSize = 18f
            setTextColor(fg)
        }
        root.addView(info)
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
                            .putString("city", place.name)
                            .putFloat("lat", place.lat.toFloat())
                            .putFloat("lon", place.lon.toFloat())
                            .remove("cache")
                            .apply()
                        info.text = place.name
                        status.text = "Kaydedildi."
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
            text = "\nDört widget var: Şeffaf (5x2), Geniş (4x2), İnce (4x1) ve Kare (2x2). Eklemek için ana ekranda boş bir alana uzun bas → Widget'lar → Hava & Saat.\n\n" +
                "Saate dokun: alarmlar açılır.\nHava durumuna dokun: yenilenir.\nAlt satıra dokun: bu ayarlar açılır."
            setTextColor(muted)
            typeface = Typeface.DEFAULT
        })

        setContentView(scroll)
    }
}
