package com.serediuk.stairs.ui

import android.graphics.Typeface
import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serediuk.stairs.calc.BoxKind
import com.serediuk.stairs.calc.Fmt
import com.serediuk.stairs.calc.LengthUnit
import com.serediuk.stairs.calc.Model3D
import com.serediuk.stairs.calc.Solid
import com.serediuk.stairs.calc.SolidKind
import com.serediuk.stairs.calc.StairResult
import com.serediuk.stairs.calc.StairType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private class Face(
    val pts: List<Offset>,
    val depth: Float,
    val color: Color,
    val lines: List<Pair<Offset, Offset>>
)

// quads of a hexahedron, vertex index = ix + 2*iy + 4*iz
private val FACES = arrayOf(
    intArrayOf(0, 2, 6, 4), // x0
    intArrayOf(1, 3, 7, 5), // x1
    intArrayOf(0, 1, 5, 4), // y0
    intArrayOf(2, 3, 7, 6), // y1 (top)
    intArrayOf(0, 1, 3, 2), // z0
    intArrayOf(4, 5, 7, 6)  // z1
)

private fun kindColor(k: SolidKind): Color = when (k) {
    SolidKind.TREAD -> Color(0xFF4A5058)
    SolidKind.STRINGER -> Color(0xFFE6E9ED)
    SolidKind.POST -> Color(0xFFDCE0E5)
    SolidKind.RAIL -> Color(0xFFD3D8DE)
    SolidKind.BASE -> Color(0xFFB4BBC4)
    SolidKind.BRACKET -> Color(0xFFF2F4F6)
}

@Composable
fun Stair3DView(r: StairResult, unit: LengthUnit, modifier: Modifier = Modifier) {
    var yaw by remember { mutableFloatStateOf(0.7f) }
    var pitch by remember { mutableFloatStateOf(0.45f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val solids = remember(r) { Model3D.build(r) }

    val bg = MaterialTheme.colorScheme.surfaceVariant
    val dimColor = MaterialTheme.colorScheme.primary
    val dimText = MaterialTheme.colorScheme.onPrimary
    val floorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    yaw += pan.x * 0.01f
                    pitch = (pitch + pan.y * 0.008f).coerceIn(0.05f, 1.45f)
                    zoom = (zoom * gestureZoom).coerceIn(0.4f, 4f)
                }
            }
    ) {
        drawStairs(r, solids, unit, yaw, pitch, zoom, dimColor, dimText, floorColor)
    }
}

