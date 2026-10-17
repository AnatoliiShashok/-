package com.serediuk.stairs.calc

import kotlin.math.max

enum class SolidKind { TREAD, STRINGER, POST, RAIL, BASE, BRACKET }

/** Hexahedron. Vertex index = ix + 2*iy + 4*iz (in the solid's own local axes). */
class Solid(val v: Array<FloatArray>, val kind: SolidKind)

private typealias Frame = (Double, Double, Double) -> DoubleArray

/** Builds an industrial metal staircase: grating treads, side beams, posts and handrails. */
object Model3D {
    private const val STRINGER_H = 140.0
    private const val POST_S = 40.0
    private const val RAIL_H = 1000.0
    private const val RAIL_T = 60.0
    private const val MID_H = 500.0
    private const val MID_T = 16.0
    private const val SIDE_T = 40.0

    fun build(r: StairResult): List<Solid> {
        val out = ArrayList<Solid>()
        val d1 = r.flights[0].treadMm.takeIf { it > 0 } ?: 300.0
        val d2 = r.flights.getOrNull(1)?.treadMm?.takeIf { it > 0 } ?: d1
        val rr = r.riserMm
        val w = r.widthMm
        val n = r.risers
        val straight = r.type == StairType.STRAIGHT
        val m = r.flights[0].risers
        val topLen = max(1.5 * d2, 800.0)
        val tt = if (r.thicknessMm > 0) r.thicknessMm else 30.0

        val identity: Frame = { x, y, z -> doubleArrayOf(x, y, z) }
        if (straight) {
            flight(out, identity, 0.0, n, d1, rr, w, topLen, w, true, tt)
        } else {
            val run1 = (m - 1) * d1
            val platZ = if (r.type == StairType.U_SHAPED) 2 * w else w
            flight(out, identity, 0.0, m, d1, rr, w, w, platZ, false, tt)
            val second: Frame = if (r.type == StairType.L_SHAPED) {
                ({ lx, ly, lz -> doubleArrayOf(run1 + lz, ly, w + lx) })
            } else {
                ({ lx, ly, lz -> doubleArrayOf(run1 - lx, ly, w + lz) })
            }
            flight(out, second, m * rr, n - m, d2, rr, w, topLen, w, true, tt)
        }
        return out
    }

    private fun flight(
        out: MutableList<Solid>, t: Frame, y0: Double, k: Int,
        d: Double, rr: Double, w: Double,
        platLen: Double, platW: Double, top: Boolean, tt: Double
    ) {
        val steps = k - 1
        val slope = rr / d
        val end = steps * d
        val yTop = y0 + k * rr
        fun ref(x: Double): Double = if (x <= end) y0 + rr + slope * x else yTop

        // grating treads
        for (i in 1..steps) {
            box(out, t, SolidKind.TREAD, (i - 1) * d, i * d, max(y0, y0 + i * rr - tt), y0 + i * rr, SIDE_T, w - SIDE_T)
            // angle brackets under the tread, bolted to the inner face of each stringer
            val yb = y0 + i * rr - tt
            val bz = arrayOf(SIDE_T to (SIDE_T + 50.0), (w - SIDE_T - 50.0) to (w - SIDE_T))
            for (zb in bz) {
                val bb = max(y0, yb - 55.0)
                box(out, t, SolidKind.BRACKET, (i - 1) * d + 15.0, (i - 1) * d + 135.0, bb, max(bb + 1.0, yb), zb.first, zb.second)
            }
        }

        // foot plates on the floor under the stringers
        if (y0 <= 0.0 && steps > 0) {
            val fz = arrayOf((-POST_S - 20.0) to (SIDE_T + 20.0), (w - SIDE_T - 20.0) to (w + POST_S + 20.0))
            for (zp in fz) box(out, t, SolidKind.BASE, -160.0, 220.0, 0.0, 10.0, zp.first, zp.second)
        }

        // closed stringers: full-height boards running above the treads (one segment per step)
        val sideZ = arrayOf(0.0 to SIDE_T, (w - SIDE_T) to w)
        fun boardTop(x: Double) = y0 + rr + slope * x + StairCalculator.BOARD_ABOVE_MM
        fun boardBottom(x: Double) = max(y0, boardTop(x) - StairCalculator.BOARD_HEIGHT_MM)
        for (i in 1..steps) {
            val xa = (i - 1) * d
            val xb = i * d
            for (z in sideZ) {
                hex(out, t, SolidKind.STRINGER) { ix, iy, iz ->
                    val x = if (ix == 0) xa else xb
                    doubleArrayOf(
                        x,
                        if (iy == 0) boardBottom(x) else boardTop(x),
                        if (iz == 0) z.first else z.second
                    )
                }
            }
        }

        // platform / landing: grating slab on a solid base
        box(out, t, SolidKind.BASE, end, end + platLen, 0.0, yTop - tt, 0.0, platW)
        box(out, t, SolidKind.TREAD, end, end + platLen, yTop - tt, yTop, 0.0, platW)

        if (!top && steps == 0) return

        // posts and handrails on both sides
        // posts are bolted to the OUTER face of the stringers
        val postZ = arrayOf((-POST_S) to 0.0, w to (w + POST_S))
        val xs = ArrayList<Double>()
        var j = 0
        while (j < steps) {
            xs.add(j * d)
            j += 3
        }
        xs.add(end)

        for (z in postZ) {
            for (x in xs) {
                val bottom = max(y0, boardTop(x) - 40.0)
                box(out, t, SolidKind.POST, x - 20.0, x + 20.0, bottom, ref(x) + RAIL_H + RAIL_T, z.first, z.second)
            }
            for (i in 1..steps) {
                val xa = (i - 1) * d
                val xb = i * d
                hex(out, t, SolidKind.RAIL) { ix, iy, iz ->
                    val x = if (ix == 0) xa else xb
                    doubleArrayOf(x, ref(x) + RAIL_H + iy * RAIL_T, if (iz == 0) z.first else z.second)
                }
                hex(out, t, SolidKind.RAIL) { ix, iy, iz ->
                    val x = if (ix == 0) xa else xb
                    doubleArrayOf(x, ref(x) + MID_H + iy * MID_T, if (iz == 0) z.first + 12.0 else z.second - 12.0)
                }
            }
            if (top) {
                val xe = end + platLen
                box(out, t, SolidKind.RAIL, end, xe, yTop + RAIL_H, yTop + RAIL_H + RAIL_T, z.first, z.second)
                box(out, t, SolidKind.RAIL, end, xe, yTop + MID_H, yTop + MID_H + MID_T, z.first + 12.0, z.second - 12.0)
                box(out, t, SolidKind.POST, xe - POST_S, xe, yTop - tt, yTop + RAIL_H + RAIL_T, z.first, z.second)
            }
        }
    }

    private fun box(
        out: MutableList<Solid>, t: Frame, kind: SolidKind,
        x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double
    ) {
        hex(out, t, kind) { ix, iy, iz ->
            doubleArrayOf(if (ix == 0) x0 else x1, if (iy == 0) y0 else y1, if (iz == 0) z0 else z1)
        }
    }

    private fun hex(out: MutableList<Solid>, t: Frame, kind: SolidKind, c: (Int, Int, Int) -> DoubleArray) {
        val v = Array(8) { i ->
            val l = c(i and 1, (i shr 1) and 1, (i shr 2) and 1)
            val g = t(l[0], l[1], l[2])
            floatArrayOf(g[0].toFloat(), g[1].toFloat(), g[2].toFloat())
        }
        out.add(Solid(v, kind))
    }
}
