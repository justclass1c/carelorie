package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xxx.carelorie.Routes
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.data.onboarding.Choice
import com.xxx.carelorie.data.onboarding.OnboardingStep
import com.xxx.carelorie.ui.layout.ContentWidth
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.OnboardingEvent
import com.xxx.carelorie.ui.viewmodels.OnboardingUiState
import com.xxx.carelorie.ui.viewmodels.OnboardingViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val UI_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * The setup flow.
 *
 * Every step carries a Skip, and the whole thing carries "Skip for now" — a user can reach the
 * dashboard without answering anything and finish later from their profile. What they do answer
 * is kept, and reopening resumes at the first blank question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    userId: String,
    viewModel: OnboardingViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) { viewModel.onEvent(OnboardingEvent.Start(userId)) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            navController.navigate(Routes.DASHBOARD) { popUpTo(0) }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Consume before awaiting: showSnackbar suspends until the bar goes away, so
            // leaving the screen cancels this effect and the message would stay set and
            // replay every time you came back.
            viewModel.onEvent(OnboardingEvent.ErrorConsumed)
            snackbarHostState.showSnackbar(it)
        }
    }

    val step = uiState.step
    if (uiState.isLoading || step == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(step.section.label) },
                navigationIcon = {
                    if (!uiState.isFirstStep) {
                        IconButton(onClick = { viewModel.onEvent(OnboardingEvent.Back) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.onEvent(OnboardingEvent.SkipAll) }) {
                        Text("Skip for now")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .constrainedWidth(ContentWidth.Form)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )

                Text(
                    text = step.question,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                step.note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    StepBody(
                        step = step,
                        uiState = uiState,
                        onAnswer = { viewModel.onEvent(OnboardingEvent.Answer(it)) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A question can always be left blank: the flow is a convenience, not a gate.
                    if (!uiState.currentAnswered && step !is OnboardingStep.Summary) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(OnboardingEvent.Next) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Skip") }
                    }

                    Button(
                        onClick = {
                            if (uiState.isLastStep) viewModel.onEvent(OnboardingEvent.Finish)
                            else viewModel.onEvent(OnboardingEvent.Next)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (uiState.isLastStep) "Start tracking" else "Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBody(
    step: OnboardingStep,
    uiState: OnboardingUiState,
    onAnswer: (Any?) -> Unit
) {
    val draft = uiState.draft ?: return

    when (step) {
        is OnboardingStep.Options -> OptionsBody(
            choices = step.choices,
            columns = step.columns,
            selectedId = step.read(draft),
            onSelect = onAnswer
        )

        is OnboardingStep.Measure -> MeasureBody(
            unit = step.unit,
            range = step.range,
            decimals = step.decimals,
            value = step.read(draft),
            onChange = onAnswer
        )

        is OnboardingStep.BirthDate -> BirthDateBody(
            value = draft.birthday,
            onChange = onAnswer
        )

        is OnboardingStep.Name -> OutlinedTextField(
            value = draft.name,
            onValueChange = onAnswer,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        is OnboardingStep.Summary -> SummaryBody(step.kind, uiState)
    }
}

@Composable
private fun OptionsBody(
    choices: List<Choice>,
    columns: Int,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    if (columns > 1) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(choices) { choice ->
                ChoiceCard(
                    choice = choice,
                    selected = choice.id == selectedId,
                    compact = true,
                    onClick = { onSelect(choice.id) }
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            choices.forEach { choice ->
                ChoiceCard(
                    choice = choice,
                    selected = choice.id == selectedId,
                    compact = false,
                    onClick = { onSelect(choice.id) }
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    choice: Choice,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                )
                choice.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!compact) {
                RadioButton(selected = selected, onClick = onClick)
            }
        }
    }
}

/**
 * Slider plus a text field, as the prototype has it — the slider for a quick approximate answer
 * and the field for anyone who knows their exact number.
 */
@Composable
private fun MeasureBody(
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    value: Float?,
    onChange: (Float?) -> Unit
) {
    val midpoint = (range.start + range.endInclusive) / 2f
    val current = value ?: midpoint
    var typed by remember(value == null) { mutableStateOf(value?.let { format(it, decimals) } ?: "") }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "${format(current, decimals)} $unit",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Slider(
            value = current,
            onValueChange = {
                val snapped = if (decimals == 0) it.roundToInt().toFloat() else (it * 10f).roundToInt() / 10f
                typed = format(snapped, decimals)
                onChange(snapped)
            },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )

        Text(
            text = "or type it in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { text ->
                typed = text
                val parsed = text.trim().toFloatOrNull()
                // Out-of-range typing is ignored rather than clamped: silently rewriting what
                // someone typed is worse than waiting for them to finish.
                if (parsed != null && parsed in range) onChange(parsed)
            },
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimals == 0) KeyboardType.Number else KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateBody(value: String, onChange: (String) -> Unit) {
    val initialMillis = remember(value) {
        runCatching { LocalDate.parse(value, UI_DATE) }.getOrNull()
            ?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        // Nobody logging calories was born this decade, and a future date is never right.
        yearRange = (LocalDate.now().year - 100)..(LocalDate.now().year - 10)
    )

    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis?.let { millis ->
            val date = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
            onChange(date.format(UI_DATE))
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        DatePicker(state = state, title = null, headline = null, showModeToggle = true)
    }
}

@Composable
private fun SummaryBody(kind: OnboardingStep.SummaryKind, uiState: OnboardingUiState) {
    val tdee = uiState.estimatedTdee
    val targets = uiState.previewTargets

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (tdee == null) {
            SummaryCard(
                heading = "Not enough to estimate yet",
                body = "Add your height, weight, date of birth and gender and we can work out " +
                    "your daily energy needs. You can do this later from your profile — the app " +
                    "works fine on the default targets until then."
            )
            return@Column
        }

        when (kind) {
            OnboardingStep.SummaryKind.EXPENDITURE -> {
                BigNumberCard(value = "$tdee", unit = "kcal / day", caption = "Estimated expenditure")
                SummaryCard(
                    heading = "How we got there",
                    body = "Mifflin-St Jeor for your resting rate, adjusted for how often you " +
                        "train and how much you move day to day. It is an estimate — your logged " +
                        "weight trend is what refines it."
                )
            }

            OnboardingStep.SummaryKind.PLAN -> {
                BigNumberCard(
                    value = "${targets.calories}",
                    unit = "kcal / day",
                    caption = "Your daily target"
                )
                MacroBreakdown(targets)
                SummaryCard(
                    heading = "You can change any of this",
                    body = "Targets live in your profile and can be edited by hand at any time. " +
                        "Your answers also let the AI coach give advice based on your actual " +
                        "goal and training rather than generic tips."
                )
            }
        }
    }
}

@Composable
private fun BigNumberCard(value: String, unit: String, caption: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MacroBreakdown(targets: NutritionTargets) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MacroPill("Protein", "${targets.proteinGrams.toInt()}g", MacroColors.Protein, Modifier.weight(1f))
        MacroPill("Carbs", "${targets.carbsGrams.toInt()}g", MacroColors.Carbs, Modifier.weight(1f))
        MacroPill("Fat", "${targets.fatGrams.toInt()}g", MacroColors.Fat, Modifier.weight(1f))
    }
}

@Composable
private fun MacroPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryCard(heading: String, body: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun format(value: Float, decimals: Int): String =
    if (decimals == 0) value.roundToInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
