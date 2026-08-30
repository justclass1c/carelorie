package com.xxx.carelorie.ui.screens

import android.app.Activity
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.xxx.carelorie.Routes
import com.xxx.carelorie.data.nutrition.FoodCandidate
import com.xxx.carelorie.ui.components.dashboard.MEAL_TYPES
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.FoodSearchEvent
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    navController: NavController,
    userId: String,
    mealType: String,
    viewModel: FoodSearchViewModel,
    isWideScreen: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mealMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(userId, mealType) {
        viewModel.onEvent(FoodSearchEvent.MealTypeChanged(mealType))
        viewModel.onEvent(FoodSearchEvent.LoadPresets(userId))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(FoodSearchEvent.MessageConsumed)
        }
    }

    fun startScan() {
        // Google Code Scanner provides the whole scanning UI and needs no CAMERA permission.
        GmsBarcodeScanning.getClient(context)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { viewModel.onEvent(FoodSearchEvent.BarcodeScanned(it)) }
            }
            .addOnFailureListener {
                viewModel.onEvent(
                    FoodSearchEvent.BarcodeScanned("") // triggers the not-found message path
                )
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { mealMenuOpen = true }
                        ) {
                            Text(uiState.mealType, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change meal")
                        }
                        DropdownMenu(
                            expanded = mealMenuOpen,
                            onDismissRequest = { mealMenuOpen = false }
                        ) {
                            MEAL_TYPES.forEach { meal ->
                                DropdownMenuItem(
                                    text = { Text(meal) },
                                    trailingIcon = {
                                        if (meal == uiState.mealType) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.onEvent(FoodSearchEvent.MealTypeChanged(meal))
                                        mealMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // The tick from the sketch: goes to Review before anything is logged.
                    BadgedBox(
                        badge = {
                            if (uiState.hasSelection) {
                                Badge { Text("${uiState.selectedCount}") }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { navController.navigate(Routes.REVIEW_FOODS) },
                            enabled = uiState.hasSelection
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Review selected foods")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onEvent(FoodSearchEvent.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search food") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.onEvent(FoodSearchEvent.SearchOnline) }
                ),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { viewModel.onEvent(FoodSearchEvent.SearchOnline) },
                    label = { Text("Search online") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { startScan() },
                    label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { viewModel.onEvent(FoodSearchEvent.AiSearch) },
                    label = { Text("Search With AI") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
                )
            }

            if (uiState.isAnalysing) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Asking A.I…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (uiState.isLoading && uiState.results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No foods to show. Try searching online or scanning a barcode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.results, key = { it.selectionId }) { candidate ->
                        SelectableFoodRow(
                            candidate = candidate,
                            isSelected = uiState.isSelected(candidate),
                            onToggle = { viewModel.onEvent(FoodSearchEvent.ToggleSelection(candidate)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableFoodRow(
    candidate: FoodCandidate,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val preset = candidate.preset
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroTag("P", "${preset.protein.toInt()}g", MacroColors.Protein)
                    MacroTag("C", "${preset.carbs.toInt()}g", MacroColors.Carbs)
                    MacroTag("F", "${preset.fat.toInt()}g", MacroColors.Fat)
                    MacroTag("Cal", "${preset.calories}", MacroColors.Calories)
                }
                candidate.detail?.source?.let { source ->
                    Text(
                        text = source.label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroTag(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
