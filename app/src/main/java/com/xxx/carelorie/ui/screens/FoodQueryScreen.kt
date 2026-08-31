package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xxx.carelorie.Routes
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.xxx.carelorie.data.local.FoodPresetEntity
import com.xxx.carelorie.data.nutrition.FoodCandidate
import androidx.compose.ui.graphics.Color
import com.xxx.carelorie.ui.components.LargeTitle
import com.xxx.carelorie.ui.layout.ContentWidth
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.components.food.PhotoSourceDialog
import com.xxx.carelorie.ui.util.rememberFoodPhotoCapture
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.FoodQueryEvent
import com.xxx.carelorie.ui.viewmodels.FoodQueryViewModel

/**
 * The user's food library.
 *
 * Split into foods they created — which they can edit and delete — and the dishes that ship
 * with the app, which are shared between every user and so are read-only. Copying a built-in
 * gives them an editable version rather than changing a dish for everybody.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodQueryScreen(
    navController: NavController,
    userId: String,
    viewModel: FoodQueryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun startScan() {
        GmsBarcodeScanning.getClient(context)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { viewModel.onEvent(FoodQueryEvent.BarcodeScanned(it)) }
            }
            .addOnFailureListener {
                // Empty string routes to the "cancelled or unreadable" message.
                viewModel.onEvent(FoodQueryEvent.BarcodeScanned(""))
            }
    }

    val photoCapture = rememberFoodPhotoCapture(
        onImage = { viewModel.onEvent(FoodQueryEvent.PhotoTaken(it)) },
        onError = { viewModel.onEvent(FoodQueryEvent.PhotoFailed(it)) }
    )
    var photoSourceOpen by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.onEvent(FoodQueryEvent.Start(userId))
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        val canUndo = uiState.lastDeleted != null

        // Clear the message before awaiting the result, not after. showSnackbar suspends for the
        // whole time the bar is up, so leaving the tab cancels this effect mid-wait — and the
        // message stayed set, which is why "Deleted …" reappeared on every return to the screen
        // and never went away.
        viewModel.onEvent(FoodQueryEvent.MessageShown)

        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (canUndo) "Undo" else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onEvent(FoodQueryEvent.UndoDelete)
        } else {
            viewModel.onEvent(FoodQueryEvent.MessageConsumed)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // No topBar. This is a tab, and the other four open with a large title inside their own
        // scroll rather than a Material title bar — having one here shifted the content down and
        // changed the typography every time you switched to it.
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.foodEditor()) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add food") }
            )
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
                    .constrainedWidth(ContentWidth.Reading)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                LargeTitle(
                    title = "Presets",
                    subtitle = "Your library and the built-in dishes",
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                )

                // Independent, not either/or. Being offline and having rows the server has
                // rejected are separate facts, and both stay true at the same time. Rendering
                // them as a chain meant one failed connectivity probe swapped the warning for
                // the offline notice and back, which read as the red bar flickering.
                if (uiState.isOffline) {
                    Notice("Offline — changes will sync when you reconnect.")
                    Spacer(Modifier.height(8.dp))
                }

                if (uiState.showUnsyncedWarning) {
                    // The server rejected the write. Saying so beats a row that quietly never
                    // leaves the phone.
                    Notice(
                        "${uiState.unsyncedCount} food(s) saved here but not accepted by the " +
                            "server. Check the food_presets table has the brand and " +
                            "servingDescription columns and an insert policy.",
                        warning = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.onEvent(FoodQueryEvent.QueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search foods") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.onEvent(FoodQueryEvent.SearchOnline) }
                    ),
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(Modifier.height(12.dp))

                // Same three ways in as the food search screen. The difference is what happens
                // to a result: there you log it, here there is no meal to log into, so a result
                // is added to your library instead.
                // Three chips overflow a narrow phone, so the row scrolls rather than
                // clipping the last one.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { viewModel.onEvent(FoodQueryEvent.SearchOnline) },
                        label = { Text("Search online") },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { startScan() },
                        label = { Text("Scan QR") },
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { viewModel.onEvent(FoodQueryEvent.AiSearch) },
                        label = { Text("Estimate with AI") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { photoSourceOpen = true },
                        label = { Text("Scan Food") },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)) }
                    )
                }

                if (uiState.isBusy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (uiState.isAnalysing) "Asking A.I…" else "Searching…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (uiState.isLoading && uiState.allPresets.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                // Search results still need showing even when nothing in the library matches —
                // that is exactly the case where you searched online because you had nothing.
                if (uiState.isEmpty && !uiState.hasResults) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (uiState.query.isBlank()) {
                                "No foods yet. Tap Add food to create one."
                            } else {
                                "Nothing matches \"${uiState.query}\". Try Search online or AI."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    return@Column
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.hasResults) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader(
                                    uiState.resultsLabel ?: "Search results",
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { viewModel.onEvent(FoodQueryEvent.ClearResults) }
                                ) {
                                    Text("Clear")
                                }
                            }
                        }

                        items(uiState.searchResults, key = { it.selectionId }) { candidate ->
                            ResultRow(
                                candidate = candidate,
                                onAdd = { viewModel.onEvent(FoodQueryEvent.AddResult(candidate)) }
                            )
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    item {
                        SectionHeader("Your foods (${uiState.ownFoods.size})")
                    }

                    if (uiState.ownFoods.isEmpty()) {
                        item {
                            Text(
                                if (uiState.hasNoCustomFoods) {
                                    "You haven't created any foods yet."
                                } else {
                                    "None of your foods match that search."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    items(uiState.ownFoods, key = { it.localId }) { preset ->
                        PresetRow(
                            preset = preset,
                            onEdit = { navController.navigate(Routes.foodEditor(preset.localId)) },
                            onDelete = { viewModel.onEvent(FoodQueryEvent.Delete(preset)) },
                            onCopy = null
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("Carelorie presets (${uiState.builtInFoods.size})")
                        Text(
                            "Shared by everyone, so these can't be changed. Copy one to make it yours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(uiState.builtInFoods, key = { it.localId }) { preset ->
                        PresetRow(
                            preset = preset,
                            onEdit = null,
                            onDelete = null,
                            onCopy = { viewModel.onEvent(FoodQueryEvent.CopyToOwnFoods(preset)) }
                        )
                    }
                }
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

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

/**
 * A food found online, by barcode or by AI. Not in the library yet — the button puts it there.
 */
