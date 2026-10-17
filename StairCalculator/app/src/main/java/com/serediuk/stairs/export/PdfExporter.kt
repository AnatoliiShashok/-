package com.serediuk.stairs.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.serediuk.stairs.R
import com.serediuk.stairs.calc.Advice
import com.serediuk.stairs.calc.Fmt
import com.serediuk.stairs.calc.LengthUnit
import com.serediuk.stairs.calc.StairResult
import com.serediuk.stairs.ui.AppInfo
import com.serediuk.stairs.ui.StringerDrawer
import com.serediuk.stairs.ui.TipsEngine
import com.serediuk.stairs.ui.typeRes
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private class PdfWriter(private val doc: PdfDocument, private val footer: String) {
    val pageW = 595
    val pageH = 842
    val margin = 40f
    private var pageNum = 0
    private lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = margin

    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = 0xFF777777.toInt()
    }

    init {
        startPage()
    }

    private fun startPage() {
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        canvas = page.canvas
        y = margin
    }

    private fun finishPage() {
        canvas.drawText(footer, margin, pageH - 20f, footerPaint)
        doc.finishPage(page)
    }

    fun ensure(height: Float) {
        if (y + height > pageH - margin - 10f) {
            finishPage()
            startPage()
        }
    }

    fun text(s: String, p: Paint) {
        ensure(p.textSize + 6f)
        y += p.textSize
        canvas.drawText(s, margin, y, p)
        y += 5f
    }

    fun wrapped(s: String, p: Paint) {
        val maxW = pageW - 2 * margin
        var line = ""
        for (word in s.split(" ")) {
            val cand = if (line.isEmpty()) word else line + " " + word
            if (p.measureText(cand) > maxW && line.isNotEmpty()) {
                text(line, p)
                line = word
            } else {
                line = cand
            }
        }
        if (line.isNotEmpty()) text(line, p)
    }

    fun gap(h: Float) {
        y += h
    }

    fun close() {
        finishPage()
    }
}

object PdfExporter {

