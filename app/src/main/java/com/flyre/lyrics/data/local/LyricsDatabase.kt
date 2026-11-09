package com.flyre.lyrics.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LyricEntity::class], version = 1, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricDao(): LyricDao

    companion object {
        @Volatile
        private var INSTANCE: LyricsDatabase? = null

        fun getInstance(context: Context): LyricsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "flyre_lyrics.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

