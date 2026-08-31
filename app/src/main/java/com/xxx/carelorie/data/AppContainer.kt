package com.xxx.carelorie.data

import android.content.Context
import com.xxx.carelorie.BuildConfig
import com.xxx.carelorie.data.nutrition.FoodRecognitionService
import com.xxx.carelorie.data.nutrition.FoodRecognitionServiceProvider
import com.xxx.carelorie.data.nutrition.OpenFoodFactsService
import com.xxx.carelorie.data.remote.DeepSeekService
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
    val connectivity: ConnectivityChecker = AndroidConnectivityChecker(context)
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


    val foodRepository: FoodRepository by lazy {
        FoodRepository(
            supabaseRepository = supabaseRepository,
            foodLogDao = database.foodLogDao(),
            foodPresetDao = database.foodPresetDao(),
            connectivity = connectivity
        )
    }

    val mealPresetRepository: MealPresetRepository by lazy {
        MealPresetRepository(
            mealPresetDao = database.mealPresetDao(),
            foodRepository = foodRepository,
            supabaseRepository = supabaseRepository,
            connectivity = connectivity
        )
    }

    val openFoodFactsService: OpenFoodFactsService by lazy { OpenFoodFactsService() }
    val deepSeekService: DeepSeekService by lazy { DeepSeekService() }

    /**
     * Photos go to Gemini, text estimates to DeepSeek; whichever keys are present are used and
     * the rest report themselves as unconfigured. Every screen works either way.
     */
    val foodRecognitionService: FoodRecognitionService by lazy {
        FoodRecognitionServiceProvider.create(
            deepSeekKey = BuildConfig.DEEPSEEK_API_KEY,
            geminiKey = BuildConfig.GEMINI_API_KEY
        )
    }
}
