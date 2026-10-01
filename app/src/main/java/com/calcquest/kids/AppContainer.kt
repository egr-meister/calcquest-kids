package com.calcquest.kids

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.calcquest.kids.data.local.AppDatabase
import com.calcquest.kids.data.repository.CalculatorRepository
import com.calcquest.kids.data.repository.QuestRepository
import com.calcquest.kids.data.repository.SettingsRepository
import com.calcquest.kids.domain.generation.QuestionGenerator
import com.calcquest.kids.domain.timer.ElapsedClock
import com.calcquest.kids.domain.timer.WallClock
import com.calcquest.kids.ui.common.SoundPlayer
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "calcquest_settings")

/**
 * Manual dependency injection: one instance per process, owned by [CalcQuestApp].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Survives screen navigation; used for writes that must finish after a screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val elapsedClock = ElapsedClock { SystemClock.elapsedRealtime() }
    val wallClock = WallClock { System.currentTimeMillis() }

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext.settingsStore) }

    val calculatorRepository: CalculatorRepository by lazy { CalculatorRepository(database, wallClock) }

    val questRepository: QuestRepository by lazy {
        QuestRepository(database, QuestionGenerator(Random.Default), wallClock)
    }

    val soundPlayer: SoundPlayer by lazy { SoundPlayer(appContext) }

    /**
     * Question ids whose timer has run in this process. A timer found in storage that is not
     * in this set was interrupted by process death or a restart and is restored paused.
     */
    val timersSeenThisProcess: MutableSet<Long> = java.util.Collections.synchronizedSet(mutableSetOf())
}
