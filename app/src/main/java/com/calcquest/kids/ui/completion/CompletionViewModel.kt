package com.calcquest.kids.ui.completion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcquest.kids.data.repository.QuestRepository
import com.calcquest.kids.data.repository.SettingsRepository
import com.calcquest.kids.data.repository.isSolved
import com.calcquest.kids.data.repository.op
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.RouteCalculator
import com.calcquest.kids.domain.quests.Topic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ReviewItem(
    val number: Int,
    val equation: String,
    val spoken: String,
    val firstTry: Boolean,
    val hintUsed: Boolean,
)

data class CompletionUiState(
    val loading: Boolean = true,
    val topic: Topic? = null,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val solved: Int = 0,
    val total: Int = 0,
    val firstTryCount: Int = 0,
    val review: List<ReviewItem> = emptyList(),
    val nextTopic: Topic? = null,
    val routeComplete: Boolean = false,
    val animate: Boolean = false,
)

class CompletionViewModel(
    private val attemptId: Long,
    private val quests: QuestRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CompletionUiState())
    val state: StateFlow<CompletionUiState> = _state.asStateFlow()

    private val openAttempt = Channel<Long>(Channel.BUFFERED)
    val openAttemptEvents = openAttempt.receiveAsFlow()
    private var busy = false

    init {
        viewModelScope.launch {
            val attempt = quests.observeAttempt(attemptId).first()
            val questions = quests.observeQuestions(attemptId).first()
            val s = settings.current()
            val topic = attempt?.let { Topic.fromName(it.topic) }
            val difficulty = Difficulty.fromName(attempt?.difficulty)
            val completed = RouteCalculator.completedFor(quests.completionRecordsNow(), difficulty)
            val route = RouteCalculator.compute(s.enabledTopics, completed)
            _state.value = CompletionUiState(
                loading = false,
                topic = topic,
                difficulty = difficulty,
                solved = questions.count { it.isSolved },
                total = questions.size,
                firstTryCount = questions.count { it.isSolved && it.firstSelectedAnswer == it.correctAnswer },
                review = questions.map { q ->
                    val result = q.op.apply(q.firstOperand, q.secondOperand)
                    ReviewItem(
                        number = q.position + 1,
                        equation = "${q.firstOperand} ${q.op.symbol} ${q.secondOperand} = $result",
                        spoken = "${q.firstOperand} ${q.op.spoken} ${q.secondOperand} equals $result",
                        firstTry = q.firstSelectedAnswer == q.correctAnswer,
                        hintUsed = q.hintUsed,
                    )
                },
                nextTopic = topic?.let { RouteCalculator.nextAfter(it, s.enabledTopics, completed) },
                routeComplete = route.isRouteComplete,
                animate = !s.reduceAnimation,
            )
        }
    }

    /** "Continue to next level": resume an unfinished attempt there, or start a fresh one. */
    fun continueToNext() {
        val next = _state.value.nextTopic ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val d = _state.value.difficulty
                val id = quests.activeAttemptId(next, d) ?: quests.startNewAttempt(next, d)
                openAttempt.send(id)
            } finally {
                busy = false
            }
        }
    }
}
