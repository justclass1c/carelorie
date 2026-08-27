package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xxx.carelorie.data.nutrition.FoodCandidate
import com.xxx.carelorie.data.nutrition.NutritionDetail
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.FoodSearchEvent
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel

/**
 * Last stop before anything reaches the food log.
 *
 * Shows the full nutrition panel for each selected item, lets portions be adjusted, and
 * labels where the numbers came from — so an AI estimate is never mistaken for a measured value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewFoodsScreen(
    navController: NavController,
    userId: Int,
    viewModel: FoodSearchViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Once everything is logged, the selection empties and this screen has nothing to show.
    LaunchedEffect(uiState.isLoggingComplete) {
        if (uiState.isLoggingComplete) {
            viewModel.onEvent(FoodSearchEvent.ResetLogged)
            navController.popBackStack()
            navController.popBackStack()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(FoodSearchEvent.MessageConsumed)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Review foods") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Back to search")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add more")
                    }
                    Button(
                        onClick = { viewModel.onEvent(FoodSearchEvent.LogSelected(userId)) },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.hasSelection && !uiState.isLoading
                    ) {
                        Text("Log ${uiState.selectedCount} to ${uiState.mealType}")
                    }
                }
            }
        }
    ) { padding ->
        if (!uiState.hasSelection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nothing selected yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TotalsCard(uiState.totalCalories, uiState.totalProtein, uiState.totalCarbs, uiState.totalFat)

            Spacer(Modifier.height(16.dp))

            uiState.selectedList.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    onRemove = { viewModel.onEvent(FoodSearchEvent.ToggleSelection(candidate)) },
                    onQuantityChange = { q ->
                        viewModel.onEvent(FoodSearchEvent.ChangeQuantity(candidate, q))
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TotalsCard(calories: Int, protein: Float, carbs: Float, fat: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$calories kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("P ${protein.toInt()}g", color = MacroColors.Protein, fontWeight = FontWeight.Medium)
                Text("C ${carbs.toInt()}g", color = MacroColors.Carbs, fontWeight = FontWeight.Medium)
                Text("F ${fat.toInt()}g", color = MacroColors.Fat, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: FoodCandidate,
    onRemove: () -> Unit,
    onQuantityChange: (Float) -> Unit
) {
    var detailExpanded by remember { mutableStateOf(false) }
    val detail = candidate.detail

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        candidate.preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    detail?.servingDescription?.let {
                        Text(
                            it,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove ${candidate.preset.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Estimates are flagged, so a guess is never presented as a measurement.
            detail?.source?.let { source ->
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(source.label, fontSize = 11.sp) },
                    leadingIcon = if (source.isEstimate) {
                        { Icon(Icons.Default.Info, null, Modifier.size(14.dp)) }
                    } else null
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Servings", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onQuantityChange(candidate.quantity - 0.5f) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer servings")
                }
                Text(
                    formatQuantity(candidate.quantity),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 40.dp),
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = { onQuantityChange(candidate.quantity + 0.5f) }) {
                    Icon(Icons.Default.Add, contentDescription = "More servings")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NutrientValue("Calories", "${candidate.calories}", MacroColors.Calories)
                NutrientValue("Protein", "${candidate.protein.toInt()}g", MacroColors.Protein)
                NutrientValue("Carbs", "${candidate.carbs.toInt()}g", MacroColors.Carbs)
                NutrientValue("Fat", "${candidate.fat.toInt()}g", MacroColors.Fat)
            }

            if (detail != null && detail.hasAnyDetail) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { detailExpanded = !detailExpanded }) {
                    Text(if (detailExpanded) "Hide nutrition detail" else "Nutrition detail")
                }
                if (detailExpanded) {
                    NutritionDetailPanel(detail, candidate.quantity)
                }
            }
        }
    }
}

/**
 * Only renders values the source actually provided. A missing field is omitted rather than
 * shown as zero, because "not measured" and "contains none" are different claims.
 */
@Composable
private fun NutritionDetailPanel(detail: NutritionDetail, quantity: Float) {
    val rows = buildList {
        detail.fiberGrams?.let { add("Fibre" to "${(it * quantity).format1()} g") }
        detail.sugarGrams?.let { add("Sugars" to "${(it * quantity).format1()} g") }
        detail.saturatedFatGrams?.let { add("Saturated fat" to "${(it * quantity).format1()} g") }
        detail.sodiumMilligrams?.let { add("Sodium" to "${(it * quantity).toInt()} mg") }
        detail.cholesterolMilligrams?.let { add("Cholesterol" to "${(it * quantity).toInt()} mg") }
        detail.potassiumMilligrams?.let { add("Potassium" to "${(it * quantity).toInt()} mg") }
    }

    Column(Modifier.padding(top = 8.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Values not listed were not reported by ${detail.source.label}.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NutrientValue(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = color)
    }
}

private fun formatQuantity(q: Float): String =
    if (q % 1f == 0f) q.toInt().toString() else String.format("%.1f", q)

private fun Float.format1(): String =
    if (this % 1f == 0f) toInt().toString() else String.format("%.1f", this)
