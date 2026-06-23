package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String = "#2196F3",
    val sectionName: String = "General"
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val notebookId: Int = 0, // 0 = Uncategorized
    val isPinned: Boolean = false,
    val noteType: String = "TEXT", // TEXT, CHECKLIST, DRAWING, VOICE
    val tags: String = "", // comma-separated e.g. "work,idea"
    val checklistJson: String = "[]", // JSON string for checklists
    val strokesJson: String = "[]", // JSON string for drawings
    val mediaUri: String? = null, // image uri path
    val audioUri: String? = null, // voice note audio path
    val fileUri: String? = null, // other attached files path
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
    val colorHex: String = "", // custom background note card styling
    val reminderTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_passcode")
data class AppPasscode(
    @PrimaryKey val id: Int = 1,
    val passcodeHash: String,
    val hint: String = ""
)

// Auxiliary data model for checklist item JSON
data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean
)

// Auxiliary data models for drawing JSON
data class PointData(val x: Float, val y: Float)

data class DrawingStroke(
    val strokeId: String,
    val points: List<PointData>,
    val colorHex: String,
    val strokeWidth: Float,
    val brushType: String, // PEN, PENCIL, MARKER, ERASER
    val shapeType: String? = null, // RECTANGLE, CIRCLE, LINE, or null for default stream
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f
)
