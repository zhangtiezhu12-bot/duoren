package com.yourcompany.videocallsdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object TokenClient {
    suspend fun fetch(
        endpoint: String,
        roomName: String,
        identity: String,
        name: String
    ): ConnectionInfo = withContext(Dispatchers.IO) {
        val sep = if (endpoint.contains("?")) "&" else "?"
        fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        val requestUrl = endpoint + sep +
            "room=${enc(roomName)}&identity=${enc(identity)}&name=${enc(name)}"

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                error("Token 服务返回 HTTP $status: $body")
            }

            val json = JSONObject(body)
            val token = json.optString("token")
            val serverUrl = json.optString("wsUrl")
            val returnedRoom = json.optString("room", roomName)
            val returnedIdentity = json.optString("identity", identity)
            val returnedName = json.optString("name", name)

            require(token.isNotBlank()) { "Token 服务响应缺少 token" }
            require(serverUrl.startsWith("ws://") || serverUrl.startsWith("wss://")) {
                "Token 服务响应 wsUrl 无效: $serverUrl"
            }

            ConnectionInfo(
                roomName = returnedRoom,
                identity = returnedIdentity,
                name = returnedName,
                serverUrl = serverUrl,
                token = token
            )
        } finally {
            connection.disconnect()
        }
    }
}
