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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.xxx.carelorie.Routes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.xxx.carelorie.ui.components.LargeTitle
import com.xxx.carelorie.data.ThemeManager
import com.xxx.carelorie.ui.layout.ContentWidth
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.layout.isExpandedScreen
import com.xxx.carelorie.ui.viewmodels.ProfileUiEvent
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

private val MEMBER_SINCE = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun Profile(navController: NavController, userId: String, viewModel: ProfileViewModel, isOnboarding: Boolean = false) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
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
            navController.navigate(Routes.LOGIN) {
                popUpTo(0)
            }
            // Consume it. The ViewModel belongs to the Activity, so an unconsumed flag survived
            // the trip through the login screen and bounced the next session straight back out
            // of the profile tab.
            viewModel.onEvent(ProfileUiEvent.LogoutHandled)
        }
    }

    LaunchedEffect(uiState.isSaveSuccess) {
        if (!uiState.isSaveSuccess) return@LaunchedEffect
        if (isOnboarding) {
            // Finish the sign-up flow instead of leaving the new user parked on this form with
            // no sign that they were done. popUpTo(0) so back cannot return to onboarding.
            navController.navigate(Routes.DASHBOARD) { popUpTo(0) }
        }
        // Consume it either way, so an ordinary profile edit doesn't leave the flag set.
        viewModel.onEvent(ProfileUiEvent.ResetSaveStatus)
    }

    // Expanded windows have room to show Personal and Macros at once, so the tab row only
    // earns its place on narrower screens.
    val twoColumnSections = isExpandedScreen

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .constrainedWidth(if (twoColumnSections) ContentWidth.Reading else ContentWidth.Form)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
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

            LargeTitle(
                title = "Profile",
                trailing = {
                    // A tinted circle rather than a bare glyph: the edit affordance was the same
                    // weight as the decorative icons elsewhere on this screen.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(onClick = { viewModel.onEvent(ProfileUiEvent.ToggleEditMode) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = uiState.name.ifEmpty { "Username" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.stats.memberSince?.let { since ->
                                Text(
                                    text = "Member since ${since.format(MEMBER_SINCE)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCell("Active streak", uiState.stats.activeStreak, Modifier.weight(1f))
                        StatCell("Longest streak", uiState.stats.longestStreak, Modifier.weight(1f))
                        StatCell("Days tracked", uiState.stats.totalTracked, Modifier.weight(1f))
                    }
                }
            }

            // Offered until the plan is complete. Onboarding is skippable, so this is the way
            // back into it — and it says what finishing actually buys, rather than nagging.
            if (!uiState.hasCompletedOnboarding) {
                Spacer(Modifier.height(12.dp))
                SetUpPlanCard(
                    progress = uiState.onboardingProgress,
                    onStart = { navController.navigate(Routes.ONBOARDING) }
                )
            }

            Spacer(Modifier.height(15.dp))

            val tabs = if (twoColumnSections) listOf("Details", "Settings") else listOf("Personal", "Macros", "Settings")

            // Resizing across the breakpoint changes how many tabs there are, so clamp rather
            // than leaving no tab highlighted on the frame the window changes.
            val activeTab = selectedTab.coerceAtMost(tabs.lastIndex)

            PrimaryTabRow(selectedTabIndex = activeTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val settingsTabIndex = tabs.lastIndex

            when {
                activeTab == 0 && twoColumnSections -> {
                    // Wide: personal details and macro limits share the row instead of
                    // hiding behind separate tabs.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (uiState.isEditMode) {
                                ProfileEditSection(uiState = uiState, onEvent = viewModel::onEvent)
                            } else {
                                ProfileViewSection(uiState = uiState)
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            if (uiState.isEditMode) {
                                MacroLimitsEditSection(uiState = uiState, onEvent = viewModel::onEvent)
                            } else {
                                MacroLimitsView(uiState = uiState)
                            }
                        }
                    }
                }

                activeTab == 0 -> {
                    if (uiState.isEditMode) {
                        ProfileEditSection(uiState = uiState, onEvent = viewModel::onEvent)
                    } else {
                        ProfileViewSection(uiState = uiState)
                    }
                }

                activeTab < settingsTabIndex -> {
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
                        // Food Query used to be linked from here; it is a nav tab now, so a
                        // second link from a sibling top-level destination is just clutter.
                        Spacer(Modifier.height(24.dp))
                        RecoveryKeySection(
                            hasKey = uiState.hasRecoveryKey,
                            onRegenerate = { viewModel.onEvent(ProfileUiEvent.RegenerateRecoveryKeyClicked) }
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?") },
            text = { Text("You will need to sign in again to view your data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.onEvent(ProfileUiEvent.Logout)
                    }
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (uiState.recoveryKey.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ProfileUiEvent.RecoveryKeyDismissed) },
            title = { Text("Your recovery key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This key is shown only once. Save it somewhere safe — it can be used to reset your password if you ever forget it. It cannot be viewed again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            uiState.recoveryKey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ProfileUiEvent.RecoveryKeyDismissed) }) {
                    Text("I've saved it")
                }
            }
        )
    }

    if (uiState.showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ProfileUiEvent.RegenerateDialogDismissed) },
            title = { Text("Reset recovery key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your current password to generate a new recovery key. The old key stops working immediately.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = uiState.regeneratePassword,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.RegeneratePasswordChanged(it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (uiState.regeneratePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (uiState.regeneratePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { viewModel.onEvent(ProfileUiEvent.ToggleRegeneratePasswordVisibility) }) {
                                Icon(imageVector = icon, contentDescription = "Toggle password visibility")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    uiState.regenerateError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onEvent(ProfileUiEvent.ConfirmRegenerateRecoveryKey) },
                    enabled = !uiState.regenerateLoading
                ) {
                    if (uiState.regenerateLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Generate")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ProfileUiEvent.RegenerateDialogDismissed) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** One of the three counters in the profile header. */
@Composable
private fun StatCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Prompt to finish onboarding, shown while the plan is incomplete. */
@Composable
private fun SetUpPlanCard(progress: Float, onStart: () -> Unit) {
    val started = progress > 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (started) "Finish setting up your plan" else "Set up your plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A few questions about your body, training and goal. We use them to work " +
                    "out your daily targets and to give the AI coach something real to work with.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (started) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}% complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart) {
                Text(if (started) "Continue" else "Get started")
            }
        }
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
fun RecoveryKeySection(hasKey: Boolean, onRegenerate: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recovery Key",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasKey) {
                "Your recovery key was shown once when you first opened your profile. It can reset your password but can never be viewed again. If you lost it, generate a new one (your current password is required)."
            } else {
                "A recovery key is generated and shown once on your first visit. It can be used to reset your password."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRegenerate,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text("Reset recovery key")
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
