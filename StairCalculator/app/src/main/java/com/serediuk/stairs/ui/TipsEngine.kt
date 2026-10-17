package com.serediuk.stairs.ui

import androidx.annotation.StringRes
import com.serediuk.stairs.R
import com.serediuk.stairs.calc.Fmt
import com.serediuk.stairs.calc.LengthUnit
import com.serediuk.stairs.calc.StairResult
import com.serediuk.stairs.calc.StairType
import kotlin.math.tan

/** A hint: string resource + already formatted arguments. The text is resolved in the app language. */
class TipItem(@StringRes val res: Int, val args: List<String>, val problem: Boolean)

/** Offline "smart hints" built from the calculation result. */
object TipsEngine {

    fun build(r: StairResult, unit: LengthUnit): List<TipItem> {
        val tips = ArrayList<TipItem>()
        fun len(mm: Double) = Fmt.len(mm, unit)
        val straight = r.type == StairType.STRAIGHT
        val h = r.heightMm
        val angle = r.angleDeg

        if (straight && angle > 42.0) {
            tips.add(
                TipItem(
                    R.string.tip_steep,
                    listOf(Fmt.angle(angle), len(h / tan(Math.toRadians(35.0))), len(r.input.runMm)),
                    true
                )
            )
        }
        if (straight && angle < 23.0) {
            tips.add(
                TipItem(
                    R.string.tip_shallow,
                    listOf(Fmt.angle(angle), len(h / tan(Math.toRadians(30.0)))),
                    true
                )
            )
        }
        if (straight && r.riserMm > 190.0) {
            tips.add(
                TipItem(
                    R.string.tip_riser_high,
                    listOf(len(r.riserMm), len(h / (r.risers + 1)), len(r.treadMm)),
                    true
                )
            )
        }
        if (straight && r.riserMm < 150.0 && r.risers > 2) {
            tips.add(
                TipItem(
                    R.string.tip_riser_low,
                    listOf(len(r.riserMm), len(h / (r.risers - 1)), len(r.treadMm)),
                    true
                )
            )
        }
        if (r.comfortMm < 600.0 || r.comfortMm > 640.0) {
            val b = 630.0 - 2 * r.riserMm
            if (b in 200.0..400.0) {
                tips.add(
                    TipItem(
                        R.string.tip_comfort,
                        listOf(Fmt.num(r.comfortMm, 0) + " mm", len(r.riserMm), len(b)),
                        true
                    )
                )
            }
        }
        if (r.thicknessMm > 0 && r.gapMm > 150.0) {
            tips.add(TipItem(R.string.tip_gap, listOf(len(r.gapMm)), true))
        }
        if (r.widthMm < 700.0 || r.widthMm > 1000.0) {
            tips.add(TipItem(R.string.tip_width, listOf(len(r.widthMm)), true))
        }

        if (!straight) {
            val second = if (r.type == StairType.U_SHAPED) 2 * r.widthMm else r.widthMm
            tips.add(
                TipItem(
                    R.string.tip_landing,
                    listOf(len(r.widthMm), len(second), len(r.flights[0].riseMm)),
                    false
                )
            )
        }
        if (r.input.treadMm <= 0 && r.input.riserMm <= 0) {
            tips.add(TipItem(R.string.tip_auto_tread, listOf(len(r.treadMm)), false))
        }
        if (tips.none { it.problem } && r.advices.all { it.ok }) {
            tips.add(
                TipItem(
                    R.string.tip_ok,
                    listOf(Fmt.angle(angle), Fmt.num(r.comfortMm, 0) + " mm"),
                    false
                )
            )
        }
        return tips.sortedByDescending { it.problem }
    }
}
