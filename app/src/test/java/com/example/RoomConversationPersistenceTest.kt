package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationThreadEntity
import com.example.data.local.KothaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomConversationPersistenceTest {

    private lateinit var dbPath: String
    private lateinit var database: KothaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbPath = "test_kotha_persistence.db"
        context.deleteDatabase(dbPath)
        database = Room.databaseBuilder(context, KothaDatabase::class.java, dbPath)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbPath)
    }

    @Test
    fun `conversation threads and messages persist across database reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val threadDao = database.threadDao()
        val messageDao = database.chatMessageDao()

        val threadId = threadDao.insertThread(
            ConversationThreadEntity(
                title = "সকালের অ্যালার্ম",
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        messageDao.insertMessage(
            ChatMessageEntity(
                threadId = threadId,
                isUser = true,
                content = "সকাল ৭টায় অ্যালার্ম দাও",
                timestamp = 1100L
            )
        )
        messageDao.insertMessage(
            ChatMessageEntity(
                threadId = threadId,
                isUser = false,
                content = "সকাল ৭টার অ্যালার্ম সেট করার কার্ড তৈরি করেছি।",
                emotionTag = "Neutral",
                languageTag = "Bengali",
                timestamp = 1200L
            )
        )
        threadDao.touchThreadUpdatedAt(threadId, 1200L)

        // Simulate app restart by closing and reopening the Room database
        database.close()
        database = Room.databaseBuilder(context, KothaDatabase::class.java, dbPath)
            .allowMainThreadQueries()
            .build()

        val persistedThreads = database.threadDao().observeThreads().first()
        assertEquals(1, persistedThreads.size)
        assertEquals("সকালের অ্যালার্ম", persistedThreads.first().title)
        assertEquals(1200L, persistedThreads.first().updatedAt)

        val persistedMessages = database.chatMessageDao().getMessagesForThreadOnce(threadId)
        assertEquals(2, persistedMessages.size)
        assertEquals("সকাল ৭টায় অ্যালার্ম দাও", persistedMessages[0].content)
        assertTrue(persistedMessages[0].isUser)
        assertEquals("Bengali", persistedMessages[1].languageTag)
    }

    @Test
    fun `deleting thread cascades and removes its messages`() = runTest {
        val threadId = database.threadDao().insertThread(
            ConversationThreadEntity(title = "Temp Thread")
        )
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(threadId = threadId, isUser = true, content = "Hello")
        )

        database.threadDao().deleteThread(threadId)

        val remainingMessages = database.chatMessageDao().getMessagesForThreadOnce(threadId)
        assertTrue(remainingMessages.isEmpty())
    }
}
