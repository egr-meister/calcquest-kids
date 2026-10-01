package com.calcquest.kids.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CalculationEntity::class,
        LevelProgressEntity::class,
        QuestAttemptEntity::class,
        QuestQuestionEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calculationDao(): CalculationDao
    abstract fun questDao(): QuestDao

    companion object {
        const val VERSION = 1
        private const val NAME = "calcquest.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // Intentionally no fallbackToDestructiveMigration(): updates must migrate data.
                .build()
    }
}

/**
 * Schema migrations. Schemas are exported to app/schemas/ by the Room compiler.
 * When the version is bumped, add a Migration(old, new) here and a test against the
 * exported JSON. Version 1 is the initial schema, so the list is empty.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
