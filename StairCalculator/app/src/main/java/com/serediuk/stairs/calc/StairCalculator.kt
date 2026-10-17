package com.serediuk.stairs.calc

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

enum class StairType { STRAIGHT, L_SHAPED, U_SHAPED }

enum class LengthUnit(val symbol: String, val mmPerUnit: Double, val digits: Int) {
    MM("mm", 1.0, 0),
    CM("cm", 10.0, 1),
    IN("in", 25.4, 2)
}

/** All values in millimetres. */
data class StairInput(
    val type: StairType,
    val heightMm: Double,
    val runMm: Double,
    val treadMm: Double,
    val riserMm: Double,
    val widthMm: Double,
    val thicknessMm: Double = 0.0,
    val fitLength: Boolean = false,
    /** L-shaped only: available length of the second direction (0 = same as the first) */
    val run2Mm: Double = 0.0
)

enum class BoxKind { STEP, LANDING, TOP }

data class Box(
    val x0: Double, val x1: Double,
    val y0: Double, val y1: Double,
    val z0: Double, val z1: Double,
    val kind: BoxKind = BoxKind.STEP
)

data class Flight(val risers: Int, val treads: Int, val riseMm: Double, val runMm: Double) {
    val slopeMm: Double get() = hypot(riseMm, runMm)

    /** Tread depth of this flight */
    val treadMm: Double get() = if (treads > 0) runMm / treads else 0.0

    /** Saw-tooth outline of the stringer, origin = lower inner corner. */
    fun profile(riser: Double, tread: Double): List<Pair<Double, Double>> {
        val pts = ArrayList<Pair<Double, Double>>()
        pts.add(0.0 to 0.0)
        for (i in 1..risers) {
            pts.add((i - 1) * tread to i * riser)
            if (i < risers) pts.add(i * tread to i * riser)
        }
        return pts
    }

    /** Marking points: corner of every step. */
    fun marks(riser: Double, tread: Double): List<Pair<Double, Double>> =
        (1..risers).map { ((it - 1) * tread) to (it * riser) }
}

enum class Advice(val ok: Boolean) {
    TREAD_ADJUSTED(true),
    FITS(true),
    NOT_FITS(false),
    COMFORT_OK(true),
    COMFORT_BAD(false),
    RISER_HIGH(false),
    RISER_LOW(false),
    TREAD_SHORT(false),
    ANGLE_STEEP(false),
    ANGLE_SHALLOW(false),
    SMALL_STAIR(false)
}

data class Materials(
    val treadBoards: Int,
    val treadBoardLenMm: Double,
    val treadBoardWidthMm: Double,
    val treadThicknessMm: Double,
    val riserBoards: Int,
    val riserBoardHeightMm: Double,
    val riserThicknessMm: Double,
    val stringers: Int,
    val stringerBoardMm: Double,
    val landingAreaMm2: Double,
    val volumeM3: Double,
    val fasteners: Int
)

data class StairResult(
    val input: StairInput,
    val type: StairType,
    val heightMm: Double,
    val widthMm: Double,
    val risers: Int,
    val treadsTotal: Int,
    val riserMm: Double,
    val treadMm: Double,
    val tread2Mm: Double,
    val angleDeg: Double,
    val comfortMm: Double,
    val footprintXMm: Double,
    val footprintZMm: Double,
    val fits: Boolean,
    val advices: List<Advice>,
    val flights: List<Flight>,
    val boxes: List<Box>,
    val materials: Materials,
    val stringerSlopeMm: Double,
    val handrailMm: Double,
    val thicknessMm: Double,
    val gapMm: Double,
    val heightErrMm: Double,
    val runErrMm: Double
)

object StairCalculator {
    const val NOSING_MM = 30.0
    const val TREAD_THICKNESS_MM = 40.0
    const val RISER_THICKNESS_MM = 20.0
    const val STRINGER_MARGIN_MM = 300.0
    /** Closed stringer: board edge above the tread nose line, and board height (vertical) */
    const val BOARD_ABOVE_MM = 60.0
    const val BOARD_HEIGHT_MM = 320.0
    /** Allowed deviation of total height / length: 1 cm */
    const val TOLERANCE_MM = 10.0

