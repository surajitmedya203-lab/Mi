package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationThreadEntity::class,
        ChatMessageEntity::class,
        MemoryFactEntity::class,
        SpeakerProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KothaDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun speakerProfileDao(): SpeakerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: KothaDatabase? = null

        fun getInstance(context: Context): KothaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KothaDatabase::class.java,
                    "kotha_assistant.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
