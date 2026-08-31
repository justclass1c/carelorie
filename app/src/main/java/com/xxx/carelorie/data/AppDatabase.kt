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
    version = 16,
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
         * The AI advice and onboarding answers on `user_profiles`, shared by the migrations that
         * introduce them. Format is the ALTER-ready `column type` form.
         */
        private val userProfileColumnDdls = listOf(
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

        private fun columnExists(
            db: SupportSQLiteDatabase,
            table: String,
            column: String
        ): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameColumn = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == column) return true
                }
            }
            return false
        }

        /**
         * ALTER that tolerates a column already being there. The two branch histories shipped
         * overlapping schemas under the same version numbers, so a database can arrive at a
         * migration already carrying pieces of what it adds.
         */
        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            ddl: String
        ) {
            if (!columnExists(db, table, ddl.substringBefore(' '))) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN $ddl")
            }
        }

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
                for (ddl in userProfileColumnDdls) {
                    addColumnIfMissing(db, "user_profiles", ddl)
                }

                // --- users: "member since" ----------------------------------------------
                addColumnIfMissing(db, "users", "createdAt TEXT")

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
                addColumnIfMissing(
                    db,
                    "users",
                    "recoveryKey TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * 14 to 15: sync bookkeeping on `meal_presets` — plus everything master's version 14
         * never had.
         *
         * Two different databases both answer "version 14". The nk branch's has the saved-meal
         * tables from [MIGRATION_12_13] and needs only the two sync flags: saved meals were
         * device-only, so the table had no need to record what had reached the server, and now
         * that they sync it needs the same flags the diary and the food library carry. Master's
         * version 14 was built under `fallbackToDestructiveMigration()` with none of the saved
         * meals, onboarding columns, or `users.createdAt` — the merge that joined the branches
         * kept nk's version numbering, so this migration must reconcile whichever flavor it
         * finds. Everything is guarded: CREATE IF NOT EXISTS for what is absent, [addColumnIfMissing]
         * for what may or may not be. Existing rows default to unsynced, which is exactly right —
         * they have never been uploaded, and marking them so is what gets them pushed on the next
         * refresh.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- saved meals, with the sync flags already in place on a fresh create ----
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_presets (
                        localId TEXT NOT NULL PRIMARY KEY,
                        ownerUserId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        mealType TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        isPendingDelete INTEGER NOT NULL DEFAULT 0
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

                // --- nk's 14: the tables exist, only the flags are new ---------------------
                addColumnIfMissing(db, "meal_presets", "isSynced INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(
                    db,
                    "meal_presets",
                    "isPendingDelete INTEGER NOT NULL DEFAULT 0"
                )

                // --- master's 14: none of the 12-to-13 changes ever ran -------------------
                addColumnIfMissing(db, "users", "createdAt TEXT")
                for (ddl in userProfileColumnDdls) {
                    addColumnIfMissing(db, "user_profiles", ddl)
                }
            }
        }

        /**
         * 15 to 16: offline-first bookkeeping for user data, and a persistent "was synced" flag
         * for saved meals.
         *
         * The weight and profile tables used to write locally and then immediately call Supabase
         * inline, so a flaky connection could fail the whole save or leave the remote copy stale.
         * Adding an [isSynced] flag lets those writes queue locally and upload later. Saved meals
         * get a [wasSynced] flag so a delete made after a rename still reaches the server instead
         * of resurrecting the old copy on the next refresh.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "meal_presets", "wasSynced INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "weight_records", "isSynced INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "user_profiles", "isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "carelorie_database"
                )
                .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
