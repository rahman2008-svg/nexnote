package com.example.data.dao

import androidx.room.*
import com.example.data.model.AppPasscode
import com.example.data.model.Note
import com.example.data.model.Notebook
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // --- Notebooks ---
    @Query("SELECT * FROM notebooks ORDER BY name ASC")
    fun getAllNotebooks(): Flow<List<Notebook>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: Notebook): Long

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    // --- Notes ---
    @Query("SELECT * FROM notes WHERE isHidden = 0 ORDER BY isPinned DESC, lastModified DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isHidden = 1 ORDER BY lastModified DESC")
    fun getHiddenNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteByIdSuspend(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE notebookId = :notebookId AND isHidden = 0 ORDER BY isPinned DESC, lastModified DESC")
    fun getNotesByNotebook(notebookId: Int): Flow<List<Note>>

    // --- Passcode Lock ---
    @Query("SELECT * FROM app_passcode WHERE id = 1")
    fun getPasscode(): Flow<AppPasscode?>

    @Query("SELECT * FROM app_passcode WHERE id = 1")
    suspend fun getPasscodeSuspend(): AppPasscode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasscode(passcode: AppPasscode)

    @Query("DELETE FROM app_passcode WHERE id = 1")
    suspend fun deletePasscode()
}
