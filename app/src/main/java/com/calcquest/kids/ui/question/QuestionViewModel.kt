package com.calcquest.kids.ui.question

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcquest.kids.data.local.QuestQuestionEntity
import com.calcquest.kids.data.repository.QuestRepository
import com.calcquest.kids.data.repository.SettingsRepository
import com.calcquest.kids.data.repository.attemptedWrong
import com.calcquest.kids.data.repository.isSolved
import com.calcquest.kids.data.repository.op
import com.calcquest.kids.data.repository.options
import com.calcquest.kids.domain.generation.HintProvider
import com.calcquest.kids.domain.quests.AnswerOutcome
import com.calcquest.kids.domain.quests.ArithmeticOp
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.QUESTIONS_PER_LEVEL
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.domain.timer.ElapsedClock
import com.calcquest.kids.domain.timer.QuestionTimer
import com.calcquest.kids.ui.common.Sfx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Feedback {
    data class Correct(val explanation: String) : Feedback
    data class TryAgain(val guidance: String) : Feedback
}

data class TimerUi(
    val remainingMillis: Long,
    val expired: Boolean,
    val running: Boolean,
    /** Restored after process death or restart: waits for the child to tap Resume. */
    val waitingForResume: Boolean,
)

data class OptionUi(val value: Int, val triedWrong: Boolean, val isCorrectAndSolved: Boolean)

data class QuestionUiState(
    val loading: Boolean = true,
    val topic: Topic? = null,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val questionId: Long = 0,
    val position: Int = 0,
    val total: Int = QUESTIONS_PER_LEVEL,
    val a: Int = 0,
    val op: ArithmeticOp = ArithmeticOp.ADD,
    val b: Int = 0,
    val options: List<OptionUi> = emptyList(),
    val solved: Boolean = false,
    val solvedCount: Int = 0,
    val allSolved: Boolean = false,
    val feedback: Feedback? = null,
    val hintOpen: Boolean = false,
    val hint: String = "",
    val dotGroups: Pair<Int, Int>? = null,
    val timer: TimerUi? = null,
) {
    val expression: String get() = "$a ${op.symbol} $b = ?"
    val spokenExpression: String get() = "$a ${op.spoken} $b equals what?"
}

sealed interface QuestionEvent {
    data class ShowCompletion(val attemptId: Long) : QuestionEvent
    data object Close : QuestionEvent
}

