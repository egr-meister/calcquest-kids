package com.calcquest.kids.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.calcquest.kids.domain.calculator.CalcOperator
import com.calcquest.kids.domain.calculator.CalculatorState
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.domain.timer.TimerDurations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val enabledTopics: Set<Topic> = Topic.entries.toSet(),
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val timerEnabled: Boolean = false,
    val timerSeconds: Int = TimerDurations.DEFAULT_SECONDS,
    val soundEnabled: Boolean = false,
    val reduceAnimation: Boolean = false,
) {
    /** Duration applied to newly shown questions; 0 when the Challenge Timer is off. */
    val timerDurationMillis: Long get() = if (timerEnabled) timerSeconds * 1000L else 0L
}

/** Small preferences stored with DataStore (not Room). */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val enabledTopics = stringSetPreferencesKey("enabled_topics")
        val difficulty = stringPreferencesKey("difficulty")
        val timerEnabled = booleanPreferencesKey("timer_enabled")
        val timerSeconds = intPreferencesKey("timer_seconds")
        val sound = booleanPreferencesKey("sound_enabled")
        val reduceAnimation = booleanPreferencesKey("reduce_animation")

        val draftFirst = stringPreferencesKey("calc_draft_first")
        val draftOperator = stringPreferencesKey("calc_draft_operator")
        val draftSecond = stringPreferencesKey("calc_draft_second")
        val draftShowingResult = booleanPreferencesKey("calc_draft_showing_result")
        val draftExpression = stringPreferencesKey("calc_draft_expression")
        val draftRounded = booleanPreferencesKey("calc_draft_rounded")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val topics = p[Keys.enabledTopics]?.mapNotNull { Topic.fromName(it) }?.toSet()
        AppSettings(
            // At least one topic must remain enabled.
            enabledTopics = if (topics.isNullOrEmpty()) Topic.entries.toSet() else topics,
            difficulty = Difficulty.fromName(p[Keys.difficulty]),
            timerEnabled = p[Keys.timerEnabled] ?: false,
            timerSeconds = TimerDurations.sanitize(p[Keys.timerSeconds] ?: TimerDurations.DEFAULT_SECONDS),
            soundEnabled = p[Keys.sound] ?: false,
            reduceAnimation = p[Keys.reduceAnimation] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(settings: AppSettings) {
        require(settings.enabledTopics.isNotEmpty()) { "At least one topic must stay enabled" }
        store.edit { p ->
            p[Keys.enabledTopics] = settings.enabledTopics.map { it.name }.toSet()
            p[Keys.difficulty] = settings.difficulty.name
            p[Keys.timerEnabled] = settings.timerEnabled
            p[Keys.timerSeconds] = TimerDurations.sanitize(settings.timerSeconds)
            p[Keys.sound] = settings.soundEnabled
            p[Keys.reduceAnimation] = settings.reduceAnimation
        }
    }

    val calculatorDraft: Flow<CalculatorState> = store.data.map { p ->
        CalculatorState(
            first = p[Keys.draftFirst] ?: "",
            operator = CalcOperator.fromSymbol(p[Keys.draftOperator]),
            second = p[Keys.draftSecond] ?: "",
            showingResult = p[Keys.draftShowingResult] ?: false,
            lastExpression = p[Keys.draftExpression]?.takeIf { it.isNotEmpty() },
            resultRounded = p[Keys.draftRounded] ?: false,
        )
    }

    suspend fun saveCalculatorDraft(state: CalculatorState) {
        store.edit { p ->
            p[Keys.draftFirst] = state.first
            p[Keys.draftOperator] = state.operator?.symbol ?: ""
            p[Keys.draftSecond] = state.second
            p[Keys.draftShowingResult] = state.showingResult
            p[Keys.draftExpression] = state.lastExpression ?: ""
            p[Keys.draftRounded] = state.resultRounded
        }
    }

    /** Restores defaults: all topics, Easy, timer and sound off; clears the calculator draft. */
    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
