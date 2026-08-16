package com.xxx.carelorie.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xxx.carelorie.ui.components.food.FoodLogEntryCard
import com.xxx.carelorie.ui.viewmodels.FoodLogEvent
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FoodLogScreen(
    navController: NavController,
    userId: Int,
    viewModel: FoodLogViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(userId, uiState.selectedDate) {
        viewModel.onEvent(FoodLogEvent.LoadLogs(userId, uiState.selectedDate))
    }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                viewModel.onEvent(FoodLogEvent.ChangeDate(userId, newDate))
            },
            uiState.selectedDate.year,
            uiState.selectedDate.monthValue - 1,
            uiState.selectedDate.dayOfMonth
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Food Log",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Date Navigator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.onEvent(FoodLogEvent.ChangeDate(userId, uiState.selectedDate.minusDays(1)))
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day")
            }

            Text(
                text = uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { datePickerDialog.show() }
            )

            IconButton(onClick = {
                viewModel.onEvent(FoodLogEvent.ChangeDate(userId, uiState.selectedDate.plusDays(1)))
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Macro Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MacroSummaryItem(label = "P", current = uiState.totalProtein, target = uiState.proteinTarget, color = Color(0xFFE91E63))
            MacroSummaryItem(label = "C", current = uiState.totalCarbs, target = uiState.carbsTarget, color = Color(0xFF2196F3))
            MacroSummaryItem(label = "F", current = uiState.totalFat, target = uiState.fatTarget, color = Color(0xFF4CAF50))
            MacroSummaryItem(label = "T", current = uiState.totalCalories.toFloat(), target = uiState.caloriesTarget.toFloat(), color = Color(0xFFFF9800), isCalories = true)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No food logs for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.logs) { log ->
                    FoodLogEntryCard(log = log)
                }
            }
        }
    }
}

@Composable
fun MacroSummaryItem(label: String, current: Float, target: Float, color: Color, isCalories: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
            color = Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = label, fontWeight = FontWeight.Bold, color = color)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${current.toInt()} of ${target.toInt()}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
