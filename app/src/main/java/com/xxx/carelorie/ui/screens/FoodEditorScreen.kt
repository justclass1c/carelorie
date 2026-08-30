package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.viewmodels.FoodEditorEvent
import com.xxx.carelorie.ui.viewmodels.FoodEditorViewModel

/**
 * Create or edit one of the user's own foods.
 *
 * Opening a built-in preset seeds this form as a *new* copy — the original is a shared row and
 * editing it would change the dish for every user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorScreen(
    navController: NavController,
    userId: String,
    presetLocalId: String?,
    viewModel: FoodEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId, presetLocalId) {
        viewModel.onEvent(FoodEditorEvent.Load(userId, presetLocalId))
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) navController.popBackStack()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(FoodEditorEvent.ErrorConsumed)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditingExisting) "Edit food" else "New food")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { viewModel.onEvent(FoodEditorEvent.Save) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save food")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .constrainedWidth()
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (uiState.isCopyOfBuiltIn) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Carelorie presets are shared, so saving this creates your own copy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onEvent(FoodEditorEvent.NameChanged(it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.brand,
                    onValueChange = { viewModel.onEvent(FoodEditorEvent.BrandChanged(it)) },
                    label = { Text("Brand (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.servingDescription,
                    onValueChange = { viewModel.onEvent(FoodEditorEvent.ServingChanged(it)) },
                    label = { Text("Serving (optional)") },
                    placeholder = { Text("e.g. 1 plate (350 g)") },
                    supportingText = { Text("The amounts below are for one of these.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    "Per serving",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                NumberField(
                    value = uiState.calories,
                    onValueChange = { viewModel.onEvent(FoodEditorEvent.CaloriesChanged(it)) },
                    label = "Calories (kcal)",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = uiState.protein,
                        onValueChange = { viewModel.onEvent(FoodEditorEvent.ProteinChanged(it)) },
                        label = "Protein (g)",
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = uiState.carbs,
                        onValueChange = { viewModel.onEvent(FoodEditorEvent.CarbsChanged(it)) },
                        label = "Carbs (g)",
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = uiState.fat,
                        onValueChange = { viewModel.onEvent(FoodEditorEvent.FatChanged(it)) },
                        label = "Fat (g)",
                        modifier = Modifier.weight(1f)
                    )
                }

                // A hint, not a rule — real foods don't always add up exactly, so this offers
                // the calculated figure rather than overriding what was typed.
                val fromMacros = uiState.caloriesFromMacros
                val entered = uiState.calories.trim().toIntOrNull()
                if (fromMacros != null && entered != null && kotlin.math.abs(fromMacros - entered) > 30) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Those macros work out to about $fromMacros kcal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.onEvent(FoodEditorEvent.UseMacroCalories) }) {
                            Text("Use it")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth()
    )
}