    fun calculate(inp: StairInput): StairResult? {
        if (inp.heightMm <= 0 || inp.runMm <= 0 || inp.widthMm <= 0) return null

        val h = inp.heightMm
        val w = inp.widthMm
        val tol = TOLERANCE_MM
        val type = inp.type
        val straight = type == StairType.STRAIGHT
        val lShape = type == StairType.L_SHAPED

        // Height and length(s) are fixed. Run available for the treads of each flight:
        val a1 = inp.runMm - (if (straight) 0.0 else w)
        val a2 = if (lShape && inp.run2Mm > 0) inp.run2Mm - w else a1
        if (a1 <= 0 || a2 <= 0) return null

        fun nOf(t1: Int, t2: Int) = if (straight) t1 + 1 else t1 + t2 + 2

        // candidate number of treads per flight; for an L stair c = total treads split by the flight lengths
        fun candidate(c: Int): Pair<Int, Int> =
            if (!lShape) {
                c to c
            } else {
                val t1 = (c * a1 / (a1 + a2)).roundToInt().coerceIn(1, c - 1)
                t1 to (c - t1)
            }

        // entered riser > entered tread > best comfort (2h+b = 630 mm)
        val pair: Pair<Int, Int> = when {
            inp.riserMm > 0 -> {
                val nn = max(2, (h / inp.riserMm).roundToInt())
                val c = when {
                    straight -> nn - 1
                    lShape -> max(2, nn - 2)
                    else -> max(1, ((nn - 2) / 2.0).roundToInt())
                }
                candidate(c)
            }
            inp.treadMm > 0 -> {
                val t1 = max(1, (a1 / inp.treadMm).roundToInt())
                if (lShape) t1 to max(1, (a2 / inp.treadMm).roundToInt()) else t1 to t1
            }
            else -> {
                var best = candidate(if (lShape) 2 else 1)
                var bestScore = Double.MAX_VALUE
                var c = if (lShape) 2 else 1
                while (c <= 120) {
                    val p = candidate(c)
                    val nn = nOf(p.first, p.second)
                    if (nn > 120) break
                    val rr = h / nn
                    var score = abs(2 * rr + a1 / p.first - 630.0)
                    if (lShape) score += abs(2 * rr + a2 / p.second - 630.0)
                    if (score < bestScore) {
                        bestScore = score
                        best = p
                    }
                    c++
                }
                best
            }
        }
        val t1 = pair.first
        val t2 = pair.second
        val n = nOf(t1, t2)
        if (n > 120) return null

        val rExact = h / n
        val rRound = Math.round(rExact).toDouble()
        // prefer the entered / whole-millimetre value when the total stays within +-1 cm
        val r = when {
            inp.riserMm > 0 && abs(n * inp.riserMm - h) <= tol -> inp.riserMm
            abs(n * rRound - h) <= tol -> rRound
            else -> rExact
        }
        fun pick(t: Int, avail: Double): Double {
            val exact = avail / t
            val rounded = Math.round(exact).toDouble()
            return when {
                inp.treadMm > 0 && abs(t * inp.treadMm - avail) <= tol -> inp.treadMm
                abs(t * rounded - avail) <= tol -> rounded
                else -> exact
            }
        }
        val d1 = pick(t1, a1)
        val d2 = if (lShape) pick(t2, a2) else d1
        val hAct = n * r

        val advices = ArrayList<Advice>()
        val m = t1 + 1
        val flights: List<Flight> = if (straight) {
            listOf(Flight(n, t1, n * r, t1 * d1))
        } else {
            listOf(
                Flight(m, t1, m * r, t1 * d1),
                Flight(n - m, t2, (n - m) * r, t2 * d2)
            )
        }

        // ---- 3D blocks (x = direction of the first flight, y = up, z = width) ----
        val boxes = ArrayList<Box>()
        val run1 = t1 * d1
        val run2 = t2 * d2
        when (type) {
            StairType.STRAIGHT -> {
                for (i in 1..t1) boxes.add(Box((i - 1) * d1, i * d1, 0.0, i * r, 0.0, w))
                boxes.add(Box(run1, run1 + 1.5 * d1, 0.0, hAct, 0.0, w, BoxKind.TOP))
            }
            StairType.L_SHAPED -> {
                for (i in 1..t1) boxes.add(Box((i - 1) * d1, i * d1, 0.0, i * r, 0.0, w))
                boxes.add(Box(run1, run1 + w, 0.0, m * r, 0.0, w, BoxKind.LANDING))
                for (j in 1..t2) {
                    boxes.add(Box(run1, run1 + w, 0.0, (m + j) * r, w + (j - 1) * d2, w + j * d2))
                }
                boxes.add(Box(run1, run1 + w, 0.0, hAct, w + run2, w + run2 + 1.5 * d2, BoxKind.TOP))
            }
            StairType.U_SHAPED -> {
                for (i in 1..t1) boxes.add(Box((i - 1) * d1, i * d1, 0.0, i * r, 0.0, w))
                boxes.add(Box(run1, run1 + w, 0.0, m * r, 0.0, 2 * w, BoxKind.LANDING))
                for (j in 1..t2) {
                    boxes.add(Box(run1 - j * d2, run1 - (j - 1) * d2, 0.0, (m + j) * r, w, 2 * w))
                }
                boxes.add(Box(run1 - run2 - 1.5 * d2, run1 - run2, 0.0, hAct, w, 2 * w, BoxKind.TOP))
            }
        }

        val stair = boxes.filter { it.kind != BoxKind.TOP }
        val footX = stair.maxOf { it.x1 } - stair.minOf { it.x0 }
        val footZ = stair.maxOf { it.z1 } - stair.minOf { it.z0 }
        val limZ = if (lShape && inp.run2Mm > 0) inp.run2Mm else inp.runMm
        val fits = footX <= inp.runMm + tol && (!lShape || footZ <= limZ + tol)

        // slope: total rise / total run of each flight (H / L for a straight stair); the steepest one is reported
        var angle = 0.0
        for (f in flights) {
            val a = if (f.runMm > 1.0) Math.toDegrees(atan2(f.riseMm, f.runMm))
            else Math.toDegrees(atan2(r, f.treadMm.takeIf { it > 0 } ?: d1))
            if (a > angle) angle = a
        }
        val c1 = 2 * r + d1
        val c2 = 2 * r + d2
        val comfort = if (abs(c1 - 630.0) >= abs(c2 - 630.0)) c1 else c2
        val dMin = if (d2 < d1) d2 else d1

        if (inp.treadMm > 0 && inp.riserMm <= 0 &&
            (abs(d1 - inp.treadMm) > 0.5 || abs(d2 - inp.treadMm) > 0.5)
        ) advices.add(Advice.TREAD_ADJUSTED)
        advices.add(if (fits) Advice.FITS else Advice.NOT_FITS)
        advices.add(if (comfort in 600.0..640.0) Advice.COMFORT_OK else Advice.COMFORT_BAD)
        if (r > 190.0) advices.add(Advice.RISER_HIGH)
        if (r < 150.0) advices.add(Advice.RISER_LOW)
        if (dMin < 250.0) advices.add(Advice.TREAD_SHORT)
        if (angle > 42.0) advices.add(Advice.ANGLE_STEEP)
        if (angle < 23.0) advices.add(Advice.ANGLE_SHALLOW)

        // ---- materials (not shown in the UI) ----
        val treadCount = flights.sumOf { it.treads }
        val landingArea = when (type) {
            StairType.STRAIGHT -> 0.0
            StairType.L_SHAPED -> w * w
            StairType.U_SHAPED -> w * 2 * w
        }
        val perFlight = if (w > 900.0) 3 else 2
        val slope = flights.maxOf { it.slopeMm }
        val volumeMm3 = treadCount * w * (d1 + NOSING_MM) * TREAD_THICKNESS_MM +
                n * w * r * RISER_THICKNESS_MM +
                landingArea * TREAD_THICKNESS_MM
        val materials = Materials(
            treadBoards = treadCount,
            treadBoardLenMm = w,
            treadBoardWidthMm = d1 + NOSING_MM,
            treadThicknessMm = TREAD_THICKNESS_MM,
            riserBoards = n,
            riserBoardHeightMm = r,
            riserThicknessMm = RISER_THICKNESS_MM,
            stringers = perFlight * flights.size,
            stringerBoardMm = slope + STRINGER_MARGIN_MM,
            landingAreaMm2 = landingArea,
            volumeM3 = volumeMm3 / 1.0e9,
            fasteners = (treadCount + n) * perFlight * 2
        )

        return StairResult(
            input = inp,
            type = type,
            heightMm = hAct,
            widthMm = w,
            risers = n,
            treadsTotal = treadCount,
            riserMm = r,
            treadMm = d1,
            tread2Mm = d2,
            angleDeg = angle,
            comfortMm = comfort,
            footprintXMm = footX,
            footprintZMm = footZ,
            fits = fits,
            advices = advices,
            flights = flights,
            boxes = boxes,
            materials = materials,
            stringerSlopeMm = slope,
            handrailMm = flights.sumOf { it.slopeMm },
            thicknessMm = inp.thicknessMm,
            gapMm = if (inp.thicknessMm > 0) r - inp.thicknessMm else r,
            heightErrMm = hAct - h,
            runErrMm = footX - inp.runMm
        )
    }
}
