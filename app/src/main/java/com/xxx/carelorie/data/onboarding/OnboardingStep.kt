package com.xxx.carelorie.data.onboarding

import com.xxx.carelorie.data.UserProfile

/** The three chapters the prototype groups its questions into. */
enum class OnboardingSection(val label: String) {
    BASICS("Basics"),
    GOALS("Goals"),
    PROGRAM("New Program")
}

/**
 * One question in the setup flow.
 *
 * Each step owns a [read] and a [write] against [UserProfile], so the host screen never needs to
 * know which field it is editing — it renders the step's shape and hands the answer back. Adding,
 * removing or reordering a question is an edit to [OnboardingFlow.steps] and nothing else.
 */
sealed class OnboardingStep {
    abstract val key: String
    abstract val section: OnboardingSection
    abstract val question: String
    open val note: String? = null

    /** True when this step has an answer already — drives the Next button and the progress bar. */
    abstract fun isAnswered(profile: UserProfile): Boolean

    /**
     * Pick one from a fixed list.
     *
     * [columns] is 1 for the stacked option lists and 3 for the body-fat grid, which is the only
     * structural difference between them in the prototype.
     */
    class Options(
        override val key: String,
        override val section: OnboardingSection,
        override val question: String,
        override val note: String? = null,
        val choices: List<Choice>,
        val columns: Int = 1,
        val read: (UserProfile) -> String?,
        val write: (UserProfile, String) -> UserProfile
    ) : OnboardingStep() {
        override fun isAnswered(profile: UserProfile) = !read(profile).isNullOrBlank()
    }

    /** A number with a sensible range — height, weight, target weight. */
    class Measure(
        override val key: String,
        override val section: OnboardingSection,
        override val question: String,
        override val note: String? = null,
        val unit: String,
        val range: ClosedFloatingPointRange<Float>,
        val decimals: Int,
        val read: (UserProfile) -> Float?,
        val write: (UserProfile, Float?) -> UserProfile
    ) : OnboardingStep() {
        override fun isAnswered(profile: UserProfile) = read(profile) != null
    }

    /** Date of birth. Stored on the profile as dd/MM/yyyy, matching the profile editor. */
    class BirthDate(
        override val key: String,
        override val section: OnboardingSection,
        override val question: String
    ) : OnboardingStep() {
        override fun isAnswered(profile: UserProfile) = profile.birthday.isNotBlank()
    }

    /** Free text — only the user's name. */
    class Name(
        override val key: String,
        override val section: OnboardingSection,
        override val question: String
    ) : OnboardingStep() {
        override fun isAnswered(profile: UserProfile) = profile.name.isNotBlank()
    }

    /**
     * A computed read-back rather than a question: the expenditure estimate and the final plan.
     *
     * Always "answered", because there is nothing to fill in.
     */
    class Summary(
        override val key: String,
        override val section: OnboardingSection,
        override val question: String,
        val kind: SummaryKind
    ) : OnboardingStep() {
        override fun isAnswered(profile: UserProfile) = true
    }

    enum class SummaryKind { EXPENDITURE, PLAN }
}

/**
 * The flow itself, in order.
 *
 * Follows the prototype's twenty screens, minus the ones the app already collects elsewhere. The
 * user can leave at any point: [OnboardingStep.isAnswered] is advisory, used to enable Next and to
 * show progress, never to block an exit.
 */
object OnboardingFlow {

