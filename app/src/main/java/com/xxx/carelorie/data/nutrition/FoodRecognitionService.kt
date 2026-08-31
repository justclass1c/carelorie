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

sealed interface RecognitionResult {
    data class Success(val candidates: List<FoodCandidate>) : RecognitionResult
    /** [retryable] marks a transient problem worth trying another model or another moment. */
    data class Failure(val reason: String, val retryable: Boolean = false) : RecognitionResult
    /** No key configured — the UI explains this rather than pretending the call failed. */
    object NotConfigured : RecognitionResult
}

/**
 * Turns a photo of a meal into food candidates.
 */
interface FoodRecognitionService {
    suspend fun recognise(imageBase64: String): RecognitionResult
    suspend fun estimateNutrition(query: String, context: String? = null): RecognitionResult
    val isConfigured: Boolean

    /**
     * Whether [recognise] can actually look at a picture.
     *
     * Separate from [isConfigured] because a text-only backend is perfectly usable for
     * [estimateNutrition] while being useless for a photo — and the UI needs to know which
     * buttons to offer rather than letting one silently fail.
     */
    val supportsImages: Boolean get() = false
}

/**
 * Stand-in used when no API key is configured.
 *
 * It deliberately refuses rather than guessing. An earlier version returned canned macros —
 * 250 kcal for literally any query — which looked exactly like a working answer, so "apple" and
 * "pizza" came back identical and the numbers could end up logged as if they were real. A clear
 * refusal is more useful than a confident fabrication.
 */
class StubFoodRecognitionService : FoodRecognitionService {

    override val isConfigured: Boolean = false
    override val supportsImages: Boolean = false

    override suspend fun recognise(imageBase64: String): RecognitionResult =
        RecognitionResult.NotConfigured

    override suspend fun estimateNutrition(query: String, context: String?): RecognitionResult =
        RecognitionResult.NotConfigured
}

/**
 * Text-based nutrition estimation via DeepSeek's chat endpoint.
 *
 * Text only. `deepseek-chat` has no vision capability, so [recognise] refuses rather than posting
 * an image part the model discards — which is what the previous version did, leaving the model to
 * answer from a prompt with no picture attached and no way for anyone to tell.
 */
class DeepSeekFoodRecognitionService(private val apiKey: String) : FoodRecognitionService {

    override val isConfigured: Boolean = apiKey.isNotBlank()
    override val supportsImages: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private companion object {
        const val TAG = "DeepSeekRecognition"
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-chat"
        const val TIMEOUT_MS = 60_000
        const val PROMPT_IMAGE = """You are a nutrition estimator. Identify every distinct food item in this photo.
Reply with ONLY a JSON array, no markdown fences and no commentary. Each element must be:
{"name":string,"calories":int,"protein_g":number,"carbs_g":number,"fat_g":number,
"serving":string,"fiber_g":number|null,"sugar_g":number|null,
"saturated_fat_g":number|null,"sodium_mg":number|null}
Estimate for the portion actually visible. If the dish looks Malaysian or Southeast Asian, use
the local recipe. Return [] if there is no food in the image."""

        const val PROMPT_TEXT = """You are a nutrition estimation API. For the food item specified, estimate its nutritional facts per typical serving.

IMPORTANT: You must ALWAYS return a JSON array containing EXACTLY ONE item, even if you are uncertain. Use your best estimate based on common recipes, USDA food data, and general nutrition knowledge. Do not refuse, apologise, or ask for clarification — just return the estimate.

If online search context is provided, use it as a hint, but prioritise your internal knowledge for regional dishes like Malaysian, Singaporean, or Italian specialties.

Reply with ONLY a JSON array. No conversational text, no markdown fences, and no commentary.
The element must follow this strict schema:
{"name":string,"calories":int,"protein_g":number,"carbs_g":number,"fat_g":number,
"serving":string,"fiber_g":number|null,"sugar_g":number|null,
"saturated_fat_g":number|null,"sodium_mg":number|null}

Guidelines:
- Generic foods (e.g., "cake", "apple", "rice", "fried chicken"): provide a typical single serving estimate.
- Specific dishes (e.g., Pan Mee, Spaghetti Carbonara): provide a typical portion estimate.
- Pan Mee: Approx 500-600 kcal for a standard bowl.
- Spaghetti Carbonara: Approx 500-700 kcal for a standard plate.
- If the exact serving size is unknown, use a reasonable standard (e.g., "1 slice", "1 bowl", "1 plate").
- Use standard metric measurements (grams/milligrams)."""
    }

    override suspend fun recognise(imageBase64: String): RecognitionResult =
        RecognitionResult.Failure(
            "Photo recognition needs a Gemini key. Add GEMINI_API_KEY to local.properties."
        )

