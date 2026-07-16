package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xxx.carelorie.ui.components.dashboard.MacroRow
import com.xxx.carelorie.ui.components.dashboard.ProgressPreview
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Dashboard(navController: NavController, username: String) {
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
                text = "Welcome, $username",
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = currentDate,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // content below
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            ProgressPreview()

            Spacer(Modifier.height(20.dp))

            MacroRow()


        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    Dashboard(navController = NavController(context = LocalContext.current), username = "name")
}
