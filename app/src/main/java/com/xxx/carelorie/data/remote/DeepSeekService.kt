package com.xxx.carelorie.data.remote

import android.util.Log
import com.xxx.carelorie.BuildConfig
import com.xxx.carelorie.data.UserProfile
import com.xxx.carelorie.data.onboarding.Choices
import com.xxx.carelorie.data.onboarding.TdeeCalculator.ageYears
import com.xxx.carelorie.data.onboarding.TdeeCalculator.heightCm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<DeepSeekMessage>,
    val max_tokens: Int = 220
)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
data class DeepSeekResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
    val error: DeepSeekError? = null
)

@Serializable
data class DeepSeekChoice(
    val message: DeepSeekMessage
)

@Serializable
data class DeepSeekError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/**
 * Everything the coach knows about a user, assembled once and reused by every AI feature.
 *
 * Built from a [UserProfile], so onboarding answers flow into the prompts automatically — and a
 * user who skipped onboarding simply produces a shorter briefing rather than a broken one. That
 * is the whole point of [describe]: it emits only the lines it actually has answers for.
 */
data class CoachContext(
    val profile: UserProfile,
    val weightHistoryLast7Days: List<Pair<String, Float>> = emptyList()
) {
    val bmi: Float? = run {
        val h = profile.heightCm
        val w = profile.weight
        if (h != null && w != null && h > 0f) w / ((h / 100f) * (h / 100f)) else null
    }

    val bmiCategory: String? = bmi?.let {
        when {
            it < 18.5f -> "Underweight"
            it < 25f -> "Normal weight"
            it < 30f -> "Overweight"
            else -> "Obese"
        }
    }

    /** Net change over the window, or null when there are not two points to compare. */
    val weightChange: Float? =
        if (weightHistoryLast7Days.size >= 2) {
            weightHistoryLast7Days.last().second - weightHistoryLast7Days.first().second
        } else null

    /**
     * The briefing sent to the model.
     *
     * Every line is conditional. With onboarding skipped this is three or four lines; with it
     * complete it is a full profile, and the advice gets correspondingly specific.
     */
    fun describe(): String {
        val lines = mutableListOf<String>()

        fun add(label: String, value: String?) {
            if (!value.isNullOrBlank()) lines += "$label: $value"
        }

        add("Name", profile.name.ifBlank { null })
        add("Gender", profile.gender.ifBlank { null })
        profile.ageYears?.let { add("Age", "$it") }
        profile.heightCm?.let { add("Height", "${fmt(it)} cm") }
        profile.weight?.let { add("Current weight", "${fmt(it)} kg") }
        bmi?.let { add("BMI", "${fmt(it)} ($bmiCategory)") }

        add("Goal", Choices.labelFor(Choices.Goal, profile.goal))
        profile.targetWeight?.let { add("Target weight", "${fmt(it)} kg") }
        add("Recent weight trend", Choices.labelFor(Choices.WeightTrend, profile.weightTrend))
        add("Has weighed over 95 kg before", Choices.labelFor(Choices.EverWeighedOver95, profile.everWeighedOver95))
        add("Body fat", Choices.labelFor(Choices.BodyFat, profile.bodyFatBand))

        add("Training sessions per week", Choices.labelFor(Choices.ExerciseFrequency, profile.exerciseFrequency))
        add("Daily activity", Choices.labelFor(Choices.ActivityLevel, profile.activityLevel))
        add("Training type", Choices.labelFor(Choices.TrainingType, profile.trainingType))
        add("Lifting experience", Choices.labelFor(Choices.Experience, profile.liftingExperience)
            ?: profile.liftingExperience.ifBlank { null })
        add("Cardio experience", Choices.labelFor(Choices.Experience, profile.cardioExperience))

        add("Preferred diet", Choices.labelFor(Choices.DietType, profile.dietType))
        add("Protein preference", Choices.labelFor(Choices.ProteinPreference, profile.proteinPreference))
        profile.estimatedTdee?.let { add("Estimated daily expenditure", "$it kcal") }
        add("Daily targets", "${profile.calorieLimit} kcal, ${fmt(profile.proteinLimit)}g protein, " +
            "${fmt(profile.carbsLimit)}g carbs, ${fmt(profile.fatLimit)}g fat")

        if (weightHistoryLast7Days.isNotEmpty()) {
            lines += "Recent weight log:"
            weightHistoryLast7Days.forEach { (date, kg) -> lines += "  $date: ${fmt(kg)} kg" }
            weightChange?.let {
                val direction = when {
                    it > 0.05f -> "up ${fmt(it)} kg"
                    it < -0.05f -> "down ${fmt(-it)} kg"
                    else -> "unchanged"
                }
                lines += "Net change over the window: $direction"
            }
        }

        return lines.joinToString("\n")
    }

    /** True once there is enough here for advice to be worth more than a generic platitude. */
    val isRichEnough: Boolean
        get() = profile.weight != null && profile.heightCm != null

    private fun fmt(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
}

/**
 * DeepSeek-backed coaching.
 *
 * Never logs prompts or responses: they carry the user's name, body measurements and health
 * profile, and logcat is readable by other processes on some devices. Failures log the error
 * only, which is what is actually useful for debugging.
 */
class DeepSeekService {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            })
        }
    }

    private val apiKey = BuildConfig.DEEPSEEK_API_KEY

    val isConfigured: Boolean get() = apiKey.isNotEmpty()

    /**
     * A short, personal read on how the user's weight is tracking against their goal.
     *
     * Returns null when the key is missing, the profile is too sparse to say anything useful, or
     * the call fails — every caller treats null as "show the empty state", never as an error.
     */
    suspend fun getCoachInsight(context: CoachContext): String? {
        if (!isConfigured) {
            Log.w(TAG, "No API key configured; skipping insight")
            return null
        }
        if (!context.isRichEnough) return null

        val system = buildString {
            append("You are a professional health and fitness coach. ")
            append("Give brief, specific, encouraging advice grounded in the user's own numbers. ")
            append("2-3 sentences, no preamble, no lists, no markdown. ")
            append("Never invent data you were not given.")
        }

        val user = buildString {
            append("Here is the user's profile:\n")
            append(context.describe())
            append("\n\nGive them 2-3 sentences of actionable advice about their weight trend ")
            append("and whether their current targets suit their goal.")
            if (!context.profile.hasCompletedOnboarding) {
                append(" Some details are missing because they have not finished setting up ")
                append("their plan — work with what you have and do not mention the gaps.")
            }
        }

        return complete(system, user, maxTokens = 220)
    }

    private suspend fun complete(system: String, user: String, maxTokens: Int): String? = try {
        val response: DeepSeekResponse =
            client.post("https://api.deepseek.com/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    DeepSeekRequest(
                        messages = listOf(
                            DeepSeekMessage(role = "system", content = system),
                            DeepSeekMessage(role = "user", content = user)
                        ),
                        max_tokens = maxTokens
                    )
                )
            }.body()

        val error = response.error
        if (error != null) {
            Log.e(TAG, "API error: ${error.type} / ${error.code}")
            null
        } else {
            response.choices.firstOrNull()?.message?.content?.trim()?.ifBlank { null }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Coach request failed", e)
        null
    }

    companion object {
        private const val TAG = "DeepSeekService"

        /**
         * The system prompt for the diet chat, so the assistant knows who it is talking to.
         *
         * On the companion rather than the instance: building it needs no network, and calling
         * it through a constructor would spin up a fresh Ktor engine for every chat message.
         */
        fun chatSystemPrompt(context: CoachContext?): String = buildString {
            append("You are Carelorie's diet and training assistant. ")
            append("Answer in plain text, briefly and practically. ")
            if (context != null && context.isRichEnough) {
                append("Tailor every answer to this user:\n")
                append(context.describe())
                if (!context.profile.hasCompletedOnboarding) {
                    append("\n\nTheir plan setup is incomplete, so some fields are absent. ")
                    append("Use what is here; if a question genuinely needs a missing detail, ask for it.")
                }
            } else {
                append("You do not have their profile yet, so keep advice general ")
                append("and suggest they finish setting up their plan for tailored answers.")
            }
        }
    }
}
