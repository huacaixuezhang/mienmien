package com.mienmien.android

import org.json.JSONObject

data class TurnEvent(
    val type: String,
    val speaker: String,
    val text: String
)

sealed class RealtimeInbound {
    data class Transcription(val speaker: String, val text: String, val confidence: Double) : RealtimeInbound()
    data class Turn(val event: TurnEvent) : RealtimeInbound()
    data class Other(val raw: String) : RealtimeInbound()
}

object RealtimeEventParser {
    fun parse(raw: String): RealtimeInbound {
        return try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            when {
                "transcription" -> RealtimeInbound.Transcription(
                    speaker = json.optString("speaker", ""),
                    text = json.optString("text", ""),
                    confidence = json.optDouble("confidence", 0.0)
                )
                isTurnType(type) -> RealtimeInbound.Turn(
                    TurnEvent(
                        type = type,
                        speaker = json.optString("speaker", ""),
                        text = json.optString("text", "")
                    )
                )
                type == "turn_event" && isTurnType(json.optString("turnType")) -> RealtimeInbound.Turn(
                    TurnEvent(
                        type = json.optString("turnType"),
                        speaker = json.optString("speaker", ""),
                        text = json.optString("text", "")
                    )
                )
                else -> RealtimeInbound.Other(raw)
            }
        } catch (_: Exception) {
            RealtimeInbound.Other(raw)
        }
    }

    fun turnTypeToLabel(turnType: String): String {
        return when (turnType) {
            "INTERVIEWER_QUESTION_START" -> "面试官开始提问"
            "INTERVIEWER_QUESTION_END" -> "面试官结束提问"
            "CANDIDATE_ANSWER_START" -> "候选人开始回答"
            "CANDIDATE_ANSWER_END" -> "候选人结束回答"
            else -> "未知回合事件"
        }
    }

    private fun isTurnType(type: String): Boolean {
        return type == "INTERVIEWER_QUESTION_START" ||
                type == "INTERVIEWER_QUESTION_END" ||
                type == "CANDIDATE_ANSWER_START" ||
                type == "CANDIDATE_ANSWER_END"
    }
}
