package com.xxx.carelorie.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.xxx.carelorie.ui.theme.CarelorieTheme
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.MacroDataRepository
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.remote.SupabaseRepository
import com.xxx.carelorie.data.AppDatabase
import androidx.compose.ui.platform.LocalContext

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    // This is hard to preview because of the ViewModel dependencies
}
