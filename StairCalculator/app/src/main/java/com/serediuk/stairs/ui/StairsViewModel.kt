package com.serediuk.stairs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serediuk.stairs.calc.Fmt
import com.serediuk.stairs.calc.LengthUnit
import com.serediuk.stairs.calc.StairCalculator
import com.serediuk.stairs.calc.StairInput
import com.serediuk.stairs.calc.StairResult
import com.serediuk.stairs.calc.StairType
import com.serediuk.stairs.data.AppDatabase
import com.serediuk.stairs.data.HistoryEntity
import com.serediuk.stairs.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class InputState(
    val type: StairType,
    val unit: LengthUnit,
    val height: String,
    val run: String,
    val tread: String,
    val riser: String,
    val width: String,
    val thickness: String = "",
    val fitLength: Boolean = false,
    val run2: String = ""
)

enum class Field { HEIGHT, RUN, RUN2, TREAD, RISER, WIDTH, THICKNESS }

class StairsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val dao = AppDatabase.get(app).historyDao()

    val theme = MutableStateFlow(prefs.theme)
    val language = MutableStateFlow(prefs.language)

    private val _input = MutableStateFlow(initialInput())
    val input: StateFlow<InputState> = _input.asStateFlow()

    private val _result = MutableStateFlow<StairResult?>(null)
    val result: StateFlow<StairResult?> = _result.asStateFlow()

    private val _invalid = MutableStateFlow(false)
    val invalid: StateFlow<Boolean> = _invalid.asStateFlow()

    val history: StateFlow<List<HistoryEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------- input handling ----------

    /** Every launch starts from empty (zero) fields. */
    private fun initialInput(): InputState {
        val u = try {
            LengthUnit.valueOf(prefs.unit)
        } catch (e: IllegalArgumentException) {
            LengthUnit.CM
        }
        return InputState(StairType.STRAIGHT, u, "", "", "", "", "")
    }

    private fun raw(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** Height, length and tread are required. Riser (0 = auto) and width (default 900 mm) are optional. */
    private fun parse(s: InputState): StairInput? {
        fun mm(v: String): Double? = raw(v)?.takeIf { it > 0 }?.times(s.unit.mmPerUnit)
        val h = mm(s.height) ?: return null
        val run = mm(s.run) ?: return null
        return StairInput(
            type = s.type,
            heightMm = h,
            runMm = run,
            treadMm = mm(s.tread) ?: 0.0,
            riserMm = mm(s.riser) ?: 0.0,
            widthMm = mm(s.width) ?: 900.0,
            thicknessMm = mm(s.thickness) ?: 0.0,
            fitLength = s.fitLength,
            run2Mm = mm(s.run2) ?: 0.0
        )
    }

    private fun clearResult() {
        _result.value = null
        _invalid.value = false
    }

    fun setType(t: StairType) {
        _input.value = _input.value.copy(type = t)
        clearResult()
    }

    fun setFit(on: Boolean) {
        _input.value = _input.value.copy(fitLength = on)
        clearResult()
    }

    fun update(field: Field, value: String) {
        val s = _input.value
        _input.value = when (field) {
            Field.HEIGHT -> s.copy(height = value)
            Field.RUN -> s.copy(run = value)
            Field.RUN2 -> s.copy(run2 = value)
            Field.TREAD -> s.copy(tread = value)
            Field.RISER -> s.copy(riser = value)
            Field.WIDTH -> s.copy(width = value)
            Field.THICKNESS -> s.copy(thickness = value)
        }
        clearResult()
    }

    fun setUnit(new: LengthUnit) {
        val s = _input.value
        if (s.unit == new) return
        fun conv(v: String): String {
            val mm = raw(v)?.times(s.unit.mmPerUnit) ?: return v
            return Fmt.field(mm, new)
        }
        _input.value = s.copy(
            unit = new,
            height = conv(s.height), run = conv(s.run), tread = conv(s.tread),
            riser = conv(s.riser), width = conv(s.width), thickness = conv(s.thickness),
            run2 = conv(s.run2)
        )
        prefs.unit = new.name
    }

    // ---------- calculation ----------

    fun calculate() = compute(save = true)

    private fun compute(save: Boolean) {
        val p = parse(_input.value)
        val res = p?.let { StairCalculator.calculate(it) }
        _result.value = res
        _invalid.value = res == null
        if (save && p != null && res != null) {
            viewModelScope.launch { saveHistory(p, res) }
        }
    }

    // ---------- history ----------

    private fun close(a: Double, b: Double) = abs(a - b) < 0.01

    private suspend fun saveHistory(p: StairInput, res: StairResult) {
        val last = dao.latest()
        if (last != null && last.type == p.type.name &&
            close(last.heightMm, p.heightMm) && close(last.runMm, p.runMm) &&
            close(last.treadMm, p.treadMm) && close(last.riserMm, p.riserMm) &&
            close(last.widthMm, p.widthMm) && close(last.thicknessMm, p.thicknessMm) && close(last.run2Mm, p.run2Mm)
        ) return
        dao.insert(
            HistoryEntity(
                createdAt = System.currentTimeMillis(),
                type = p.type.name,
                heightMm = p.heightMm,
                runMm = p.runMm,
                treadMm = p.treadMm,
                riserMm = p.riserMm,
                widthMm = p.widthMm,
                risers = res.risers,
                realRiserMm = res.riserMm,
                angleDeg = res.angleDeg,
                thicknessMm = p.thicknessMm,
                run2Mm = p.run2Mm
            )
        )
        dao.trim()
    }

    fun restore(e: HistoryEntity) {
        val u = _input.value.unit
        fun f(mm: Double) = if (mm <= 0.0) "" else Fmt.field(mm, u)
        _input.value = InputState(
            StairType.valueOf(e.type), u,
            f(e.heightMm), f(e.runMm), f(e.treadMm), f(e.riserMm), f(e.widthMm),
            thickness = f(e.thicknessMm),
            run2 = f(e.run2Mm)
        )
        compute(save = false)
    }

    fun deleteEntry(e: HistoryEntity) {
        viewModelScope.launch { dao.delete(e) }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clear() }
    }

    // ---------- settings ----------

    fun setTheme(v: String) {
        prefs.theme = v
        theme.value = v
    }

    fun setLanguage(v: String) {
        prefs.language = v
        language.value = v
    }
}
