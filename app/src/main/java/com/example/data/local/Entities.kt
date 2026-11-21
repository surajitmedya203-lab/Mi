package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_threads")
data class ConversationThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val profileId: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val isUser: Boolean,
    val content: String,
    val emotionTag: String = "",
    val languageTag: String = "",
    val actionType: String = "NONE",
    val actionTitle: String = "",
    val actionPayload: String = "",
    val actionExecuted: Boolean = false,
    val hasAttachment: Boolean = false,
    val isOfflineReply: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1L,
    val fact: String,
    val category: String = "Preference",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "speaker_profiles")
data class SpeakerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val preferredLanguage: String = "AUTO",
    val personaNote: String = "",
    val pitchMultiplier: Float = 1.0f,
    val isDefault: Boolean = false
)
