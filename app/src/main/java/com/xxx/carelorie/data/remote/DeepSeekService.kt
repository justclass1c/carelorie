package com.xxx.carelorie.data.remote

import android.util.Log
import com.xxx.carelorie.BuildConfig
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
    val max_tokens: Int = 200
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

data class HealthContext(
    val name: String,
    val gender: String,
    val currentWeight: Float,
    val heightCm: Float,
    val weightChange7Days: Float,
    val experience: String
) {
    val bmi: Float = if (heightCm > 0) currentWeight / ((heightCm / 100f) * (heightCm / 100f)) else 0f
    val bmiCategory: String = when {
        bmi < 18.5f -> "Underweight"
        bmi < 25f -> "Normal weight"
        bmi < 30f -> "Overweight"
        else -> "Obese"
    }
}

data class GoalInsightContext(
    val name: String,
    val gender: String,
    val birthday: String,
    val currentWeight: Float,
    val heightCm: Float,
    val experience: String,
    val weightHistoryLast7Days: List<Pair<String, Float>>
) {
    val bmi: Float = if (heightCm > 0) currentWeight / ((heightCm / 100f) * (heightCm / 100f)) else 0f
    val bmiCategory: String = when {
        bmi < 18.5f -> "Underweight"
        bmi < 25f -> "Normal weight"
        bmi < 30f -> "Overweight"
        else -> "Obese"
    }
}

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

    suspend fun getWeightAdvice(context: HealthContext): String? {
        if (apiKey.isEmpty()) {
            Log.w("DeepSeekService", "API Key is missing")
            return null
        }

        val trend = if (context.weightChange7Days > 0) "gained ${context.weightChange7Days}kg" 
                    else if (context.weightChange7Days < 0) "lost ${Math.abs(context.weightChange7Days)}kg" 
                    else "maintained weight"

        val userPrompt = """
            User: ${context.name}, ${context.gender}, ${context.experience} experience.
            Current weight: ${context.currentWeight}kg, BMI: ${String.format(Locale.US, "%.1f", context.bmi)} (${context.bmiCategory}).
            Last 7 days trend: $trend.
            Give me brief health advice for my body weight changes.
        """.trimIndent()

        return try {
            val response: DeepSeekResponse = client.post("https://api.deepseek.com/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(DeepSeekRequest(
                    messages = listOf(
                        DeepSeekMessage(role = "system", content = "You are a professional health and fitness coach. Provide very concise, personalized advice."),
                        DeepSeekMessage(role = "user", content = userPrompt)
                    )
                ))
            }.body()

            if (response.error != null) {
                Log.e("DeepSeekService", "Weight advice API error: ${response.error.message} (${response.error.type})")
                return null
            }

            response.choices.firstOrNull()?.message?.content?.trim()
        } catch (e: Exception) {
            Log.e("DeepSeekService", "Error calling DeepSeek API", e)
            null
        }
    }

    suspend fun getGoalInsight(context: GoalInsightContext): String? {
        if (apiKey.isEmpty()) {
            Log.w("DeepSeekService", "API Key is missing for goal insight")
            return null
        }

        Log.d("DeepSeekService", "getGoalInsight called - weight: ${context.currentWeight}, height: ${context.heightCm}, history: ${context.weightHistoryLast7Days.size} days")

        val weightLog = context.weightHistoryLast7Days.joinToString("\n") { (date, weight) ->
            "  $date: ${String.format(Locale.US, "%.1f", weight)}kg"
        }

        val userPrompt = """
            Provide a brief personalized health insight for ${context.name}.
            Profile: ${context.gender}, ${context.experience} experience level.
            Height: ${String.format(Locale.US, "%.0f", context.heightCm)}cm, Current weight: ${String.format(Locale.US, "%.1f", context.currentWeight)}kg.
            BMI: ${String.format(Locale.US, "%.1f", context.bmi)} (${context.bmiCategory}).
            Weight log (last 7 days):
            $weightLog
            Give 2-3 sentences of actionable health advice based on the weight trend, BMI, and profile. Be encouraging and specific.
        """.trimIndent()

        Log.d("DeepSeekService", "Goal insight prompt: $userPrompt")

        return try {
            val response: DeepSeekResponse = client.post("https://api.deepseek.com/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(DeepSeekRequest(
                    messages = listOf(
                        DeepSeekMessage(role = "system", content = "You are a professional health and fitness coach. Provide brief, personalized, and encouraging health insights based on the user's data."),
                        DeepSeekMessage(role = "user", content = userPrompt)
                    ),
                    max_tokens = 200
                ))
            }.body()

            if (response.error != null) {
                Log.e("DeepSeekService", "Goal insight API error: ${response.error.message} (${response.error.type})")
                return null
            }

            val content = response.choices.firstOrNull()?.message?.content?.trim()
            Log.d("DeepSeekService", "Goal insight response: ${content?.take(100) ?: "NULL"}")
            content
        } catch (e: Exception) {
            Log.e("DeepSeekService", "Error calling DeepSeek API for goal insight", e)
            null
        }
    }
}
