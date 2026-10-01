package com.calcquest.kids.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcquest.kids.data.local.CalculationEntity
import com.calcquest.kids.data.repository.CalculatorRepository
import com.calcquest.kids.data.repository.SettingsRepository
import com.calcquest.kids.domain.calculator.CalcInput
import com.calcquest.kids.domain.calculator.CalculatorReducer
import com.calcquest.kids.domain.calculator.CalculatorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalculatorViewModel(
    private val repository: CalculatorRepository,
    private val settings: SettingsRepository,
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val history: StateFlow<List<CalculationEntity>> =
        repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var draftJob: Job? = null

    init {
        viewModelScope.launch {
            _state.value = settings.calculatorDraft.first().copy(error = null, notice = null)
            _ready.value = true
        }
    }

    /** Inputs are reduced synchronously on the main thread, so one Equals = one history row. */
    fun onInput(input: CalcInput) {
        if (!_ready.value) return
        val result = CalculatorReducer.reduce(_state.value, input)
        _state.value = result.state
        result.completed?.let { calc -> applicationScope.launch { repository.record(calc) } }
        saveDraft(result.state)
    }

    fun useResult(entry: CalculationEntity) = onInput(CalcInput.UseValue(entry.result))

    fun clearHistory() {
        applicationScope.launch { repository.clear() }
    }

    private fun saveDraft(state: CalculatorState) {
        draftJob?.cancel()
        draftJob = applicationScope.launch { settings.saveCalculatorDraft(state) }
    }
}
