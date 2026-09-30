package com.caner.sketchweather

import android.content.Context

/**
 * Ayar okuma: önce o an çizilen widget'a özel değer ("w<id>_anahtar"), yoksa genel değer.
 * Böylece her widget kendi saydamlığını, yazı rengini, yazı tipini vb. taşıyabilir.
 */
object Cfg {
    private val cur = ThreadLocal<Int?>()

    fun <T> with(id: Int?, block: () -> T): T {
        val prev = cur.get()
        cur.set(id)
        try { return block() } finally { cur.set(prev) }
    }

    fun current(): Int? = cur.get()
    fun wkey(id: Int, key: String) = "w${id}_$key"

    fun int(c: Context, key: String, def: Int): Int {
        val p = WeatherRepo.prefs(c)
        val id = cur.get()
        if (id != null && p.contains(wkey(id, key))) return p.getInt(wkey(id, key), def)
        return p.getInt(key, def)
    }

    fun bool(c: Context, key: String, def: Boolean): Boolean {
        val p = WeatherRepo.prefs(c)
        val id = cur.get()
        if (id != null && p.contains(wkey(id, key))) return p.getBoolean(wkey(id, key), def)
        return p.getBoolean(key, def)
    }
}
