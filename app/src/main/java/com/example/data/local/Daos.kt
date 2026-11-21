package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM conversation_threads ORDER BY updatedAt DESC")
    fun observeThreads(): Flow<List<ConversationThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ConversationThreadEntity): Long

    @Query("UPDATE conversation_threads SET title = :title, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateThreadTitle(threadId: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversation_threads SET updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun touchThreadUpdatedAt(threadId: Long, updatedAt: Long)

    @Query("DELETE FROM conversation_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("DELETE FROM conversation_threads")
    suspend fun clearAllThreads()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForThread(threadId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForThreadOnce(threadId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET actionExecuted = 1 WHERE id = :messageId")
    suspend fun markActionExecuted(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_facts WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeMemories(profileId: Long): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getMemoriesOnce(profileId: Long): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryFactEntity): Long

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllMemories()
}

@Dao
interface SpeakerProfileDao {
    @Query("SELECT * FROM speaker_profiles ORDER BY isDefault DESC, id ASC")
    fun observeProfiles(): Flow<List<SpeakerProfileEntity>>

    @Query("SELECT * FROM speaker_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): SpeakerProfileEntity?

    @Query("SELECT COUNT(*) FROM speaker_profiles")
    suspend fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SpeakerProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: SpeakerProfileEntity)

    @Query("DELETE FROM speaker_profiles WHERE id = :id AND isDefault = 0")
    suspend fun deleteProfile(id: Long)
}
