package com.xxx.carelorie.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xxx.carelorie.data.local.FoodLogDao
import com.xxx.carelorie.data.local.FoodLogEntity
import com.xxx.carelorie.data.local.FoodPresetDao
import com.xxx.carelorie.data.local.FoodPresetEntity

@Database(
    entities = [
        User::class,
        UserProfile::class,
        WeightRecord::class,
        FoodLogEntity::class,
        FoodPresetEntity::class,
        MealPresetEntity::class,
        MealPresetItemEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun foodPresetDao(): FoodPresetDao
    abstract fun mealPresetDao(): MealPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 12 to 13: the onboarding columns, the AI weight advice, account creation dates, and the
         * saved-meal tables.
         *
         * Written out rather than left to `fallbackToDestructiveMigration()`, which is what the
         * database used up to version 12. That call silently dropped every table on each bump —
         * including the offline write queue in `food_log_entries`, so any entry made without a
         * connection was destroyed by the next app update rather than uploaded. All the changes
         * here are additive, so the migration is only ALTER and CREATE.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- user_profiles: AI advice + the onboarding answers -------------------
                val profileColumns = listOf(
                    "weightAdvice TEXT",
                    "everWeighedOver95 TEXT",
                    "weightTrend TEXT",
                    "bodyFatBand TEXT",
                    "exerciseFrequency TEXT",
                    "activityLevel TEXT",
                    "cardioExperience TEXT",
                    "goal TEXT",
                    "targetWeight REAL",
                    "dietType TEXT",
                    "trainingType TEXT",
                    "calorieDistribution TEXT",
                    "proteinPreference TEXT",
                    "estimatedTdee INTEGER",
                    "onboardingCompletedAt TEXT"
                )
                for (column in profileColumns) {
                    db.execSQL("ALTER TABLE user_profiles ADD COLUMN $column")
                }

                // --- users: "member since" ----------------------------------------------
                db.execSQL("ALTER TABLE users ADD COLUMN createdAt TEXT")

                // --- saved meals --------------------------------------------------------
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_presets (
                        localId TEXT NOT NULL PRIMARY KEY,
                        ownerUserId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        mealType TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_preset_items (
                        localId TEXT NOT NULL PRIMARY KEY,
                        mealPresetId TEXT NOT NULL,
                        foodName TEXT NOT NULL,
                        calories INTEGER NOT NULL,
                        protein REAL NOT NULL,
                        carbs REAL NOT NULL,
                        fat REAL NOT NULL,
                        quantity REAL NOT NULL,
                        sourcePresetId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meal_preset_items_mealPresetId " +
                        "ON meal_preset_items (mealPresetId)"
                )
            }
        }

        /**
         * 13 to 14: the hashed one-time recovery key on `users`.
         *
         * Added on master as a version bump under `fallbackToDestructiveMigration()`, which would
         * have wiped every table including the offline write queue. Written out here instead so
         * the bump stays additive, consistent with [MIGRATION_12_13].
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE users ADD COLUMN recoveryKey TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "carelorie_database"
                )
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
