package com.serediuk.stairs.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.serediuk.stairs.calc.Flight
import com.serediuk.stairs.calc.StairCalculator
import kotlin.math.max
import kotlin.math.min

/** Draws a closed stringer (side board with tread seats) with a native canvas - used on screen and in the PDF. */
object StringerDrawer {

    fun draw(
        canvas: Canvas,
        left: Float, top: Float, width: Float, height: Float,
        flight: Flight, riser: Double, tread: Double,
        fillColor: Int, lineColor: Int, textColor: Int, textSize: Float,
        riserLabel: String, treadLabel: String, angleLabel: String
    ) {
        if (flight.treads < 1) return
        val k = flight.risers
        val above = StairCalculator.BOARD_ABOVE_MM
        val bh = StairCalculator.BOARD_HEIGHT_MM
        val slope = riser / tread
        val maxX = (k - 1) * tread
        fun topAt(x: Double) = riser + slope * x + above
        val maxY = topAt(maxX)
        val padL = textSize * 5f
        val pad = textSize * 1.3f

        val scale = min(
            (width - padL - pad) / maxX,
            (height - 2 * pad - textSize * 1.5f) / maxY
        ).toFloat()

        fun px(x: Double): Float = left + padL + (x * scale).toFloat()
        fun py(y: Double): Float = top + height - pad - (y * scale).toFloat()

        // board outline: plumb cut at the bottom, sloped top and bottom edges
        val xFloor = min(maxX, max(0.0, (bh - riser - above) / slope))
        val startY = max(0.0, topAt(0.0) - bh)
        val path = Path()
        path.moveTo(px(0.0), py(startY))
        path.lineTo(px(0.0), py(topAt(0.0)))
        path.lineTo(px(maxX), py(topAt(maxX)))
        path.lineTo(px(maxX), py(max(0.0, topAt(maxX) - bh)))
        if (xFloor > 0.0) path.lineTo(px(xFloor), py(0.0))
        path.close()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = lineColor
            strokeWidth = maxOf(1.5f, textSize * 0.12f)
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)

        // tread seats (where the steps sit between the boards)
        val seats = Path()
        val pts = flight.profile(riser, tread)
        seats.moveTo(px(pts[0].first), py(pts[0].second))
        for (i in 1 until pts.size) seats.lineTo(px(pts[i].first), py(pts[i].second))
        val seatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = lineColor
            strokeWidth = maxOf(1.2f, textSize * 0.09f)
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(textSize * 0.5f, textSize * 0.3f), 0f)
        }
        canvas.drawPath(seats, seatPaint)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize
        }
        tp.textAlign = Paint.Align.RIGHT
        canvas.drawText(riserLabel, px(0.0) - textSize * 0.4f, py(riser / 2) + textSize * 0.35f, tp)
        tp.textAlign = Paint.Align.CENTER
        canvas.drawText(treadLabel, px(tread / 2), py(topAt(tread / 2)) - textSize * 0.5f, tp)
        tp.textAlign = Paint.Align.LEFT
        canvas.drawText(angleLabel, left + padL * 0.3f, top + textSize * 1.4f, tp)
    }
}
