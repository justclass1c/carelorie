package com.xxx.carelorie.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.BuildConfig
import com.xxx.carelorie.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

data class DietChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("Hello! I'm your AI Diet Assistant. How can I help you today?", false)
    ),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class DietChatViewModel(
    private val userRepository: UserRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DietChatUiState())
    val uiState: StateFlow<DietChatUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val apiKey = BuildConfig.DEEPSEEK_API_KEY

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading) return

        _uiState.update { 
            it.copy(
                messages = it.messages + ChatMessage(text, true),
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                // Fetch profile for personalization
                val profile = userRepository.getProfile(userId)
                val response = callDeepSeek(text, profile)
                
                _uiState.update { 
                    it.copy(
                        messages = it.messages + ChatMessage(response, false),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("DietChatViewModel", "Error calling DeepSeek", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to get advice: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private suspend fun callDeepSeek(prompt: String, profile: com.xxx.carelorie.data.UserProfile?): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "I'm sorry, I can't provide advice right now as the AI service is not configured."

        val url = "https://api.deepseek.com/chat/completions"
        
        // Build personalized system prompt
        val systemPrompt = buildString {
            append("You are a professional dietitian and fitness coach. Provide concise, helpful, and science-based diet and nutrition advice. ")
            if (profile != null) {
                append("The user's name is ${profile.name}. ")
                if (profile.gender.isNotEmpty()) append("Gender: ${profile.gender}. ")
                if (profile.height.isNotEmpty()) append("Height: ${profile.height} cm. ")
                if (profile.liftingExperience.isNotEmpty()) append("Fitness experience: ${profile.liftingExperience} years. ")
                append("Address the user by name occasionally and tailor your advice to their physical profile.")
            }
        }
        
        val requestBody = buildJsonObject {
            put("model", "deepseek-chat")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("temperature", 0.7)
            put("max_tokens", 512)
        }.toString()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("HTTP ${connection.responseCode}: $error")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: "I couldn't process that. Could you try again?"
        } finally {
            connection?.disconnect()
        }
    }
}
