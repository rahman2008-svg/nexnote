package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.dao.NoteDao
import com.example.data.model.AppPasscode
import com.example.data.model.Note
import com.example.data.model.Notebook

@Database(entities = [Notebook::class, Note::class, AppPasscode::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