@Composable
private fun ResultRow(candidate: FoodCandidate, onAdd: () -> Unit) {
    val preset = candidate.preset
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
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroLabel("P", "${preset.protein.toInt()}g", MacroColors.Protein)
                    MacroLabel("C", "${preset.carbs.toInt()}g", MacroColors.Carbs)
                    MacroLabel("F", "${preset.fat.toInt()}g", MacroColors.Fat)
                    MacroLabel("Cal", "${preset.calories}", MacroColors.Calories)
                }
                candidate.detail?.servingDescription?.let {
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Says where the numbers came from, so an AI guess is never mistaken for a
                // measured value once it is sitting in the library.
                candidate.detail?.source?.let { source ->
                    Text(
                        text = source.label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean = false) {
    Surface(
        color = if (warning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (warning) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun PresetRow(
    preset: FoodPresetEntity,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCopy: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                preset.brand?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroLabel("P", "${preset.protein.toInt()}g", MacroColors.Protein)
                    MacroLabel("C", "${preset.carbs.toInt()}g", MacroColors.Carbs)
                    MacroLabel("F", "${preset.fat.toInt()}g", MacroColors.Fat)
                    MacroLabel("Cal", "${preset.calories}", MacroColors.Calories)
                }
                preset.servingDescription?.let {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!preset.isSynced) {
                    Text(
                        text = "Not synced yet",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            onCopy?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy to my foods")
                }
            }
            onEdit?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${preset.name}")
                }
            }
            onDelete?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${preset.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroLabel(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
