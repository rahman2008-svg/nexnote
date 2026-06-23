package com.example.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.DatabaseProvider
import com.example.data.model.AppPasscode
import com.example.data.model.ChecklistItem
import com.example.data.model.DrawingStroke
import com.example.data.model.Note
import com.example.data.model.Notebook
import com.example.data.repository.NoteRepository
import com.example.util.JsonUtils
import com.example.util.ReminderReceiver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    
    // Core database flows
    val notebooks: StateFlow<List<Notebook>>
    val allNotes: StateFlow<List<Note>>
    val hiddenNotes: StateFlow<List<Note>>
    val passcode: StateFlow<AppPasscode?>

    // Interactive Dashboard States
    val activeNotebookId = MutableStateFlow(0) // 0 = All
    val searchQuery = MutableStateFlow("")
    val selectedTagFilter = MutableStateFlow<String?>(null)
    
    // Lock / Secure authentication states
    val isLockedNotebookUnlocked = MutableStateFlow(false)

    // Notes listing combined with filters (Search, Notebook folders, Tags)
    val filteredNotes: StateFlow<List<Note>>

    init {
        val database = DatabaseProvider.getDatabase(application)
        repository = NoteRepository(database.noteDao())
        
        notebooks = repository.allNotebooks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
        allNotes = repository.allNotes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        hiddenNotes = repository.hiddenNotes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        passcode = repository.passcode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        // Combine filter state to perform instant search indices
        filteredNotes = combine(allNotes, activeNotebookId, searchQuery, selectedTagFilter) { list, notebookId, query, selectedTag ->
            var result = list
            
            // 1. Notebook filter
            if (notebookId != 0) {
                result = result.filter { it.notebookId == notebookId }
            }
            
            // 2. Tag filter
            if (selectedTag != null) {
                result = result.filter { note ->
                    note.tags.split(",").map { it.trim().lowercase() }.contains(selectedTag.lowercase())
                }
            }
            
            // 3. Search text query index
            if (query.isNotBlank()) {
                val cleanQuery = query.trim().lowercase()
                result = result.filter { note ->
                    note.title.lowercase().contains(cleanQuery) ||
                    note.content.lowercase().contains(cleanQuery) ||
                    note.tags.lowercase().contains(cleanQuery)
                }
            }
            
            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // --- Notebook actions ---
    fun addNotebook(name: String, colorHex: String = "#2196F3", section: String = "General") {
        viewModelScope.launch {
            repository.insertNotebook(Notebook(name = name, colorHex = colorHex, sectionName = section))
        }
    }

    fun deleteNotebook(notebook: Notebook) {
        viewModelScope.launch {
            repository.deleteNotebook(notebook)
        }
    }

    // --- Note actions ---
    fun insertNote(note: Note, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertNote(note)
            onComplete(id)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.insertNote(note.copy(lastModified = System.currentTimeMillis()))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            cancelAlarm(note)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.insertNote(note.copy(isPinned = !note.isPinned, lastModified = System.currentTimeMillis()))
        }
    }

    // --- Passcode / Lock System ---
    fun setupPasscode(digits: String, hint: String) {
        viewModelScope.launch {
            repository.savePasscode(digits, hint)
            isLockedNotebookUnlocked.value = true
        }
    }

    fun removePasscode() {
        viewModelScope.launch {
            repository.clearPasscode()
            isLockedNotebookUnlocked.value = false
        }
    }

    fun checkPasscode(digits: String): Boolean {
        val current = passcode.value
        val matches = current != null && current.passcodeHash == digits
        if (matches) {
            isLockedNotebookUnlocked.value = true
        }
        return matches
    }

    fun lockPrivateMode() {
        isLockedNotebookUnlocked.value = false
    }

    // --- Rule-Based Smart Actions (Auto categorization, suggested tags, suggestions reminders, duplicates detector) ---

    fun autoCategorizeNoteContent(title: String, content: String): Int {
        val text = "$title $content".lowercase()
        // Simple taxonomy mapping
        val workKeywords = listOf("work", "meeting", "office", "project", "task", "invoice", "client", "schedule", "deadline", "todo")
        val studyKeywords = listOf("study", "exam", "book", "lecture", "course", "assignment", "homework", "math", "history", "science", "learn")
        val ideaKeywords = listOf("idea", "brainstorm", "concept", "inspiration", "creator", "build", "sandbox", "sketch", "draft")

        val workScore = workKeywords.count { text.contains(it) }
        val studyScore = studyKeywords.count { text.contains(it) }
        val ideaScore = ideaKeywords.count { text.contains(it) }

        val matchName = when {
            studyScore > workScore && studyScore > ideaScore -> "Study"
            workScore > studyScore && workScore > ideaScore -> "Work"
            ideaScore > studyScore && ideaScore > workScore -> "Ideas"
            else -> null
        } ?: return 0 // Uncategorized

        // Look for existing notebook with this name, otherwise use the first matching ID or keep 0
        notebooks.value.firstOrNull { it.name.lowercase() == matchName.lowercase() }?.let {
            return it.id
        }
        return 0
    }

    fun extractSuggestedTags(title: String, content: String): List<String> {
        val text = "$title $content".lowercase()
        val suggestions = mutableListOf<String>()

        // Look for native hashtags (#study #work)
        val hashtagRegex = Regex("#(\\w+)")
        hashtagRegex.findAll("$title $content").forEach { match ->
            suggestions.add(match.groupValues[1].lowercase())
        }

        // Context keyword tagging
        if (text.contains("recipe") || text.contains("cook") || text.contains("food")) suggestions.add("recipe")
        if (text.contains("shop") || text.contains("buy") || text.contains("grocery")) suggestions.add("shopping")
        if (text.contains("meeting") || text.contains("office") || text.contains("client")) suggestions.add("work")
        if (text.contains("exam") || text.contains("homework") || text.contains("study")) suggestions.add("study")
        if (text.contains("gym") || text.contains("workout") || text.contains("health") || text.contains("run")) suggestions.add("fitness")
        if (text.contains("travel") || text.contains("flight") || text.contains("hotel") || text.contains("vacation")) suggestions.add("travel")

        return suggestions.distinct()
    }

    fun findDuplicateNoteWarning(noteId: Int, title: String, content: String): Note? {
        if (title.isBlank() && content.isBlank()) return null
        return allNotes.value.firstOrNull { existing ->
            existing.id != noteId && (
                (title.isNotBlank() && existing.title.trim().lowercase() == title.trim().lowercase()) ||
                (content.isNotBlank() && existing.content.trim() == content.trim())
            )
        }
    }

    fun parseReminderTriggerString(content: String): Long? {
        val text = content.lowercase()
        val calendar = Calendar.getInstance()

        // Simple rule based parser for human language
        when {
            text.contains("remind me tomorrow") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 9) // 9 AM tomorrow
                calendar.set(Calendar.MINUTE, 0)
                return calendar.timeInMillis
            }
            text.contains("remind me at 5pm") || text.contains("remind me at 5 pm") -> {
                calendar.set(Calendar.HOUR_OF_DAY, 17)
                calendar.set(Calendar.MINUTE, 0)
                if (calendar.timeInMillis < System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1) // Tomorrow 5 PM
                }
                return calendar.timeInMillis
            }
            text.contains("remind me next monday") -> {
                // Find next monday
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                calendar.set(Calendar.HOUR_OF_DAY, 9)
                calendar.set(Calendar.MINUTE, 0)
                return calendar.timeInMillis
            }
            text.contains("remind me tonight") -> {
                calendar.set(Calendar.HOUR_OF_DAY, 20) // 8 PM tonight
                calendar.set(Calendar.MINUTE, 0)
                if (calendar.timeInMillis < System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1) // Tomorrow 8 PM if tonight has passed
                }
                return calendar.timeInMillis
            }
        }
        return null
    }

    // --- Offline Notifications Schedule ---
    fun scheduleAlarm(note: Note, timestamp: Long) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("NOTE_TITLE", "Reminder: ${note.title}")
            putExtra("NOTE_CONTENT", note.content.take(120))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
            }
            
            // Save reminderTimestamp in DB for UI reference
            updateNote(note.copy(reminderTimestamp = timestamp))
            Log.d("NoteViewModel", "Successfully scheduled alarm for note ${note.id} at $timestamp")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Alarm schedule failed", e)
        }
    }

    fun cancelAlarm(note: Note) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        updateNote(note.copy(reminderTimestamp = null))
    }
}
