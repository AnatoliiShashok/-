package com.serediuk.stairs.calc

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Fmt {
    fun num(v: Double, digits: Int = 1): String {
        val pattern = if (digits <= 0) "0" else "0." + "#".repeat(digits)
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.getDefault())).format(v)
    }

    /** Value without the unit symbol, for text fields. */
    fun field(mm: Double, unit: LengthUnit): String = num(mm / unit.mmPerUnit, unit.digits)

    fun len(mm: Double, unit: LengthUnit): String = field(mm, unit) + " " + unit.symbol

    fun angle(deg: Double): String = num(deg, 1) + "°"
}
