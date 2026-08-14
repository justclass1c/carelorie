package com.xxx.carelorie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.xxx.carelorie.data.AppDatabase
import com.xxx.carelorie.data.MacroDataRepository
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.ui.BottomNavBar
import com.xxx.carelorie.ui.theme.CarelorieTheme
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = UserRepository(database.userDao(), database.userProfileDao())
        val macroRepository = MacroDataRepository()
        
        val authViewModel = AuthViewModel(repository)
        val profileViewModel = ProfileViewModel(repository)
        val dashboardViewModel = DashboardViewModel(repository, macroRepository)
        
        enableEdgeToEdge()
        setContent {
            CarelorieTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BottomNavBar(
                        authViewModel = authViewModel, 
                        profileViewModel = profileViewModel,
                        dashboardViewModel = dashboardViewModel
                    )
                }
            }
        }
    }
}