private fun DrawScope.drawStairs(
    r: StairResult,
    solids: List<Solid>,
    unit: LengthUnit,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    dimColor: Color,
    dimTextColor: Color,
    floorColor: Color
) {
    // bounding box of the whole model
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var maxY = 0f
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    for (s in solids) for (p in s.v) {
        minX = min(minX, p[0]); maxX = max(maxX, p[0])
        maxY = max(maxY, p[1])
        minZ = min(minZ, p[2]); maxZ = max(maxZ, p[2])
    }
    val cx = (minX + maxX) / 2f
    val cy = maxY / 2f
    val cz = (minZ + maxZ) / 2f
    val dx = maxX - minX
    val dz = maxZ - minZ
    val radius = sqrt(dx * dx + maxY * maxY + dz * dz) / 2f
    val scale = min(size.width, size.height) * 0.36f / radius * zoom

    val cY = cos(yaw)
    val sY = sin(yaw)
    val cP = cos(pitch)
    val sP = sin(pitch)

    fun rot(x: Float, y: Float, z: Float): FloatArray {
        val x1 = x * cY + z * sY
        val z1 = -x * sY + z * cY
        val y2 = y * cP - z1 * sP
        val z2 = y * sP + z1 * cP
        return floatArrayOf(x1, y2, z2)
    }

    fun scr(p: FloatArray) = Offset(size.width / 2f + p[0] * scale, size.height / 2f - p[1] * scale)
    fun proj(x: Float, y: Float, z: Float) = scr(rot(x - cx, y - cy, z - cz))

    // floor
    val fp = 0.15f * max(dx, dz)
    val a = proj(minX - fp, 0f, minZ - fp)
    val b = proj(maxX + fp, 0f, minZ - fp)
    val c = proj(maxX + fp, 0f, maxZ + fp)
    val d = proj(minX - fp, 0f, maxZ + fp)
    val floor = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    drawPath(floor, floorColor)

    // faces
    val faces = ArrayList<Face>()
    val light = floatArrayOf(-0.3f, 0.7f, 0.65f)
    for (s in solids) {
        val base = kindColor(s.kind)
        var scx = 0f; var scy = 0f; var scz = 0f
        for (p in s.v) { scx += p[0]; scy += p[1]; scz += p[2] }
        scx /= 8f; scy /= 8f; scz /= 8f

        for (fi in FACES.indices) {
            val q = FACES[fi]
            val p0 = s.v[q[0]]; val p1 = s.v[q[1]]; val p2 = s.v[q[2]]
            // normal from geometry, flipped to point out of the solid
            var nx = (p1[1] - p0[1]) * (p2[2] - p0[2]) - (p1[2] - p0[2]) * (p2[1] - p0[1])
            var ny = (p1[2] - p0[2]) * (p2[0] - p0[0]) - (p1[0] - p0[0]) * (p2[2] - p0[2])
            var nz = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p1[1] - p0[1]) * (p2[0] - p0[0])
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len < 1e-6f) continue
            nx /= len; ny /= len; nz /= len
            var fcx = 0f; var fcy = 0f; var fcz = 0f
            for (idx in q) { fcx += s.v[idx][0]; fcy += s.v[idx][1]; fcz += s.v[idx][2] }
            fcx /= 4f; fcy /= 4f; fcz /= 4f
            if (nx * (fcx - scx) + ny * (fcy - scy) + nz * (fcz - scz) < 0f) {
                nx = -nx; ny = -ny; nz = -nz
            }
            val rn = rot(nx, ny, nz)
            if (rn[2] <= 0.001f) continue

            var depth = 0f
            val pts = q.map { idx ->
                val v = s.v[idx]
                val pr = rot(v[0] - cx, v[1] - cy, v[2] - cz)
                depth += pr[2]
                scr(pr)
            }
            val shade = 0.55f + 0.45f * max(0f, rn[0] * light[0] + rn[1] * light[1] + rn[2] * light[2])

            // grating stripes on top of treads / platforms
            val lines = ArrayList<Pair<Offset, Offset>>()
            if (s.kind == SolidKind.TREAD && fi == 3) {
                for (k in 1..9) {
                    val t = k / 10f
                    val l0 = Offset(pts[0].x + (pts[1].x - pts[0].x) * t, pts[0].y + (pts[1].y - pts[0].y) * t)
                    val l1 = Offset(pts[3].x + (pts[2].x - pts[3].x) * t, pts[3].y + (pts[2].y - pts[3].y) * t)
                    lines.add(l0 to l1)
                }
                for (k in 1..5) {
                    val t = k / 6f
                    val l0 = Offset(pts[0].x + (pts[3].x - pts[0].x) * t, pts[0].y + (pts[3].y - pts[0].y) * t)
                    val l1 = Offset(pts[1].x + (pts[2].x - pts[1].x) * t, pts[1].y + (pts[2].y - pts[1].y) * t)
                    lines.add(l0 to l1)
                }
            }
            faces.add(
                Face(pts, depth / 4f, Color(base.red * shade, base.green * shade, base.blue * shade), lines)
            )
        }
    }
    for (face in faces.sortedBy { it.depth }) {
        val path = Path().apply {
            moveTo(face.pts[0].x, face.pts[0].y)
            for (i in 1 until face.pts.size) lineTo(face.pts[i].x, face.pts[i].y)
            close()
        }
        drawPath(path, face.color)
        for (l in face.lines) drawLine(Color(0x33000000), l.first, l.second, strokeWidth = 1.2f)
        drawPath(path, Color(0x55000000), style = Stroke(width = 0.8f))
    }

    // dimensions
    val textPaint = NativePaint().apply {
        isAntiAlias = true
        textSize = 11.sp.toPx()
        textAlign = NativePaint.Align.CENTER
        color = dimTextColor.toArgb()
        typeface = Typeface.DEFAULT_BOLD
    }
    val bgPaint = NativePaint().apply {
        isAntiAlias = true
        color = dimColor.toArgb()
    }

    fun dim(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        text: String, ox: Float = 0f, oy: Float = 0f
    ) {
        val pa = proj(ax, ay, az)
        val pb = proj(bx, by, bz)
        drawLine(dimColor, pa, pb, strokeWidth = 2.5f)
        drawCircle(dimColor, 4f, pa)
        drawCircle(dimColor, 4f, pb)
        val mx = (pa.x + pb.x) / 2f + ox
        val my = (pa.y + pb.y) / 2f + oy
        val tw = textPaint.measureText(text)
        val th = textPaint.textSize
        val nc = drawContext.canvas.nativeCanvas
        nc.drawRoundRect(mx - tw / 2f - 8f, my - th / 2f - 5f, mx + tw / 2f + 8f, my + th / 2f + 5f, 10f, 10f, bgPaint)
        nc.drawText(text, mx, my + th * 0.35f, textPaint)
    }

    val stair = r.boxes.filter { it.kind != BoxKind.TOP }
    val sx0 = stair.minOf { it.x0 }.toFloat()
    val sx1 = stair.maxOf { it.x1 }.toFloat()
    val off = 0.09f * max(dx, dz) + 120f
    val zf = maxZ + off
    val riser = r.riserMm.toFloat()
    val tread = r.treadMm.toFloat()

    dim(sx1, 0f, zf, sx1, r.heightMm.toFloat(), zf, "H " + Fmt.len(r.heightMm, unit))
    dim(sx0, 0f, zf, sx1, 0f, zf, "L " + Fmt.len((sx1 - sx0).toDouble(), unit))
    dim(minX - off, 0f, 0f, minX - off, 0f, r.widthMm.toFloat(), "W " + Fmt.len(r.widthMm, unit))
    if (r.type == StairType.L_SHAPED) {
        dim(sx1 + off, 0f, 0f, sx1 + off, 0f, r.footprintZMm.toFloat(), "L2 " + Fmt.len(r.footprintZMm, unit))
    }
    val e = maxZ + off * 0.35f
    dim(0f, 0f, e, 0f, riser, e, "h " + Fmt.len(r.riserMm, unit), ox = -30f)
    dim(0f, riser, e, tread, riser, e, "b " + Fmt.len(r.treadMm, unit), oy = -24f)
}
