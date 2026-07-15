package com.xxx.carelorie

import android.R.attr.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Dashboard(navController: NavController) {
    val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // greeting and date at the top left
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = currentDate,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(Modifier.height(20.dp))
        // content below
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    Dashboard(navController = NavController(context = LocalContext.current))
}
