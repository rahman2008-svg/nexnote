package com.example.data.repository

import com.example.data.dao.NoteDao
import com.example.data.model.AppPasscode
import com.example.data.model.Note
import com.example.data.model.Notebook
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    val allNotebooks: Flow<List<Notebook>> = noteDao.getAllNotebooks()
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val hiddenNotes: Flow<List<Note>> = noteDao.getHiddenNotes()
    val passcode: Flow<AppPasscode?> = noteDao.getPasscode()

    fun getNotesByNotebook(notebookId: Int): Flow<List<Note>> = noteDao.getNotesByNotebook(notebookId)

    fun getNoteById(id: Int): Flow<Note?> = noteDao.getNoteById(id)

    suspend fun getNoteByIdSuspend(id: Int): Note? = noteDao.getNoteByIdSuspend(id)

    suspend fun insertNotebook(notebook: Notebook): Long = noteDao.insertNotebook(notebook)

    suspend fun deleteNotebook(notebook: Notebook) = noteDao.deleteNotebook(notebook)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun getPasscodeSuspend(): AppPasscode? = noteDao.getPasscodeSuspend()

    suspend fun savePasscode(passcode: String, hint: String) {
        noteDao.insertPasscode(AppPasscode(passcodeHash = passcode, hint = hint))
    }

    suspend fun clearPasscode() {
        noteDao.deletePasscode()
    }
}
