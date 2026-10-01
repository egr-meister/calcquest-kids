package com.calcquest.kids.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcquest.kids.data.repository.AppSettings
import com.calcquest.kids.data.repository.CalculatorRepository
import com.calcquest.kids.data.repository.QuestRepository
import com.calcquest.kids.data.repository.SettingsRepository
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.RouteCalculator
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.domain.timer.TimerDurations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResetAction(val title: String, val message: String, val confirm: String) {
    RESET_DIFFICULTY(
        "Reset this difficulty?",
        "Quest progress and unfinished quests for the selected difficulty will be removed. Other difficulties are not changed.",
        "Reset",
    ),
    RESET_ALL_QUESTS(
        "Reset all quest progress?",
        "Completed levels and unfinished quests for every difficulty will be removed.",
        "Reset all",
    ),
    CLEAR_HISTORY(
        "Clear calculator history?",
        "All saved calculations will be removed from this device.",
        "Clear history",
    ),
    CLEAR_ALL_DATA(
        "Clear all local data?",
        "Calculations, quest progress and settings will be removed. Settings return to: all topics on, Easy, Challenge Timer off, sound off.",
        "Clear everything",
    ),
}

data class ParentUiState(
    val loading: Boolean = true,
    val saved: AppSettings = AppSettings(),
    val draft: AppSettings = AppSettings(),
    /** Topic that would become the next required level before the current one. */
    val pendingWarning: Topic? = null,
    val pendingReset: ResetAction? = null,
    val message: String? = null,
) {
    val hasUnsavedChanges: Boolean get() = !loading && draft != saved
}

class ParentViewModel(
    private val settings: SettingsRepository,
    private val quests: QuestRepository,
    private val calculator: CalculatorRepository,
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentUiState())
    val state: StateFlow<ParentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.current()
            _state.value = ParentUiState(loading = false, saved = current, draft = current)
        }
    }

    private fun editDraft(transform: (AppSettings) -> AppSettings) =
        _state.update { it.copy(draft = transform(it.draft), message = null) }

    fun toggleTopic(topic: Topic) = editDraft { d ->
        val next = if (topic in d.enabledTopics) d.enabledTopics - topic else d.enabledTopics + topic
        // At least one topic must remain enabled.
        if (next.isEmpty()) d else d.copy(enabledTopics = next)
    }

    fun setDifficulty(difficulty: Difficulty) = editDraft { it.copy(difficulty = difficulty) }
    fun setTimerEnabled(enabled: Boolean) = editDraft { it.copy(timerEnabled = enabled) }
    fun setTimerSeconds(seconds: Int) = editDraft { it.copy(timerSeconds = TimerDurations.sanitize(seconds)) }
    fun setSound(enabled: Boolean) = editDraft { it.copy(soundEnabled = enabled) }
    fun setReduceAnimation(enabled: Boolean) = editDraft { it.copy(reduceAnimation = enabled) }

    fun discardChanges() = _state.update { it.copy(draft = it.saved, pendingWarning = null) }

    /** Saves, first explaining if an incomplete earlier topic would become the next required level. */
    fun save() {
        val s = _state.value
        if (!s.hasUnsavedChanges) return
        viewModelScope.launch {
            val completed = RouteCalculator.completedFor(quests.completionRecordsNow(), s.draft.difficulty)
            val warning = RouteCalculator.earlierRequirementIntroduced(
                oldEnabled = s.saved.enabledTopics,
                newEnabled = s.draft.enabledTopics,
                completed = completed,
            )
            if (warning != null) {
                _state.update { it.copy(pendingWarning = warning) }
            } else {
                persist(s.draft)
            }
        }
    }

    fun confirmWarning() {
        val draft = _state.value.draft
        _state.update { it.copy(pendingWarning = null) }
        viewModelScope.launch { persist(draft) }
    }

    fun dismissWarning() = _state.update { it.copy(pendingWarning = null) }

    private suspend fun persist(draft: AppSettings) {
        settings.save(draft)
        _state.update { it.copy(saved = draft, draft = draft, message = "Settings saved.") }
    }

    fun requestReset(action: ResetAction) = _state.update { it.copy(pendingReset = action, message = null) }
    fun dismissReset() = _state.update { it.copy(pendingReset = null) }

    fun confirmReset() {
        val action = _state.value.pendingReset ?: return
        val difficulty = _state.value.saved.difficulty
        _state.update { it.copy(pendingReset = null) }
        // Application scope so the reset completes even if the screen is closed right away.
        applicationScope.launch {
            val message = when (action) {
                ResetAction.RESET_DIFFICULTY -> {
                    quests.resetDifficulty(difficulty)
                    "${difficulty.label} quest progress was reset."
                }
                ResetAction.RESET_ALL_QUESTS -> {
                    quests.resetAll()
                    "All quest progress was reset."
                }
                ResetAction.CLEAR_HISTORY -> {
                    calculator.clear()
                    "Calculator history was cleared."
                }
                ResetAction.CLEAR_ALL_DATA -> {
                    quests.resetAll()
                    calculator.clear()
                    settings.clearAll()
                    "All local data was cleared."
                }
            }
            val defaults = if (action == ResetAction.CLEAR_ALL_DATA) AppSettings() else null
            _state.update { st ->
                if (defaults != null) st.copy(saved = defaults, draft = defaults, message = message)
                else st.copy(message = message)
            }
        }
    }
}