    private fun paint(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun export(context: Context, r: StairResult, unit: LengthUnit): Uri {
        val doc = PdfDocument()
        val w = PdfWriter(doc, AppInfo.AUTHOR + " · " + context.getString(R.string.app_name))

        val title = paint(20f, true, 0xFF111111.toInt())
        val head = paint(13f, true, 0xFFD9480F.toInt())
        val body = paint(11f, false, 0xFF111111.toInt())
        val small = paint(10f, false, 0xFF555555.toInt())
        val ok = paint(11f, false, 0xFF2E7D32.toInt())
        val bad = paint(11f, false, 0xFFC92A2A.toInt())

        fun line(label: Int, value: String) = w.text(context.getString(label) + ": " + value, body)

        w.text(context.getString(R.string.pdf_title), title)
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        w.text(AppInfo.AUTHOR + " · " + df.format(Date()), small)
        w.gap(10f)

        w.text(context.getString(R.string.pdf_params), head)
        line(R.string.f_type, context.getString(typeRes(r.type)))
        line(R.string.f_height, Fmt.len(r.input.heightMm, unit))
        line(R.string.f_run, Fmt.len(r.input.runMm, unit))
        line(R.string.f_tread, Fmt.len(r.input.treadMm, unit))
        line(R.string.f_riser, Fmt.len(r.input.riserMm, unit))
        line(R.string.f_width, Fmt.len(r.input.widthMm, unit))
        if (r.thicknessMm > 0) line(R.string.f_thickness, Fmt.len(r.thicknessMm, unit))
        w.gap(10f)

        w.text(context.getString(R.string.sec_results), head)
        line(R.string.r_risers, r.risers.toString())
        line(R.string.r_treads, r.treadsTotal.toString())
        line(R.string.r_riser_real, Fmt.len(r.riserMm, unit))
        line(R.string.r_gap, Fmt.len(r.gapMm, unit))
        line(
            R.string.r_tread,
            if (kotlin.math.abs(r.tread2Mm - r.treadMm) > 0.5) Fmt.len(r.treadMm, unit) + " / " + Fmt.len(r.tread2Mm, unit)
            else Fmt.len(r.treadMm, unit)
        )
        line(R.string.r_angle, Fmt.angle(r.angleDeg))
        line(R.string.r_comfort, Fmt.len(r.comfortMm, unit))
        fun sg(mm: Double) = (if (mm >= 0) "+" else "−") + Fmt.len(kotlin.math.abs(mm), unit)
        line(R.string.r_dev, sg(r.heightErrMm) + " / " + sg(r.runErrMm))
        line(R.string.r_footprint, Fmt.len(r.footprintXMm, unit) + " × " + Fmt.len(r.footprintZMm, unit))
        line(R.string.r_stringer, Fmt.len(r.stringerSlopeMm, unit))
        line(R.string.r_handrail, Fmt.len(r.handrailMm, unit))
        w.gap(4f)
        for (a in r.advices) {
            val msg = when (a) {
                Advice.TREAD_ADJUSTED -> context.getString(
                    R.string.adv_tread_adjusted, Fmt.len(r.input.treadMm, unit), Fmt.len(r.treadMm, unit)
                )
                Advice.FITS -> context.getString(R.string.adv_fits, Fmt.len((r.input.runMm - r.footprintXMm).coerceAtLeast(0.0), unit))
                Advice.NOT_FITS -> context.getString(
                    R.string.adv_not_fits, Fmt.len(r.footprintXMm, unit), Fmt.len(r.input.runMm, unit)
                )
                Advice.COMFORT_OK -> context.getString(R.string.adv_comfort_ok, Fmt.num(r.comfortMm, 0) + " mm")
                Advice.COMFORT_BAD -> context.getString(R.string.adv_comfort_bad, Fmt.num(r.comfortMm, 0) + " mm")
                Advice.RISER_HIGH -> context.getString(R.string.adv_riser_high)
                Advice.RISER_LOW -> context.getString(R.string.adv_riser_low)
                Advice.TREAD_SHORT -> context.getString(R.string.adv_tread_short)
                Advice.ANGLE_STEEP -> context.getString(R.string.adv_angle_steep)
                Advice.ANGLE_SHALLOW -> context.getString(R.string.adv_angle_shallow)
                Advice.SMALL_STAIR -> context.getString(R.string.adv_small)
            }
            w.wrapped((if (a.ok) "[OK] " else "[!] ") + msg, if (a.ok) ok else bad)
        }
        w.gap(10f)


        // smart tips (in the app language)
        val tips = TipsEngine.build(r, unit)
        if (tips.isNotEmpty()) {
            w.text(context.getString(R.string.sec_tips), head)
            for (tip in tips) {
                w.wrapped("• " + context.getString(tip.res, *tip.args.toTypedArray()), body)
            }
            w.gap(10f)
        }

        // stringer drawings
        for ((index, flight) in r.flights.withIndex()) {
            if (flight.treads < 1) continue
            w.ensure(260f)
            w.gap(12f)
            val caption = context.getString(R.string.sec_stringer) +
                    if (r.flights.size > 1) " · " + context.getString(R.string.flight_n, index + 1) else ""
            w.text(caption, head)
            w.ensure(210f)
            StringerDrawer.draw(
                w.canvas, w.margin, w.y, 515f, 200f,
                flight, r.riserMm, flight.treadMm,
                0xFFE3C9A3.toInt(), 0xFF5A3E22.toInt(), 0xFF111111.toInt(), 9f,
                "h = " + Fmt.len(r.riserMm, unit),
                "b = " + Fmt.len(flight.treadMm, unit),
                "α = " + Fmt.angle(r.angleDeg)
            )
            w.gap(208f)
            w.text(context.getString(R.string.st_marks_title), small)
            flight.marks(r.riserMm, flight.treadMm).forEachIndexed { i, p ->
                w.text(
                    context.getString(R.string.st_row, i + 1, Fmt.len(p.first, unit), Fmt.len(p.second, unit)),
                    small
                )
            }
        }

        w.close()

        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "stairs_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
}
