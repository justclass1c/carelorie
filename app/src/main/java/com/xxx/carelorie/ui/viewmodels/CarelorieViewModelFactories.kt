package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xxx.carelorie.CarelorieApplication
import com.xxx.carelorie.data.AppContainer

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as CarelorieApplication).container

/**
 * Factories used with `viewModel(factory = ...)` so every ViewModel is owned by a
 * ViewModelStore and survives configuration changes.
 */
object CarelorieViewModelFactories {

    val Auth = viewModelFactory {
        initializer {
            val c = container()
            AuthViewModel(c.userRepository, c.sessionManager)
        }
    }

    val Profile = viewModelFactory {
        initializer {
            val c = container()
            ProfileViewModel(c.userRepository, c.sessionManager, c.themeManager)
        }
    }

    val Dashboard = viewModelFactory {
        initializer {
            val c = container()
            DashboardViewModel(
                userRepository = c.userRepository,
                macroRepository = c.macroDataRepository,
                foodRepository = c.foodRepository,
                deepSeekService = c.deepSeekService
            )
        }
    }

    val FoodSearch = viewModelFactory {
        initializer {
            val c = container()
            FoodSearchViewModel(
                foodRepository = c.foodRepository,
                openFoodFacts = c.openFoodFactsService,
                recognitionService = c.foodRecognitionService
            )
        }
    }

    val FoodLog = viewModelFactory {
        initializer {
            val c = container()
            FoodLogViewModel(c.foodRepository, c.userRepository)
        }
    }

    val FoodQuery = viewModelFactory {
        initializer {
            val c = container()
            FoodQueryViewModel(
                foodRepository = c.foodRepository,
                openFoodFacts = c.openFoodFactsService,
                recognitionService = c.foodRecognitionService
            )
        }
    }

    val FoodEditor = viewModelFactory {
        initializer { FoodEditorViewModel(container().foodRepository) }
    }

    fun DietChat(userId: String) = viewModelFactory {
        initializer {
            val c = container()
            DietChatViewModel(c.userRepository, userId)
        }
    }
}
