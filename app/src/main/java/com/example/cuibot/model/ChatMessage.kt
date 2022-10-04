package com.example.cuibot.model

/**
 * Features of dialog messages
 */
data class ChatMessage(
    val message: String,
    val link: String,
    val actions: List<String>,
    val receivedFrom: Int
)
