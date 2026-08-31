package com.xxx.carelorie.data.nutrition

import android.util.Log
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Photo-based food recognition, backed by Gemini.
 *
 * This exists because the DeepSeek implementation could never have worked: it posted an
 * `image_url` part to `deepseek-chat`, which is a text-only model with no vision support, so the
 * image was discarded and the model was left guessing from an empty prompt. Gemini's flash models
 * accept inline image data and are fast enough to sit behind a camera button.
 *
 * Text estimation stays on DeepSeek — see [FoodRecognitionServiceProvider]. This class handles
 * both so the app can fall back to one provider if the other is unconfigured.
 */
class GeminiFoodRecognitionService(private val apiKey: String) : FoodRecognitionService {

    override val isConfigured: Boolean = apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private companion object {
        const val TAG = "GeminiRecognition"
        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TIMEOUT_MS = 90_000

        /**
         * Tried in order.
         *
         * Google retires model ids and returns 404 with "no longer available to new users", and
         * the flash tiers periodically answer 503 under load. Walking a list means one retired or
         * busy model degrades to a slower answer rather than a dead feature.
         *
         * The alias goes first so this list does not need editing every time Google ships a new
         * flash tier; the pinned id behind it is the fallback for the days the alias is busy.
         * Checked against the API: `gemini-2.5-flash` now 404s ("no longer available to new
         * users") and `gemini-3.5-flash` hangs, so both were dropped — every photo scan was
         * paying for a failed request before it got to a model that answers.
         */
        val MODELS = listOf("gemini-flash-latest", "gemini-3.6-flash")

        const val SCHEMA = """{"name":string,"calories":int,"protein_g":number,"carbs_g":number,
"fat_g":number,"serving":string,"fiber_g":number|null,"sugar_g":number|null,
"saturated_fat_g":number|null,"sodium_mg":number|null}"""

        const val PROMPT_IMAGE = """You are a nutrition estimator looking at a photo of a meal.

Identify every distinct food item you can see. For each one, estimate the nutrition of the
portion ACTUALLY VISIBLE in the photo — not a generic serving. Judge portion size from the
plate, bowl or utensils for scale.

If the dish looks Malaysian or Southeast Asian, use the local recipe rather than a Western
approximation: nasi lemak, char kuey teow, roti canai, bak kut teh and similar dishes are
substantially different from anything with the same English description.

Reply with ONLY a JSON array. No markdown fences, no commentary. Each element must be:
$SCHEMA

Return [] if the image contains no food."""

        const val PROMPT_TEXT = """You are a nutrition estimation API.

For the food named below, estimate the nutrition of one typical serving. Always return exactly
one item — use your best estimate rather than refusing or asking for clarification. Prefer local
recipes for regional dishes (Malaysian, Singaporean, Italian and so on).

Reply with ONLY a JSON array containing one element. No markdown fences, no commentary:
$SCHEMA"""
    }

    override suspend fun recognise(imageBase64: String): RecognitionResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext RecognitionResult.NotConfigured

            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") {
                            addJsonObject { put("text", PROMPT_IMAGE) }
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", imageBase64)
                                }
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 2048)
                }
            }.toString()

            // A photo can legitimately contain several foods, so nothing is trimmed here.
            execute(body, limit = Int.MAX_VALUE)
        }

    override suspend fun estimateNutrition(query: String, context: String?): RecognitionResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext RecognitionResult.NotConfigured

            val prompt = buildString {
                append(PROMPT_TEXT)
                append("\n\nFood: ")
                append(query)
                if (!context.isNullOrBlank()) {
                    append("\n\nThese online search results may help, but prefer your own ")
                    append("knowledge for regional dishes:\n")
                    append(context)
                }
            }

            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 1024)
                }
            }.toString()

            execute(body, limit = 1)
        }

    /** Walks [MODELS] until one answers, so a retired or overloaded model is not fatal. */
    private fun execute(requestBody: String, limit: Int): RecognitionResult {
        var lastFailure: RecognitionResult.Failure? = null

        for (model in MODELS) {
            when (val outcome = callModel(model, requestBody, limit)) {
                is RecognitionResult.Success -> return outcome
                is RecognitionResult.Failure -> {
                    lastFailure = outcome
                    if (!outcome.retryable) return outcome
                    Log.w(TAG, "$model unavailable, trying the next model")
                }
                RecognitionResult.NotConfigured -> return outcome
            }
        }
        return lastFailure ?: RecognitionResult.Failure("Could not reach the AI service.")
    }

    private fun callModel(model: String, requestBody: String, limit: Int): RecognitionResult {
        var connection: HttpURLConnection? = null
        try {
            // The key goes in a header, not the query string: URLs end up in logs and crash
            // reports far more readily than headers do.
            connection = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "$model returned HTTP $code")
                return when (code) {
                    400 -> RecognitionResult.Failure("The photo could not be read. Try another one.")
                    401, 403 -> RecognitionResult.Failure(
                        "The AI key was rejected. Check GEMINI_API_KEY in local.properties."
                    )
                    404 -> RecognitionResult.Failure("$model is unavailable.", retryable = true)
                    429 -> RecognitionResult.Failure(
                        "Too many requests. Wait a moment and try again.", retryable = true
                    )
                    in 500..599 -> RecognitionResult.Failure(
                        "The AI service is busy.", retryable = true
                    )
                    else -> RecognitionResult.Failure("Could not analyse that (error $code). $error")
                }
            }

            val text = extractText(connection.inputStream.bufferedReader().use { it.readText() })
                ?: return RecognitionResult.Failure("The AI returned an empty response.")

            val candidates = parseCandidates(text)
            return if (candidates.isEmpty()) {
                RecognitionResult.Failure("No food was recognised. Try a clearer photo.")
            } else {
                RecognitionResult.Success(candidates.take(limit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "$model request failed", e)
            return RecognitionResult.Failure("Could not reach the AI service.", retryable = true)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Pulls the model's text out of a Gemini response.
     *
     * Reads the last part rather than the first: reasoning-capable models may emit a thought part
     * ahead of the answer, and taking the first would return an empty string.
     */
    private fun extractText(body: String): String? = try {
        json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.lastOrNull()
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "Could not parse the response envelope", e)
        null
    }

    private fun parseCandidates(raw: String): List<FoodCandidate> {
        // Models sometimes wrap the array in prose or a fence despite being told not to, so the
        // array is located rather than assumed to be the whole reply.
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end <= start) return emptyList()

        return try {
            val element = json.parseToJsonElement(raw.substring(start, end + 1))
            if (element !is JsonArray) return emptyList()
            element.mapNotNull { runCatching { parseOne(it.jsonObject) }.getOrNull() }
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse the model output", e)
            emptyList()
        }
    }

    private fun parseOne(obj: JsonObject): FoodCandidate? {
        fun str(key: String) =
            obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
        fun num(key: String) = str(key)?.toFloatOrNull()

        val name = str("name") ?: return null
        return FoodCandidate(
            preset = RemoteFoodPreset(
                name = name,
                calories = (num("calories") ?: 0f).toInt(),
                protein = num("protein_g") ?: 0f,
                carbs = num("carbs_g") ?: 0f,
                fat = num("fat_g") ?: 0f
            ),
            detail = NutritionDetail(
                servingDescription = str("serving"),
                fiberGrams = num("fiber_g"),
                sugarGrams = num("sugar_g"),
                saturatedFatGrams = num("saturated_fat_g"),
                sodiumMilligrams = num("sodium_mg"),
                source = NutritionSource.AI_ESTIMATE
            )
        )
    }
}
