package com.xxx.carelorie.data.nutrition

import android.util.Log
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        // Open Food Facts asks callers to identify themselves.
        const val USER_AGENT = "Carelorie/1.0 (Android; student project)"
        const val FIELDS =
            "code,product_name,brands,serving_size,nutriments,image_front_small_url"
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

    /** Free-text product search. Returns an empty list when offline or nothing matches. */
    suspend fun search(query: String, limit: Int = 20): List<FoodCandidate> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE/cgi/search.pl?search_terms=$encoded&search_simple=1" +
                "&action=process&json=1&page_size=$limit&fields=$FIELDS"
            val body = get(url) ?: return@withContext emptyList()
            try {
                val products = json.parseToJsonElement(body).jsonObject["products"]?.jsonArray
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
        val calories = num("energy-kcal_serving", "energy-kcal_100g")
            ?: num("energy_serving", "energy_100g")?.let { it / 4.184f } // kJ to kcal
            ?: return null

        val brand = product["brands"]?.jsonPrimitive?.content
            ?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val serving = product["serving_size"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "per 100 g"

        val preset = RemoteFoodPreset(
            name = if (brand != null) "$name ($brand)" else name,
            calories = calories.toInt(),
            protein = num("proteins_serving", "proteins_100g") ?: 0f,
            carbs = num("carbohydrates_serving", "carbohydrates_100g") ?: 0f,
            fat = num("fat_serving", "fat_100g") ?: 0f,
            imageUrl = product["image_front_small_url"]?.jsonPrimitive?.content
        )

        // Open Food Facts reports sodium and salt in grams; the panel shows milligrams.
        val sodiumMg = num("sodium_serving", "sodium_100g")?.times(1000f)
            ?: num("salt_serving", "salt_100g")?.times(400f) // salt to sodium, then g to mg

        return FoodCandidate(
            preset = preset,
            detail = NutritionDetail(
                servingDescription = serving,
                fiberGrams = num("fiber_serving", "fiber_100g"),
                sugarGrams = num("sugars_serving", "sugars_100g"),
                saturatedFatGrams = num("saturated-fat_serving", "saturated-fat_100g"),
                sodiumMilligrams = sodiumMg,
                cholesterolMilligrams = num("cholesterol_serving", "cholesterol_100g")
                    ?.times(1000f),
                potassiumMilligrams = num("potassium_serving", "potassium_100g")?.times(1000f),
                brand = brand,
                source = source
            )
        )
    }
}