    override suspend fun estimateNutrition(query: String, context: String?): RecognitionResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext RecognitionResult.NotConfigured

            val prompt = buildString {
                append(PROMPT_TEXT)
                append("\n\nFood: $query")
                if (!context.isNullOrBlank()) {
                    append("\n\nHere are some online search results that might help you provide a more accurate estimate:\n")
                    append(context)
                }
                append("\n\nIf you are unsure, provide a standard scientific estimate based on common recipes. For regional dishes like 'Pan Mee', provide a typical bowl estimate (approx 500g).")
            }

            val requestBody = buildJsonObject {
                put("model", MODEL)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
                put("max_tokens", 1024)
                put("temperature", 0.2)
            }.toString()

            executeRequest(requestBody)
        }

    private suspend fun executeRequest(requestBody: String): RecognitionResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "HTTP ${connection.responseCode}: $error")
                
                val detailedReason = when(connection.responseCode) {
                    400 -> "The AI request was malformed. Please try again."
                    401 -> "Invalid API Key. Check your DeepSeek configuration."
                    429 -> "Too many requests. Please wait a moment."
                    else -> "Could not process request (error ${connection.responseCode})."
                }
                return@withContext RecognitionResult.Failure(detailedReason)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val responseJson = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                return@withContext RecognitionResult.Failure("Received invalid response from AI.")
            }
            
            val text = responseJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
                ?: return@withContext RecognitionResult.Failure("The AI returned an empty response.")

            Log.d(TAG, "Raw AI response: $text")
            val candidates = parseCandidates(text)
            if (candidates.isEmpty()) {
                Log.w(TAG, "No candidates parsed from: $text")
                RecognitionResult.Failure("The AI couldn't find nutritional info for that food. Try a more specific name.")
            } else {
                RecognitionResult.Success(candidates.take(1)) // Ensure only ONE food
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            RecognitionResult.Failure("Could not reach DeepSeek. Check your connection.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseCandidates(raw: String): List<FoodCandidate> {
        val jsonStart = raw.indexOf('[')
        val jsonEnd = raw.lastIndexOf(']')
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) {
            Log.w(TAG, "No JSON array found in response: $raw")
            return emptyList()
        }
        val jsonText = raw.substring(jsonStart, jsonEnd + 1)
        
        return try {
            val element = json.parseToJsonElement(jsonText)
            if (element !is JsonArray) {
                Log.w(TAG, "Expected JSON array but got: $jsonText")
                return emptyList()
            }
            element.mapNotNull { item ->
                runCatching { parseOne(item.jsonObject) }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse model output: $jsonText", e)
            emptyList()
        }
    }

    private fun parseOne(obj: JsonObject): FoodCandidate? {
        fun str(key: String) = obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
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

/**
 * Routes each capability to a backend that can actually serve it.
 *
 * Photos go to Gemini, text estimates prefer DeepSeek and fall back to Gemini. Either key alone
 * gives a working app; neither gives honest "not configured" messages instead of invented macros.
 */
class CompositeFoodRecognitionService(
    private val imageBackend: FoodRecognitionService?,
    private val textBackend: FoodRecognitionService?
) : FoodRecognitionService {

    override val isConfigured: Boolean = imageBackend != null || textBackend != null
    override val supportsImages: Boolean = imageBackend != null

    override suspend fun recognise(imageBase64: String): RecognitionResult =
        imageBackend?.recognise(imageBase64) ?: RecognitionResult.NotConfigured

    override suspend fun estimateNutrition(query: String, context: String?): RecognitionResult {
        val primary = textBackend ?: imageBackend ?: return RecognitionResult.NotConfigured
        val result = primary.estimateNutrition(query, context)
        // One provider being down should not lose the feature when the other is configured.
        if (result is RecognitionResult.Failure && result.retryable && primary !== imageBackend) {
            imageBackend?.let { return it.estimateNutrition(query, context) }
        }
        return result
    }
}

object FoodRecognitionServiceProvider {
    /** [deepSeekKey] drives text estimation; [geminiKey] additionally unlocks photo recognition. */
    fun create(deepSeekKey: String, geminiKey: String): FoodRecognitionService {
        val gemini = geminiKey.takeIf { it.isNotBlank() }?.let { GeminiFoodRecognitionService(it) }
        val deepSeek = deepSeekKey.takeIf { it.isNotBlank() }?.let { DeepSeekFoodRecognitionService(it) }
        if (gemini == null && deepSeek == null) return StubFoodRecognitionService()
        return CompositeFoodRecognitionService(imageBackend = gemini, textBackend = deepSeek)
    }
}
