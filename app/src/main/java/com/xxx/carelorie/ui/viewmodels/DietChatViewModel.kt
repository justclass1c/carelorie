package com.xxx.carelorie.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.BuildConfig
import com.xxx.carelorie.data.remote.CoachContext
import com.xxx.carelorie.data.remote.DeepSeekService
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

        val newMessages = _uiState.value.messages + ChatMessage(text, true)
        _uiState.update { 
            it.copy(
                messages = newMessages,
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        generateAiResponse(newMessages)
    }

    fun deleteMessage(index: Int) {
        if (index < 0 || index >= _uiState.value.messages.size) return
        
        // Truncate the list from the selected index onwards
        val truncatedMessages = _uiState.value.messages.subList(0, index)
        _uiState.update { it.copy(messages = truncatedMessages) }
    }

    fun editMessage(index: Int, newText: String) {
        if (index < 0 || index >= _uiState.value.messages.size) return
        val message = _uiState.value.messages[index]
        if (!message.isUser) return

        // 1. Update the message at index and truncate subsequent ones
        val updatedMessages = _uiState.value.messages.subList(0, index).toMutableList()
        updatedMessages.add(ChatMessage(newText, true))
        
        _uiState.update { 
            it.copy(
                messages = updatedMessages,
                isLoading = true,
                error = null
            )
        }

        // 2. Regenerate response for the edited message
        generateAiResponse(updatedMessages)
    }

    private fun generateAiResponse(history: List<ChatMessage>) {
        viewModelScope.launch {
            try {
                // Fetch profile and weight history for personalization
                val profile = userRepository.getProfile(userId)
                val weightHistory = userRepository.getWeightHistory(userId)

                // Use the last user message as the prompt, but we could send full history
                val lastUserMessage = history.lastOrNull { it.isUser }?.text ?: ""
                val response = callDeepSeek(lastUserMessage, profile, weightHistory)
                
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

    private suspend fun callDeepSeek(prompt: String, profile: com.xxx.carelorie.data.UserProfile?, weightHistory: List<com.xxx.carelorie.data.WeightRecord>): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "The AI assistant is not configured yet. Add DEEPSEEK_API_KEY to " +
                "local.properties and rebuild the app to enable it."
        }

        val url = "https://api.deepseek.com/chat/completions"
        
        // One briefing shared with the Goal screen's coach, so the chat and the insight card
        // never contradict each other — and so onboarding answers reach both automatically.
        val systemPrompt = DeepSeekService.chatSystemPrompt(
            profile?.let { p ->
                CoachContext(
                    profile = p,
                    weightHistoryLast7Days = weightHistory
                        .sortedBy { it.date }
                        .takeLast(14)
                        .map { it.date to it.weight }
                )
            }
        )

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
            put("max_tokens", 4096)
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