class QuestionViewModel(
    private val attemptId: Long,
    private val quests: QuestRepository,
    private val settings: SettingsRepository,
    private val clock: ElapsedClock,
    private val timersSeenThisProcess: MutableSet<Long>,
    private val applicationScope: CoroutineScope,
    private val playSound: (Sfx) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionUiState())
    val state: StateFlow<QuestionUiState> = _state.asStateFlow()

    private val events = Channel<QuestionEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var questions: List<QuestQuestionEntity> = emptyList()
    private var currentPosition: Int? = null
    private var topic: Topic? = null
    private var difficulty: Difficulty = Difficulty.DEFAULT
    private var soundEnabled = false
    private var answering = false
    private var hintOpen = false

    // ----- timer -----
    private var timer: QuestionTimer? = null
    private var timerQuestionId: Long? = null
    private var screenVisible = false
    private var waitingForResume = false
    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { soundEnabled = it.soundEnabled }
        }
        viewModelScope.launch {
            val attempt = quests.observeAttempt(attemptId).first()
            if (attempt == null) {
                events.send(QuestionEvent.Close)
                return@launch
            }
            topic = Topic.fromName(attempt.topic)
            difficulty = Difficulty.fromName(attempt.difficulty)
            quests.observeQuestions(attemptId).collect { list -> onQuestions(list) }
        }
    }

    private suspend fun onQuestions(list: List<QuestQuestionEntity>) {
        if (list.isEmpty()) {
            // Attempt was cleared (restart or reset from Parent settings).
            events.send(QuestionEvent.Close)
            return
        }
        questions = list
        if (currentPosition == null) {
            val firstUnsolved = list.firstOrNull { !it.isSolved }
            if (firstUnsolved == null) {
                events.send(QuestionEvent.ShowCompletion(attemptId))
                return
            }
            currentPosition = firstUnsolved.position
        }
        val current = currentQuestion() ?: return
        if (timerQuestionId != current.id) prepareTimer(current)
        render()
    }

    private fun currentQuestion(): QuestQuestionEntity? = questions.firstOrNull { it.position == currentPosition }

    private fun render() {
        val q = currentQuestion() ?: return
        val wrong = q.attemptedWrong.toSet()
        val solved = q.isSolved
        val op = q.op
        val feedback = when {
            solved -> Feedback.Correct(HintProvider.explanation(q.firstOperand, op, q.secondOperand))
            wrong.isNotEmpty() -> Feedback.TryAgain(
                "Let's break it into smaller parts. " + HintProvider.hint(q.firstOperand, op, q.secondOperand)
            )
            else -> null
        }
        _state.value = QuestionUiState(
            loading = false,
            topic = topic,
            difficulty = difficulty,
            questionId = q.id,
            position = q.position,
            total = questions.size,
            a = q.firstOperand,
            op = op,
            b = q.secondOperand,
            options = q.options.map { OptionUi(it, it in wrong, solved && it == q.correctAnswer) },
            solved = solved,
            solvedCount = questions.count { it.isSolved },
            allSolved = questions.all { it.isSolved },
            feedback = feedback,
            hintOpen = hintOpen,
            hint = HintProvider.hint(q.firstOperand, op, q.secondOperand),
            dotGroups = HintProvider.dotGroups(q.firstOperand, op, q.secondOperand),
            timer = timerUi(),
        )
    }

    // ------------------------------------------------------------------ answers

    fun onAnswer(value: Int) {
        val q = currentQuestion() ?: return
        if (answering || q.isSolved) return
        answering = true
        viewModelScope.launch {
            try {
                val response = quests.answer(q.id, value)
                when (response.outcome) {
                    is AnswerOutcome.Correct -> {
                        stopTimer(q.id)
                        if (soundEnabled) playSound(if (response.levelCompleted) Sfx.COMPLETE else Sfx.CORRECT)
                    }
                    AnswerOutcome.Incorrect -> if (soundEnabled) playSound(Sfx.TRY_AGAIN)
                    AnswerOutcome.Ignored -> Unit
                }
            } finally {
                answering = false
            }
        }
    }

    /** Moves to the next unsolved question; never advances automatically. */
    fun onNext() {
        val q = currentQuestion() ?: return
        if (!q.isSolved) return
        val next = questions.firstOrNull { !it.isSolved && it.position > q.position }
            ?: questions.firstOrNull { !it.isSolved }
        if (next == null) {
            viewModelScope.launch { events.send(QuestionEvent.ShowCompletion(attemptId)) }
            return
        }
        currentPosition = next.position
        hintOpen = false
        viewModelScope.launch {
            prepareTimer(next)
            render()
        }
    }

    // ------------------------------------------------------------------ hints

    fun openHint() {
        val q = currentQuestion() ?: return
        hintOpen = true
        applicationScope.launch { quests.markHintUsed(q.id) }
        updateTimerRunning()
        render()
    }

    fun closeHint() {
        hintOpen = false
        updateTimerRunning()
        render()
    }

    // ------------------------------------------------------------------ timer

    fun onScreenVisible() {
        screenVisible = true
        updateTimerRunning()
        render()
    }

    fun onScreenHidden() {
        screenVisible = false
        updateTimerRunning()
        render()
    }

    fun onResumeTimer() {
        waitingForResume = false
        updateTimerRunning()
        render()
    }

    private suspend fun prepareTimer(q: QuestQuestionEntity) {
        // Persist and release the previous question's timer.
        pauseAndPersist()
        tickJob?.cancel()
        timer = null
        timerQuestionId = q.id
        waitingForResume = false
        if (q.isSolved) return

        val duration = q.timerDurationMillis ?: settings.current().timerDurationMillis.also {
            // Settings changes apply from the next question; the duration is fixed once shown.
            quests.assignTimerDuration(q.id, it)
        }
        if (duration <= 0L) return

        val remaining = q.timerRemainingMillis ?: duration
        val t = QuestionTimer(clock, duration, remaining, q.timerExpired)
        timer = t
        val wasInterrupted = q.timerRemainingMillis != null && !t.isExpired && q.id !in timersSeenThisProcess
        waitingForResume = wasInterrupted
        timersSeenThisProcess.add(q.id)
        updateTimerRunning()
    }

    private fun shouldRun(): Boolean {
        val t = timer ?: return false
        return screenVisible && !hintOpen && !waitingForResume && !t.isExpired && !t.isStopped &&
            currentQuestion()?.isSolved == false
    }

    private fun updateTimerRunning() {
        val t = timer ?: return
        if (shouldRun()) {
            if (!t.isRunning) {
                t.resume()
                startTicking()
            }
        } else if (t.isRunning) {
            pauseAndPersist()
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val t = timer ?: break
                if (!t.isRunning) break
                if (t.checkExpired()) {
                    persist(t)
                    render()
                    break
                }
                render()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun pauseAndPersist() {
        val t = timer ?: return
        if (!t.isRunning) return
        t.pause()
        tickJob?.cancel()
        persist(t)
    }

    private fun stopTimer(questionId: Long) {
        val t = timer ?: return
        if (timerQuestionId != questionId) return
        tickJob?.cancel()
        t.stop()
        persist(t)
    }

    private fun persist(t: QuestionTimer) {
        val id = timerQuestionId ?: return
        val remaining = t.remainingMillis()
        val expired = t.isExpired
        // Application scope: the write must finish even if this screen is closing.
        applicationScope.launch { quests.saveTimer(id, remaining, expired) }
    }

    private fun timerUi(): TimerUi? {
        val t = timer ?: return null
        return TimerUi(
            remainingMillis = t.remainingMillis(),
            expired = t.isExpired,
            running = t.isRunning,
            waitingForResume = waitingForResume,
        )
    }

    override fun onCleared() {
        pauseAndPersist()
        super.onCleared()
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
