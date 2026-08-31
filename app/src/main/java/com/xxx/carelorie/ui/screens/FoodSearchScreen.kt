package com.xxx.carelorie.ui.screens

import android.app.Activity
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.xxx.carelorie.ui.components.food.PhotoSourceDialog
import com.xxx.carelorie.ui.util.rememberFoodPhotoCapture
import com.xxx.carelorie.data.nutrition.FoodCandidate
import com.xxx.carelorie.ui.components.dashboard.MEAL_TYPES
import com.xxx.carelorie.ui.layout.isExpandedScreen
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.FoodSearchEvent
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import com.xxx.carelorie.ui.viewmodels.PresetFilter
import com.xxx.carelorie.ui.viewmodels.SearchMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    navController: NavController,
    userId: String,
    mealType: String,
    logDate: LocalDate = LocalDate.now(),
    viewModel: FoodSearchViewModel
) {
    val twoPane = isExpandedScreen
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mealMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(userId, mealType, logDate) {
        viewModel.onEvent(FoodSearchEvent.Start(userId, mealType, logDate))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            // Consume before awaiting: showSnackbar suspends until the bar goes away, so
            // leaving the screen cancels this effect and the message would stay set and
            // replay every time you came back.
            viewModel.onEvent(FoodSearchEvent.MessageConsumed)
            snackbarHostState.showSnackbar(it)
        }
    }

    val photoCapture = rememberFoodPhotoCapture(
        onImage = { viewModel.onEvent(FoodSearchEvent.PhotoCaptured(it)) },
        onError = { viewModel.onEvent(FoodSearchEvent.PhotoFailed(it)) }
    )
    var photoSourceOpen by remember { mutableStateOf(false) }

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
                            Column {
                                Text(uiState.mealType, fontWeight = FontWeight.Medium)
                                if (uiState.logDate != LocalDate.now()) {
                                    Text(
                                        text = uiState.logDate.format(
                                            DateTimeFormatter.ofPattern("d MMM yyyy")
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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

            // Which slice of the local library to show. Custom foods have to be findable at the
            // moment of logging, not only from the manage screen.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresetFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.mode == SearchMode.PRESETS &&
                            uiState.presetFilter == filter,
                        onClick = { viewModel.onEvent(FoodSearchEvent.PresetFilterChanged(filter)) },
                        label = { Text(filter.label) }
                    )
                }
                Spacer(Modifier.weight(1f))
                // Straight to the editor rather than the library: you searched and didn't find
                // it, so creating one is the action you actually want. The library itself is a
                // nav tab now.
                TextButton(onClick = { navController.navigate(Routes.foodEditor()) }) {
                    Text("+ New")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Three chips overflow a narrow phone, so the row scrolls rather than
            // clipping the last one.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                    label = { Text("Estimate with AI") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { photoSourceOpen = true },
                    label = { Text("Scan food") },
                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)) }
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

            val resultsPane: @Composable (Modifier) -> Unit = { paneModifier ->
                when {
                    uiState.isLoading && uiState.results.isEmpty() ->
                        Box(paneModifier, contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    uiState.results.isEmpty() ->
                        Box(paneModifier, contentAlignment = Alignment.Center) {
                            Text(
                                "No foods to show. Try searching online or scanning a barcode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                    else -> LazyColumn(
                        modifier = paneModifier,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.results, key = { it.selectionId }) { candidate ->
                            SelectableFoodRow(
                                candidate = candidate,
                                isSelected = uiState.isSelected(candidate),
                                onToggle = {
                                    viewModel.onEvent(FoodSearchEvent.ToggleSelection(candidate))
                                }
                            )
                        }
                    }
                }
            }

            if (twoPane) {
                // Wide: what you have picked stays visible beside the results, so you don't
                // have to leave the screen to check the running selection.
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    resultsPane(Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    SelectionPane(
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                        uiState = uiState,
                        onRemove = { viewModel.onEvent(FoodSearchEvent.ToggleSelection(it)) },
                        onReview = { navController.navigate(Routes.REVIEW_FOODS) }
                    )
                }
            } else {
                resultsPane(Modifier.fillMaxSize())
            }
        }
    }
    if (photoSourceOpen) {
        PhotoSourceDialog(
            onDismiss = { photoSourceOpen = false },
            onCamera = photoCapture.takePhoto,
            onGallery = photoCapture.pickPhoto
        )
    }
}

/** The running selection, shown alongside the results on wide screens. */
@Composable
private fun SelectionPane(
    modifier: Modifier = Modifier,
    uiState: com.xxx.carelorie.ui.viewmodels.FoodSearchUiState,
    onRemove: (FoodCandidate) -> Unit,
    onReview: () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = "Selected (${uiState.selectedCount})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))

        if (!uiState.hasSelection) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing selected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.selectedList, key = { it.selectionId }) { candidate ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    candidate.preset.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${candidate.calories} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemove(candidate) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "${uiState.totalCalories} kcal total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onReview,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text("Review ${uiState.selectedCount}")
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
        shape = RoundedCornerShape(16.dp),
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
