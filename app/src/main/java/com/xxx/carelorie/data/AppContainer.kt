package com.xxx.carelorie.data

import android.content.Context
import com.xxx.carelorie.BuildConfig
import com.xxx.carelorie.data.nutrition.FoodRecognitionService
import com.xxx.carelorie.data.nutrition.FoodRecognitionServiceProvider
import com.xxx.carelorie.data.nutrition.OpenFoodFactsService
import com.xxx.carelorie.data.remote.SupabaseRepository

/**
 * Single place where repositories and services are built and shared.
 *
 * Held on the Application so one instance survives configuration changes, rather than being
 * rebuilt in MainActivity.onCreate on every rotation.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
    val sessionManager: SessionManager = SessionManager(context)
    val themeManager: ThemeManager = ThemeManager(context)

    val userRepository: UserRepository by lazy {
        UserRepository(
            userDao = database.userDao(),
            userProfileDao = database.userProfileDao(),
            weightDao = database.weightDao(),
            sessionManager = sessionManager,
            supabaseRepository = supabaseRepository
        )
    }

    val macroDataRepository: MacroDataRepository by lazy {
        MacroDataRepository(supabaseRepository)
    }

    val foodRepository: FoodRepository by lazy {
        FoodRepository(supabaseRepository, database.foodLogDao())
    }

    val openFoodFactsService: OpenFoodFactsService by lazy { OpenFoodFactsService() }

    /**
     * Real recogniser when DEEPSEEK_API_KEY is set in local.properties, stub otherwise.
     * Every screen works either way.
     */
    val foodRecognitionService: FoodRecognitionService by lazy {
        FoodRecognitionServiceProvider.create(BuildConfig.DEEPSEEK_API_KEY)
    }
}
