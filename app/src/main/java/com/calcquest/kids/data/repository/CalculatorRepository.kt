package com.calcquest.kids.data.repository

import androidx.room.withTransaction
import com.calcquest.kids.data.local.AppDatabase
import com.calcquest.kids.data.local.CalculationEntity
import com.calcquest.kids.domain.calculator.CompletedCalculation
import com.calcquest.kids.domain.timer.WallClock
import kotlinx.coroutines.flow.Flow

class CalculatorRepository(
    private val db: AppDatabase,
    private val clock: WallClock,
) {
    private val dao = db.calculationDao()

    val history: Flow<List<CalculationEntity>> = dao.observeLatest(HISTORY_LIMIT)

    /** Stores one successful calculation and keeps only the latest [HISTORY_LIMIT]. */
    suspend fun record(calc: CompletedCalculation) {
        db.withTransaction {
            dao.insert(
                CalculationEntity(
                    firstOperand = calc.firstOperand,
                    operator = calc.operator.symbol,
                    secondOperand = calc.secondOperand,
                    result = calc.result,
                    rounded = calc.rounded,
                    createdAt = clock.nowMillis(),
                )
            )
            dao.trimTo(HISTORY_LIMIT)
        }
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val HISTORY_LIMIT = 50
    }
}
