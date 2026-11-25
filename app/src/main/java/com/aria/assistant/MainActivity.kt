
package com.aria.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AiReply(
    val message: String,
    val action: String = "CHAT",
    val parameters: JSONObject = JSONObject()
)

class AiBrain(
    private val apiKey: String,
    private val model: String = "gpt-4.1-mini"
) {
    private val endpoint =
        "https://api.openai.com/v1/chat/completions"

    private val systemPrompt = """
        You are ARIA, an intelligent personal AI assistant.
        Speak naturally in Bangla or English.
        Understand the user's intention.
        Break complicated tasks into steps.
        Never claim an action was completed unless it was.
        
        Return JSON only:
        {
          "message": "Your response",
          "action": "CHAT",
          "parameters": {}
        }

        Allowed actions:
        CHAT, CALL, OPEN_APP, PLAY_MUSIC,
        OPEN_GALLERY, EDIT_IMAGE, WEB_SEARCH,
        DOWNLOAD, REMINDER

        For sensitive actions, request confirmation.
        Never bypass Android permissions.
    """.trimIndent()

    suspend fun think(
        userMessage: String,
        history: JSONArray = JSONArray()
    ): AiReply = withContext(Dispatchers.IO) {

        require(apiKey.isNotBlank()) {
            "OpenAI API key is missing"
        }

        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt)
            )

        for (i in 0 until history.length()) {
            messages.put(history.getJSONObject(i))
        }

        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", userMessage)
        )

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("response_format",
                JSONObject().put("type", "json_object")
            )

        val connection =
            URL(endpoint).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization", "Bearer $apiKey"
            )
            connection.setRequestProperty(
                "Content-Type", "application/json"
            )

            connection.outputStream.use {
                it.write(body.toString().toByteArray())
            }

            val status = connection.responseCode

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream.bufferedReader().use {
                it.readText()
            }

            if (status !in 200..299) {
                throw Exception("AI API error: $status $response")
            }

            val result = JSONObject(response)
            val content = result
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val parsed = JSONObject(content)

            AiReply(
                message = parsed.optString(
                    "message", "I'm listening."
                ),
                action = parsed.optString("action", "CHAT"),
                parameters = parsed.optJSONObject("parameters")
                    ?: JSONObject()
            )
        } finally {
            connection.disconnect()
        }
    }
}
