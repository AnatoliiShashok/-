@file:OptIn(ExperimentalMaterial3Api::class)

package com.serediuk.stairs.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serediuk.stairs.R
import com.serediuk.stairs.calc.Advice
import com.serediuk.stairs.calc.Flight
import com.serediuk.stairs.calc.Fmt
import com.serediuk.stairs.calc.LengthUnit
import com.serediuk.stairs.calc.StairResult
import com.serediuk.stairs.calc.StairType
import com.serediuk.stairs.export.PdfExporter

@Composable
fun CalculatorScreen(vm: StairsViewModel) {
    val input by vm.input.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val invalid by vm.invalid.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val unit = input.unit

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // stair type
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StairType.entries.forEach { t ->
                FilterChip(
                    selected = input.type == t,
                    onClick = { vm.setType(t) },
                    label = { Text(stringResource(typeRes(t)), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // units
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LengthUnit.entries.forEach { u ->
                FilterChip(
                    selected = unit == u,
                    onClick = { vm.setUnit(u) },
                    label = { Text(u.symbol) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        NumField(stringResource(R.string.f_height), input.height, unit.symbol) { vm.update(Field.HEIGHT, it) }
        NumField(stringResource(R.string.f_run), input.run, unit.symbol) { vm.update(Field.RUN, it) }
        if (input.type == StairType.L_SHAPED) {
            NumField(stringResource(R.string.f_run2), input.run2, unit.symbol) { vm.update(Field.RUN2, it) }
        }
        NumField(stringResource(R.string.f_tread), input.tread, unit.symbol) { vm.update(Field.TREAD, it) }
        NumField(stringResource(R.string.f_thickness), input.thickness, unit.symbol) { vm.update(Field.THICKNESS, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(38.0, 40.0).forEach { mm ->
                FilterChip(
                    selected = false,
                    onClick = { vm.update(Field.THICKNESS, Fmt.field(mm, unit)) },
                    label = { Text(Fmt.len(mm, unit)) }
                )
            }
        }
        NumField(stringResource(R.string.f_width), input.width, unit.symbol) { vm.update(Field.WIDTH, it) }
        NumField(stringResource(R.string.f_riser), input.riser, unit.symbol) { vm.update(Field.RISER, it) }

        Button(
            onClick = {
                focusManager.clearFocus()
                vm.calculate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.calc_button))
        }

        val r = result
        if (invalid) {
            Text(
                stringResource(R.string.invalid_input),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (r != null) {
            SectionCard(stringResource(R.string.sec_3d)) {
                Stair3DView(r, unit)
                Text(
                    stringResource(R.string.hint_3d),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(stringResource(R.string.sec_results)) {
                TileGrid(
                    listOf(
                        stringResource(R.string.r_risers) to r.risers.toString(),
                        stringResource(R.string.r_treads) to r.treadsTotal.toString(),
                        stringResource(R.string.r_riser_real) to Fmt.len(r.riserMm, unit),
                        stringResource(R.string.r_gap) to Fmt.len(r.gapMm, unit),
                        stringResource(R.string.r_tread) to treadText(r, unit),
                        stringResource(R.string.r_angle) to Fmt.angle(r.angleDeg),
                        stringResource(R.string.r_comfort) to Fmt.len(r.comfortMm, unit),
                        stringResource(R.string.r_dev) to signedLen(r.heightErrMm, unit) + " / " + signedLen(r.runErrMm, unit),
                        stringResource(R.string.r_footprint) to
                                Fmt.len(r.footprintXMm, unit) + " × " + Fmt.len(r.footprintZMm, unit),
                        stringResource(R.string.r_stringer) to Fmt.len(r.stringerSlopeMm, unit),
                        stringResource(R.string.r_handrail) to Fmt.len(r.handrailMm, unit)
                    )
                )
                r.advices.forEach { a ->
                    val color = if (a.ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            if (a.ok) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(adviceText(a, r, unit), color = color, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            TipsCard(r, unit)
            StringerCard(r, unit)

            Button(
                onClick = {
                    try {
                        val uri = PdfExporter.export(context, r, unit)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(null, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(send, context.getString(R.string.share_title))
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.export_error, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_pdf))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun treadText(r: StairResult, unit: LengthUnit): String =
    if (kotlin.math.abs(r.tread2Mm - r.treadMm) > 0.5) Fmt.len(r.treadMm, unit) + " / " + Fmt.len(r.tread2Mm, unit)
    else Fmt.len(r.treadMm, unit)

private fun signedLen(mm: Double, unit: LengthUnit): String =
    (if (mm >= 0) "+" else "−") + Fmt.len(kotlin.math.abs(mm), unit)

@Composable
private fun NumField(label: String, value: String, suffix: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = { Text(suffix) },
        singleLine = true,
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun adviceText(a: Advice, r: StairResult, unit: LengthUnit): String = when (a) {
    Advice.TREAD_ADJUSTED -> stringResource(
        R.string.adv_tread_adjusted, Fmt.len(r.input.treadMm, unit), Fmt.len(r.treadMm, unit)
    )
    Advice.FITS -> stringResource(R.string.adv_fits, Fmt.len((r.input.runMm - r.footprintXMm).coerceAtLeast(0.0), unit))
    Advice.NOT_FITS -> stringResource(
        R.string.adv_not_fits, Fmt.len(r.footprintXMm, unit), Fmt.len(r.input.runMm, unit)
    )
    Advice.COMFORT_OK -> stringResource(R.string.adv_comfort_ok, Fmt.num(r.comfortMm, 0) + " mm")
    Advice.COMFORT_BAD -> stringResource(R.string.adv_comfort_bad, Fmt.num(r.comfortMm, 0) + " mm")
    Advice.RISER_HIGH -> stringResource(R.string.adv_riser_high)
    Advice.RISER_LOW -> stringResource(R.string.adv_riser_low)
    Advice.TREAD_SHORT -> stringResource(R.string.adv_tread_short)
    Advice.ANGLE_STEEP -> stringResource(R.string.adv_angle_steep)
    Advice.ANGLE_SHALLOW -> stringResource(R.string.adv_angle_shallow)
    Advice.SMALL_STAIR -> stringResource(R.string.adv_small)
}

@Composable
private fun TipsCard(r: StairResult, unit: LengthUnit) {
    val tips = TipsEngine.build(r, unit)
    if (tips.isEmpty()) return
    SectionCard(stringResource(R.string.sec_tips)) {
        tips.forEach { tip ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = if (tip.problem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(tip.res, *tip.args.toTypedArray()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StringerCard(r: StairResult, unit: LengthUnit) {
    val flights = r.flights.withIndex().filter { it.value.treads >= 1 }
    if (flights.isEmpty()) return
    var sel by remember(r.risers, r.type) { mutableIntStateOf(0) }
    val idx = sel.coerceIn(0, flights.lastIndex)
    val flight = flights[idx].value

    SectionCard(stringResource(R.string.sec_stringer)) {
        if (flights.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                flights.forEachIndexed { i, f ->
                    FilterChip(
                        selected = idx == i,
                        onClick = { sel = i },
                        label = { Text(stringResource(R.string.flight_n, f.index + 1)) }
                    )
                }
            }
        }
        StringerCanvas(flight, r, unit)
        Text(
            stringResource(R.string.st_marks_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        flight.marks(r.riserMm, flight.treadMm).forEachIndexed { i, p ->
            Text(
                stringResource(R.string.st_row, i + 1, Fmt.len(p.first, unit), Fmt.len(p.second, unit)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StringerCanvas(flight: Flight, r: StairResult, unit: LengthUnit) {
    val fill = Color(0xFFC69A6B).toArgb()
    val line = Color(0xFF5A3E22).toArgb()
    val text = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelR = "h = " + Fmt.len(r.riserMm, unit)
    val labelD = "b = " + Fmt.len(flight.treadMm, unit)
    val labelA = "α = " + Fmt.angle(r.angleDeg)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        drawIntoCanvas {
            StringerDrawer.draw(
                it.nativeCanvas, 0f, 0f, size.width, size.height,
                flight, r.riserMm, flight.treadMm,
                fill, line, text, 12.sp.toPx(),
                labelR, labelD, labelA
            )
        }
    }
}
