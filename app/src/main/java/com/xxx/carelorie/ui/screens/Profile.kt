package com.xxx.carelorie.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.xxx.carelorie.data.ThemeManager
import com.xxx.carelorie.ui.viewmodels.ProfileUiEvent
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

@Composable
fun Profile(navController: NavController, userId: String, viewModel: ProfileViewModel, isOnboarding: Boolean = false) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(userId) {
        viewModel.onEvent(ProfileUiEvent.LoadProfile(userId, isOnboarding))
    }

    // Cancel any unsaved edits when the user leaves the profile screen, when the app resumes
    // this screen, or when the screen is recomposed (e.g., navigation back/rotation).
    // This prevents half-edited data from surviving navigation and ensures the profile is
    // reloaded from the latest source when the user comes back. Onboarding keeps edit mode.
    DisposableEffect(lifecycleOwner, userId, isOnboarding) {
        if (!isOnboarding) {
            viewModel.onEvent(ProfileUiEvent.CancelEdit(userId))
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isOnboarding) {
                viewModel.onEvent(ProfileUiEvent.CancelEdit(userId))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isOnboarding) {
                viewModel.onEvent(ProfileUiEvent.CancelEdit(userId))
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onEvent(ProfileUiEvent.ErrorConsumed)
        }
    }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            navController.navigate("login") {
                popUpTo(0)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isOnboarding) {
                Text(
                    text = "Welcome! Create your profile!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile (ID: ${uiState.userId.take(8)}...)",
                    style = MaterialTheme.typography.headlineSmall
                )

                IconButton(onClick = { viewModel.onEvent(ProfileUiEvent.ToggleEditMode) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.name.ifEmpty { "Username" },
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(15.dp))

            val tabs = listOf("Personal", "Macros", "Settings")
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    if (uiState.isEditMode) {
                        ProfileEditSection(uiState = uiState, onEvent = viewModel::onEvent)
                    } else {
                        ProfileViewSection(uiState = uiState)
                    }
                }

                1 -> {
                    if (uiState.isEditMode) {
                        MacroLimitsEditSection(uiState = uiState, onEvent = viewModel::onEvent)
                    } else {
                        MacroLimitsView(uiState = uiState)
                    }
                }

                else -> {
                    ThemeSection(
                        theme = uiState.theme,
                        onThemeChange = { viewModel.onEvent(ProfileUiEvent.ThemeChanged(it)) }
                    )
                    if (!uiState.isEditMode) {
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.onEvent(ProfileUiEvent.Logout) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Logout")
                            }

                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete Account")
                            }
                        }
                    }
                }
            }

            if (uiState.isEditMode) {
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.onEvent(ProfileUiEvent.SaveProfile) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save Profile")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("This will permanently delete your profile and all your data. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.onEvent(ProfileUiEvent.DeleteAccount)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProfileViewSection(uiState: com.xxx.carelorie.ui.viewmodels.ProfileUiState) {
    Column {
        ProfileInfoRow(label = "Name", value = uiState.name)
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Birthday", value = uiState.birthday)
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Gender", value = uiState.gender)
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Height", value = if (uiState.height.isNotEmpty()) "${uiState.height} cm" else "")
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Weight", value = if (uiState.weight.isNotEmpty()) "${uiState.weight} kg" else "")
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Lifting Experience", value = if (uiState.liftingExperience.isNotEmpty()) "${uiState.liftingExperience} years" else "")
    }
}

@Composable
fun MacroLimitsView(uiState: com.xxx.carelorie.ui.viewmodels.ProfileUiState) {
    Column {
        ProfileInfoRow(label = "Calories", value = "${uiState.calorieLimit.ifEmpty { "-" }} kcal")
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Protein", value = "${uiState.proteinLimit.ifEmpty { "-" }} g")
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Carbs", value = "${uiState.carbsLimit.ifEmpty { "-" }} g")
        HorizontalDivider(Modifier.padding(vertical = 15.dp))
        ProfileInfoRow(label = "Fat", value = "${uiState.fatLimit.ifEmpty { "-" }} g")
    }
}

@Composable
fun ProfileEditSection(
    uiState: com.xxx.carelorie.ui.viewmodels.ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { onEvent(ProfileUiEvent.NameChanged(it)) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.birthday,
            onValueChange = { onEvent(ProfileUiEvent.BirthdayChanged(it)) },
            label = { Text("Birthday (dd/mm/yyyy)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(text = "Gender", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = uiState.gender == "Male",
                onClick = { onEvent(ProfileUiEvent.GenderChanged("Male")) }
            )
            Text("Male", modifier = Modifier.padding(end = 16.dp))
            
            RadioButton(
                selected = uiState.gender == "Female",
                onClick = { onEvent(ProfileUiEvent.GenderChanged("Female")) }
            )
            Text("Female")
        }

        OutlinedTextField(
            value = uiState.height,
            onValueChange = { onEvent(ProfileUiEvent.HeightChanged(it)) },
            label = { Text("Height (cm)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.weight,
            onValueChange = { onEvent(ProfileUiEvent.WeightChanged(it)) },
            label = { Text("Weight (kg)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.liftingExperience,
            onValueChange = { onEvent(ProfileUiEvent.ExperienceChanged(it)) },
            label = { Text("Lifting Experience (years)") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MacroLimitsEditSection(
    uiState: com.xxx.carelorie.ui.viewmodels.ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.calorieLimit,
            onValueChange = { onEvent(ProfileUiEvent.CalorieLimitChanged(it)) },
            label = { Text("Calories (kcal)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.proteinLimit,
            onValueChange = { onEvent(ProfileUiEvent.ProteinLimitChanged(it)) },
            label = { Text("Protein (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.carbsLimit,
            onValueChange = { onEvent(ProfileUiEvent.CarbsLimitChanged(it)) },
            label = { Text("Carbs (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.fatLimit,
            onValueChange = { onEvent(ProfileUiEvent.FatLimitChanged(it)) },
            label = { Text("Fat (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ThemeSection(theme: String, onThemeChange: (String) -> Unit) {
    val options = listOf(
        ThemeManager.THEME_SYSTEM to "System",
        ThemeManager.THEME_LIGHT to "Light",
        ThemeManager.THEME_DARK to "Dark"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = theme == value,
                    onClick = { onThemeChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value.ifEmpty { "-" },
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