    val steps: List<OnboardingStep> = listOf(

        // ------------------------------------------------------------------ Basics
        OnboardingStep.Name(
            key = "name",
            section = OnboardingSection.BASICS,
            question = "What should we call you?"
        ),
        OnboardingStep.Options(
            key = "gender",
            section = OnboardingSection.BASICS,
            question = "What is your gender?",
            note = "Used by the Mifflin-St Jeor equation to estimate your energy needs.",
            choices = Choices.Gender,
            read = { it.gender.ifBlank { null } },
            write = { p, v -> p.copy(gender = v) }
        ),
        OnboardingStep.BirthDate(
            key = "birthday",
            section = OnboardingSection.BASICS,
            question = "When were you born?"
        ),
        OnboardingStep.Measure(
            key = "height",
            section = OnboardingSection.BASICS,
            question = "What is your height?",
            unit = "cm",
            range = 120f..220f,
            decimals = 0,
            read = { it.height.trim().toFloatOrNull() },
            write = { p, v -> p.copy(height = v?.toInt()?.toString() ?: "") }
        ),
        OnboardingStep.Measure(
            key = "weight",
            section = OnboardingSection.BASICS,
            question = "What is your weight?",
            unit = "kg",
            range = 35f..200f,
            decimals = 1,
            read = { it.weight },
            write = { p, v -> p.copy(weight = v) }
        ),
        OnboardingStep.Options(
            key = "everWeighedOver95",
            section = OnboardingSection.BASICS,
            question = "Have you ever weighed more than 95 kg?",
            note = "Weight history can affect your current metabolism.",
            choices = Choices.EverWeighedOver95,
            read = { it.everWeighedOver95 },
            write = { p, v -> p.copy(everWeighedOver95 = v) }
        ),
        OnboardingStep.Options(
            key = "weightTrend",
            section = OnboardingSection.BASICS,
            question = "How has your weight trended over the past few weeks?",
            choices = Choices.WeightTrend,
            read = { it.weightTrend },
            write = { p, v -> p.copy(weightTrend = v) }
        ),
        OnboardingStep.Options(
            key = "bodyFat",
            section = OnboardingSection.BASICS,
            question = "What is your body fat level?",
            note = "An estimate is fine.",
            choices = Choices.BodyFat,
            columns = 3,
            read = { it.bodyFatBand },
            write = { p, v -> p.copy(bodyFatBand = v) }
        ),
        OnboardingStep.Options(
            key = "exerciseFrequency",
            section = OnboardingSection.BASICS,
            question = "How often do you exercise?",
            choices = Choices.ExerciseFrequency,
            read = { it.exerciseFrequency },
            write = { p, v -> p.copy(exerciseFrequency = v) }
        ),
        OnboardingStep.Options(
            key = "activityLevel",
            section = OnboardingSection.BASICS,
            question = "How active are you day to day?",
            choices = Choices.ActivityLevel,
            read = { it.activityLevel },
            write = { p, v -> p.copy(activityLevel = v) }
        ),
        OnboardingStep.Options(
            key = "liftingExperience",
            section = OnboardingSection.BASICS,
            question = "What is your experience with lifting?",
            choices = Choices.Experience,
            read = { it.liftingExperience.ifBlank { null } },
            write = { p, v -> p.copy(liftingExperience = v) }
        ),
        OnboardingStep.Options(
            key = "cardioExperience",
            section = OnboardingSection.BASICS,
            question = "What is your experience with cardio?",
            choices = Choices.Experience,
            read = { it.cardioExperience },
            write = { p, v -> p.copy(cardioExperience = v) }
        ),
        OnboardingStep.Summary(
            key = "expenditure",
            section = OnboardingSection.BASICS,
            question = "Based on your answers, this is our estimate",
            kind = OnboardingStep.SummaryKind.EXPENDITURE
        ),

        // ------------------------------------------------------------------ Goals
        OnboardingStep.Options(
            key = "goal",
            section = OnboardingSection.GOALS,
            question = "What is your goal?",
            choices = Choices.Goal,
            read = { it.goal },
            write = { p, v -> p.copy(goal = v) }
        ),
        OnboardingStep.Measure(
            key = "targetWeight",
            section = OnboardingSection.GOALS,
            question = "What is your target weight?",
            unit = "kg",
            range = 35f..200f,
            decimals = 1,
            read = { it.targetWeight ?: it.weight },
            write = { p, v -> p.copy(targetWeight = v) }
        ),

        // ------------------------------------------------------------------ Program
        OnboardingStep.Options(
            key = "dietType",
            section = OnboardingSection.PROGRAM,
            question = "What is your preferred diet?",
            note = "This sets how your calories are split between fat and carbohydrate.",
            choices = Choices.DietType,
            read = { it.dietType },
            write = { p, v -> p.copy(dietType = v) }
        ),
        OnboardingStep.Options(
            key = "trainingType",
            section = OnboardingSection.PROGRAM,
            question = "What training will you do during this program?",
            choices = Choices.TrainingType,
            read = { it.trainingType },
            write = { p, v -> p.copy(trainingType = v) }
        ),
        OnboardingStep.Options(
            key = "calorieDistribution",
            section = OnboardingSection.PROGRAM,
            question = "How would you like calories distributed through the week?",
            choices = Choices.CalorieDistribution,
            read = { it.calorieDistribution },
            write = { p, v -> p.copy(calorieDistribution = v) }
        ),
        OnboardingStep.Options(
            key = "proteinPreference",
            section = OnboardingSection.PROGRAM,
            question = "What is your preferred protein intake?",
            choices = Choices.ProteinPreference,
            read = { it.proteinPreference },
            write = { p, v -> p.copy(proteinPreference = v) }
        ),
        OnboardingStep.Summary(
            key = "plan",
            section = OnboardingSection.PROGRAM,
            question = "Here is your plan",
            kind = OnboardingStep.SummaryKind.PLAN
        )
    )

    /** 0f..1f across the whole flow, for the progress bar at the top of every step. */
    fun progressAt(index: Int): Float =
        ((index + 1).toFloat() / steps.size).coerceIn(0f, 1f)

    /**
     * The first question the user has not answered, or 0 when they have answered none.
     *
     * Lets someone who skipped setup resume where they stopped rather than starting over.
     */
    fun resumeIndex(profile: UserProfile): Int =
        steps.indexOfFirst { !it.isAnswered(profile) }.takeIf { it >= 0 } ?: 0
}
