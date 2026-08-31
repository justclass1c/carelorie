package com.xxx.carelorie.data.nutrition

import android.util.Log
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Open Food Facts lookup.
 *
 * Free, open, and needs no API key — which is why it backs both the barcode scanner and the
 * detailed nutrition panel. One integration, two features.
 *
 * Deliberately uses HttpURLConnection and hand-parses the JSON rather than pulling in an HTTP
 * client: the response shape is loose (fields appear and disappear per product) so tolerant
 * parsing is more robust here than strict deserialisation into a data class.
 */
class OpenFoodFactsService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private companion object {
        const val TAG = "OpenFoodFacts"
        const val BASE = "https://world.openfoodfacts.org"
        /** Product search moved off the main site onto its own host. */
        const val SEARCH_BASE = "https://search.openfoodfacts.org"
        // Open Food Facts asks callers to identify themselves.
        const val USER_AGENT = "Carelorie/1.0 (Android; student project)"
        const val FIELDS =
            "code,product_name,brands,serving_size,nutriments,image_front_small_url"
        /** The search service rejects unknown field names, so it gets its own shorter list. */
        const val SEARCH_FIELDS = "code,product_name,brands,nutriments"
        const val TIMEOUT_MS = 12_000
    }

    /** Looks up a single product by barcode. Returns null when unknown or unreachable. */
    suspend fun lookupBarcode(barcode: String): FoodCandidate? = withContext(Dispatchers.IO) {
        val url = "$BASE/api/v2/product/$barcode.json?fields=$FIELDS"
        val body = get(url) ?: return@withContext null
        try {
            val root = json.parseToJsonElement(body).jsonObject
            val status = root["status"]?.jsonPrimitive?.content
            if (status != "1") return@withContext null
            val product = root["product"]?.jsonObject ?: return@withContext null
            parseProduct(product, NutritionSource.BARCODE)
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse barcode response", e)
            null
        }
    }

    /**
     * Free-text product search. Returns an empty list when offline or nothing matches.
     *
     * Uses Open Food Facts' current search service. The legacy `cgi/search.pl` endpoint this used
     * to call now answers 503 with an HTML "temporarily unavailable" page, which parsed as zero
     * results — so "Search online" looked like it worked and always found nothing.
     */
    suspend fun search(query: String, limit: Int = 20): List<FoodCandidate> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_BASE/search?q=$encoded&page_size=$limit&fields=$SEARCH_FIELDS"

            val body = get(url) ?: return@withContext emptyList()
            try {
                val root = json.parseToJsonElement(body).jsonObject
                // The search service returns "hits"; "products" is kept as a fallback so a
                // future switch back to a v2 endpoint would still parse.
                val products = (root["hits"] ?: root["products"])?.jsonArray
                    ?: return@withContext emptyList()

                products.mapNotNull { element ->
                    runCatching {
                        parseProduct(element.jsonObject, NutritionSource.OPEN_FOOD_FACTS)
                    }.getOrNull()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not parse search response", e)
                emptyList()
            }
        }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${connection.responseCode} for $url")
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseProduct(product: JsonObject, source: NutritionSource): FoodCandidate? {
        val name = product["product_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return null
        val nutriments = product["nutriments"]?.jsonObject ?: JsonObject(emptyMap())

        fun num(vararg keys: String): Float? {
            for (key in keys) {
                val raw = nutriments[key]?.jsonPrimitive?.content ?: continue
                raw.toFloatOrNull()?.let { return it }
            }
            return null
        }

        // Prefer per-serving values; fall back to per-100g, which is what most entries carry.
        // Also check generic keys as fallbacks for legacy or incomplete data.
        val calories = num("energy-kcal_serving", "energy-kcal_100g", "energy-kcal")
            ?: num("energy_serving", "energy_100g", "energy")?.let { it / 4.184f } // kJ to kcal
            ?: run {
                Log.w(TAG, "Skipping $name: No calories found in nutriments.")
                return null
            }

        // The v2 barcode endpoint returns brands as a comma-separated string; the search service
        // returns an array. Accept either, or the brand is silently lost on one of the two paths.
        val brand = when (val raw = product["brands"]) {
            is JsonArray -> raw.firstOrNull()?.jsonPrimitive?.content
            is JsonPrimitive -> raw.content.split(",").firstOrNull()
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val serving = product["serving_size"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "per 100 g"

        val preset = RemoteFoodPreset(
            name = if (brand != null) "$name ($brand)" else name,
            calories = calories.toInt(),
            protein = num("proteins_serving", "proteins_100g", "proteins") ?: 0f,
            carbs = num("carbohydrates_serving", "carbohydrates_100g", "carbohydrates") ?: 0f,
            fat = num("fat_serving", "fat_100g", "fat") ?: 0f,
            imageUrl = product["image_front_small_url"]?.jsonPrimitive?.content
        )

        // Open Food Facts reports sodium and salt in grams; the panel shows milligrams.
        val sodiumMg = num("sodium_serving", "sodium_100g", "sodium")?.times(1000f)
            ?: num("salt_serving", "salt_100g", "salt")?.times(400f) // salt to sodium, then g to mg

        return FoodCandidate(
            preset = preset,
            detail = NutritionDetail(
                servingDescription = serving,
                fiberGrams = num("fiber_serving", "fiber_100g", "fiber"),
                sugarGrams = num("sugars_serving", "sugars_100g", "sugars"),
                saturatedFatGrams = num("saturated-fat_serving", "saturated-fat_100g", "saturated-fat"),
                sodiumMilligrams = sodiumMg,
                cholesterolMilligrams = num("cholesterol_serving", "cholesterol_100g", "cholesterol")
                    ?.times(1000f),
                potassiumMilligrams = num("potassium_serving", "potassium_100g", "potassium")?.times(1000f),
                brand = brand,
                source = source
            )
        )
    }
}
