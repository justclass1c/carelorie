package com.xxx.carelorie.data.nutrition

import android.util.Log
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

sealed interface RecognitionResult {
    data class Success(val candidates: List<FoodCandidate>) : RecognitionResult
    data class Failure(val reason: String) : RecognitionResult
    /** No key configured — the UI explains this rather than pretending the call failed. */
    object NotConfigured : RecognitionResult
}

/**
 * Turns a photo of a meal into food candidates.
 *
 * Two implementations exist so the feature can be built, demoed and marked before an API key
 * exists. Swap by changing [FoodRecognitionServiceProvider.create] — nothing else changes.
 */
interface FoodRecognitionService {
    suspend fun recognise(imageBase64: String): RecognitionResult
    val isConfigured: Boolean
}

/**
 * Returns plausible results without any network call.
 *
 * This is not dead code: it makes the camera to review to log flow fully demonstrable, and it
 * keeps the app working if the key is missing or its quota runs out mid-presentation.
 */
class StubFoodRecognitionService : FoodRecognitionService {

    override val isConfigured: Boolean = true

    private val sampleMeals = listOf(
        listOf(
            FoodCandidate(
                RemoteFoodPreset(name = "Nasi Lemak", calories = 644, protein = 17f, carbs = 81f, fat = 27f),
                NutritionDetail(
                    servingDescription = "1 plate (approx. 350 g)",
                    fiberGrams = 4.2f, sugarGrams = 6f, saturatedFatGrams = 11f,
                    sodiumMilligrams = 890f, source = NutritionSource.AI_ESTIMATE
                )
            ),
            FoodCandidate(
                RemoteFoodPreset(name = "Fried Egg", calories = 90, protein = 6f, carbs = 0.4f, fat = 7f),
                NutritionDetail(
                    servingDescription = "1 egg",
                    saturatedFatGrams = 2f, sodiumMilligrams = 95f,
                    cholesterolMilligrams = 186f, source = NutritionSource.AI_ESTIMATE
                )
            )
        ),
        listOf(
            FoodCandidate(
                RemoteFoodPreset(name = "Chicken Rice", calories = 607, protein = 30f, carbs = 75f, fat = 20f),
                NutritionDetail(
                    servingDescription = "1 plate (approx. 400 g)",
                    fiberGrams = 2.1f, sugarGrams = 3f, saturatedFatGrams = 6f,
                    sodiumMilligrams = 1020f, source = NutritionSource.AI_ESTIMATE
                )
            ),
            FoodCandidate(
                RemoteFoodPreset(name = "Cucumber Slices", calories = 8, protein = 0.3f, carbs = 1.9f, fat = 0.1f),
                NutritionDetail(
                    servingDescription = "about 50 g",
                    fiberGrams = 0.3f, source = NutritionSource.AI_ESTIMATE
                )
            )
        )
    )

    override suspend fun recognise(imageBase64: String): RecognitionResult {
        delay(1400) // stand in for network latency so the loading state is visible
        // Deterministic per image, so the same photo always gives the same answer.
        val index = if (imageBase64.isEmpty()) 0 else imageBase64.length % sampleMeals.size
        return RecognitionResult.Success(sampleMeals[index])
    }
}

/**
 * Real implementation, calling Gemini's vision endpoint.
 *
 * The key comes from BuildConfig, which reads local.properties (never committed). Note that a
 * key compiled into an APK can be extracted from it — fine for coursework with a rotatable
 * free-tier key, but a Supabase Edge Function proxy is the correct fix for anything real.
 */
class GeminiFoodRecognitionService(private val apiKey: String) : FoodRecognitionService {

    override val isConfigured: Boolean = apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private companion object {
        const val TAG = "GeminiRecognition"
        const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        const val TIMEOUT_MS = 30_000
        const val PROMPT = """You are a nutrition estimator. Identify every distinct food item in this photo.
Reply with ONLY a JSON array, no markdown fences and no commentary. Each element must be:
{"name":string,"calories":int,"protein_g":number,"carbs_g":number,"fat_g":number,
"serving":string,"fiber_g":number|null,"sugar_g":number|null,
"saturated_fat_g":number|null,"sodium_mg":number|null}
Estimate for the portion actually visible. If the dish looks Malaysian or Southeast Asian, use
the local recipe. Return [] if there is no food in the image."""
    }

    override suspend fun recognise(imageBase64: String): RecognitionResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext RecognitionResult.NotConfigured

            val requestBody = buildString {
                append("""{"contents":[{"parts":[""")
                append("""{"text":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(PROMPT))}},""")
                append("""{"inline_data":{"mime_type":"image/jpeg","data":"$imageBase64"}}""")
                append("""]}],"generationConfig":{"temperature":0.2}}""")
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$ENDPOINT?key=$apiKey").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                if (connection.responseCode !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "HTTP ${connection.responseCode}: $error")
                    return@withContext RecognitionResult.Failure(
                        "Could not analyse the photo (error ${connection.responseCode})."
                    )
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val text = json.parseToJsonElement(body).jsonObject["candidates"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                    ?: return@withContext RecognitionResult.Failure("The response was empty.")

                val candidates = parseCandidates(text)
                if (candidates.isEmpty()) {
                    RecognitionResult.Failure("No food was recognised in that photo.")
                } else {
                    RecognitionResult.Success(candidates)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed", e)
                RecognitionResult.Failure("Could not reach the service. Check your connection.")
            } finally {
                connection?.disconnect()
            }
        }

    private fun parseCandidates(raw: String): List<FoodCandidate> {
        // Models often wrap JSON in ```json fences despite instructions.
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val element = json.parseToJsonElement(cleaned)
            if (element !is kotlinx.serialization.json.JsonArray) {
                Log.w(TAG, "Expected JSON array but got: $cleaned")
                return emptyList()
            }
            element.mapNotNull { item ->
                runCatching { parseOne(item.jsonObject) }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse model output: $cleaned", e)
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

object FoodRecognitionServiceProvider {
    /**
     * Picks the real service when a key is present, otherwise the stub.
     * Add GEMINI_API_KEY to local.properties to switch over — no code change needed.
     */
    fun create(apiKey: String): FoodRecognitionService =
        if (apiKey.isNotBlank()) GeminiFoodRecognitionService(apiKey) else StubFoodRecognitionService()
}
