package com.example.data.database

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private var database: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nexnote_db"
            )
            .fallbackToDestructiveMigration()
            .build()
            database = db
            db
        }
    }
}
