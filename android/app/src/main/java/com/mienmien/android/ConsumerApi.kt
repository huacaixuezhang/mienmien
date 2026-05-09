package com.mienmien.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class ConsumerApi(private val baseUrl: String = "http://10.0.2.2:8081/api/v1/consumer") {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()
    private val wsUrl = baseUrl.replace("http://", "ws://").replace("/api/v1/consumer", "/ws/consumer/diarization")
    private var ws: WebSocket? = null

    fun createSession(): String {
        val reqBody = """{"userId":"user_001","mode":"live"}""".toRequestBody(jsonType)
        val req = Request.Builder().url("$baseUrl/sessions").post(reqBody).build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    fun postVoiceEvent(sessionId: String, questionText: String): String {
        val body = """{"questionText":${jsonString(questionText)}}""".toRequestBody(jsonType)
        val req = Request.Builder()
            .url("$baseUrl/sessions/$sessionId/events/voice")
            .post(body)
            .build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    fun postTextEvent(sessionId: String, questionText: String): String {
        val body = """{"questionText":${jsonString(questionText)}}""".toRequestBody(jsonType)
        val req = Request.Builder()
            .url("$baseUrl/sessions/$sessionId/events/text")
            .post(body)
            .build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    fun photoQa(sessionId: String): String {
        val req = Request.Builder().url("$baseUrl/sessions/$sessionId/photo-qa").get().build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    fun onceAnswer(sessionId: String): String {
        val req = Request.Builder().url("$baseUrl/sessions/$sessionId/answers/once").get().build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    fun endSession(sessionId: String): String {
        val req = Request.Builder().url("$baseUrl/sessions/$sessionId/end").post("".toRequestBody(null)).build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    /**
     * 按行回调 SSE 原始行（含空行），调用方负责切回主线程更新 UI。
     */
    fun streamAnswerLines(sessionId: String, onLine: (String) -> Unit) {
        val req = Request.Builder()
            .url("$baseUrl/sessions/$sessionId/answers/stream")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body ?: return@use
            body.byteStream().bufferedReader().useLines { lines ->
                lines.forEach(onLine)
            }
        }
    }

    private fun jsonString(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    fun startRealtimeDiarization(
        sessionId: String,
        mode: String = "unsupervised",
        onTextEvent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val req = Request.Builder().url(wsUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("""{"type":"config","mode":"$mode","sessionId":"$sessionId"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onTextEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                onError(t.message ?: "websocket failed")
            }
        })
    }

    fun sendPcmFrame(frame: ByteArray) {
        ws?.send(frame.toByteString())
    }

    fun stopRealtimeDiarization() {
        ws?.close(1000, "normal")
        ws = null
    }
}
