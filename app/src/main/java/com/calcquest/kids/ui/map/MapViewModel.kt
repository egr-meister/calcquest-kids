package com.calcquest.kids.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcquest.kids.data.repository.QuestRepository
import com.calcquest.kids.data.repository.SettingsRepository
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.LevelNode
import com.calcquest.kids.domain.quests.LevelStatus
import com.calcquest.kids.domain.quests.RouteCalculator
import com.calcquest.kids.domain.quests.RouteState
import com.calcquest.kids.domain.quests.Topic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MapUiState(
    val loading: Boolean = true,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val route: RouteState? = null,
)

sealed interface MapDialog {
    data class Disabled(val topic: Topic) : MapDialog
    data class Locked(val topic: Topic, val required: Topic) : MapDialog
    data class ResumeOrRestart(val topic: Topic) : MapDialog
    data class ConfirmRestart(val topic: Topic) : MapDialog
}

class MapViewModel(
    private val quests: QuestRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<MapUiState> = combine(
        settings.settings,
        quests.completionRecords,
        quests.activeAttempts,
    ) { s, records, active ->
        val completed = RouteCalculator.completedFor(records, s.difficulty)
        val activeSolved = active
            .filter { it.difficulty == s.difficulty.name }
            .mapNotNull { row -> Topic.fromName(row.topic)?.let { it to row.solved } }
            .toMap()
        MapUiState(
            loading = false,
            difficulty = s.difficulty,
            route = RouteCalculator.compute(s.enabledTopics, completed, activeSolved),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private val _dialog = MutableStateFlow<MapDialog?>(null)
    val dialog: StateFlow<MapDialog?> = _dialog.asStateFlow()

    private val openAttempt = Channel<Long>(Channel.BUFFERED)
    val openAttemptEvents = openAttempt.receiveAsFlow()

    private var busy = false

    fun onLevelSelected(node: LevelNode) {
        when (val status = node.status) {
            LevelStatus.DisabledByParent -> _dialog.value = MapDialog.Disabled(node.topic)
            is LevelStatus.Locked -> _dialog.value = MapDialog.Locked(node.topic, status.requiredTopic)
            else -> if (node.hasActiveAttempt) {
                _dialog.value = MapDialog.ResumeOrRestart(node.topic)
            } else {
                start(node.topic, restart = false)
            }
        }
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun resume(topic: Topic) {
        _dialog.value = null
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val id = quests.activeAttemptId(topic, state.value.difficulty)
                    ?: quests.startNewAttempt(topic, state.value.difficulty)
                openAttempt.send(id)
            } finally {
                busy = false
            }
        }
    }

    fun askRestart(topic: Topic) {
        _dialog.value = MapDialog.ConfirmRestart(topic)
    }

    fun confirmRestart(topic: Topic) {
        _dialog.value = null
        start(topic, restart = true)
    }

    /** Starts (or continues) a level; guarded against repeated taps. */
    fun start(topic: Topic, restart: Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val difficulty = state.value.difficulty
                val existing = if (restart) null else quests.activeAttemptId(topic, difficulty)
                val id = existing ?: quests.startNewAttempt(topic, difficulty)
                openAttempt.send(id)
            } finally {
                busy = false
            }
        }
    }
}
