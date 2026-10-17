package com.serediuk.stairs.data

import android.content.Context
import com.serediuk.stairs.calc.StairInput
import com.serediuk.stairs.calc.StairType
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("stairs_prefs", Context.MODE_PRIVATE)

    var language: String
        get() {
            val saved = sp.getString("lang", null)
            if (saved != null) return saved
            val def = if (Locale.getDefault().language == "es") "es" else "uk"
            sp.edit().putString("lang", def).apply()
            return def
        }
        set(v) = sp.edit().putString("lang", v).apply()

    /** "system", "light" or "dark" */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    /** LengthUnit name */
    var unit: String
        get() = sp.getString("unit", "CM") ?: "CM"
        set(v) = sp.edit().putString("unit", v).apply()

    fun saveInput(i: StairInput) {
        sp.edit()
            .putString("in_type", i.type.name)
            .putString("in_h", i.heightMm.toString())
            .putString("in_run", i.runMm.toString())
            .putString("in_tread", i.treadMm.toString())
            .putString("in_riser", i.riserMm.toString())
            .putString("in_w", i.widthMm.toString())
            .apply()
    }

    fun loadInput(): StairInput {
        fun d(key: String, def: Double) = sp.getString(key, null)?.toDoubleOrNull() ?: def
        val type = try {
            StairType.valueOf(sp.getString("in_type", "STRAIGHT") ?: "STRAIGHT")
        } catch (e: IllegalArgumentException) {
            StairType.STRAIGHT
        }
        return StairInput(
            type = type,
            heightMm = d("in_h", 2700.0),
            runMm = d("in_run", 4000.0),
            treadMm = d("in_tread", 285.0),
            riserMm = d("in_riser", 170.0),
            widthMm = d("in_w", 900.0)
        )
    }
}
