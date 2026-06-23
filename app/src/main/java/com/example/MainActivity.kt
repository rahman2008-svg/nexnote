@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ChecklistItem
import com.example.data.model.DrawingStroke
import com.example.data.model.Note
import com.example.data.model.Notebook
import com.example.ui.components.DrawingCanvas
import com.example.ui.viewmodel.NoteViewModel
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import com.example.util.ExportHelper
import com.example.util.JsonUtils
import com.example.util.RichTextParser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationGate()
                }
            }
        }
    }
}

// Custom Material 3 Cosmic Palette Theme definitions
@Composable
fun NexNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),         // Deep Purple
            secondary = Color(0xFFE8DEF8),       // Light Purple
            tertiary = Color(0xFF7D5260),        // Deep Rose/Lavender
            background = Color(0xFFFDF8F6),      // Soft Warm Peach White
            surface = Color(0xFFF7F2FA),         // Accent Surface Container
            surfaceVariant = Color(0xFFF3EDF7),  // Muted M3 Layout Background
            onPrimary = Color.White,
            onSecondary = Color(0xFF1D192B),
            onBackground = Color(0xFF1D1B1E),
            onSurface = Color(0xFF1D1B1E),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFFCAC4D0)
        ),
        content = content
    )
}

// App routing and passcode gate overlay wrapper
@Composable
fun AppNavigationGate() {
    val context = LocalContext.current
    val viewModel: NoteViewModel = viewModel()
    
    val passcodeState by viewModel.passcode.collectAsState()
    val isUnlocked by viewModel.isLockedNotebookUnlocked.collectAsState()
    
    // Core navigation states
    var currentView by remember { mutableStateOf("DASHBOARD") } // DASHBOARD, EDITOR, SETTINGS, LOCK_SECURE
    var editingNote by remember { mutableStateOf<Note?>(null) }
    
    // Audio Player helper lifecycle
    val audioPlayer = remember { AudioPlayerHelper() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Authenticate check: If a device lock PIN is active in database and not yet unlocked
        if (passcodeState != null && !isUnlocked) {
            PasscodeLockScreen(
                hint = passcodeState?.hint ?: "",
                onUnlocked = { digits ->
                    val verified = viewModel.checkPasscode(digits)
                    if (!verified) {
                        Toast.makeText(context, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Unlocked Notebook successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            // Screen router
            AnimatedContent(
                targetState = currentView,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                },
                label = "routing_anim"
            ) { target ->
                when (target) {
                    "SETTINGS" -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = { currentView = "DASHBOARD" }
                    )
                    "EDITOR" -> NoteEditorLayout(
                        note = editingNote,
                        viewModel = viewModel,
                        audioPlayer = audioPlayer,
                        onClose = {
                            currentView = "DASHBOARD"
                            editingNote = null
                        }
                    )
                    else -> DashboardLayout(
                        viewModel = viewModel,
                        audioPlayer = audioPlayer,
                        onEditNote = { note ->
                            editingNote = note
                            currentView = "EDITOR"
                        },
                        onCreateNote = {
                            editingNote = null
                            currentView = "EDITOR"
                        },
                        onNavigateSettings = { currentView = "SETTINGS" }
                    )
                }
            }
        }
    }
}

// --- SECURE LOCK PIN SCREEN ---
@Composable
fun PasscodeLockScreen(
    hint: String,
    onUnlocked: (String) -> Unit
) {
    var digits by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Lock",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacers(16)
        Text(
            text = "NexNote Vault Lock",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Please enter your 4-digit security code PIN",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacers(24)
        
        // Pin bullets tracker indicator rows
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { idx ->
                val filled = idx < digits.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
        
        if (hint.isNotBlank()) {
            Spacers(8)
            Text(
                text = "Reminder Hint: $hint",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.tertiary,
                fontStyle = FontStyle.Italic
            )
        }
        
        Spacers(32)
        
        // Custom 3x4 digital keypad
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Clear", "0", "Delete")
            )
            
            keys.forEach { rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    rowKeys.forEach { key ->
                        Button(
                            onClick = {
                                when (key) {
                                    "Clear" -> digits = ""
                                    "Delete" -> if (digits.isNotEmpty()) digits = digits.dropLast(1)
                                    else -> {
                                        if (digits.length < 4) {
                                            digits += key
                                            if (digits.length == 4) {
                                                onUnlocked(digits)
                                                digits = ""
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (key == "Clear" || key == "Delete") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(80.dp, 60.dp)
                                .testTag("pin_key_$key")
                        ) {
                            Text(
                                text = key,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- MAIN NOTEBOOK DASHBOARD (TABLET DUAL SPLIT & COMPACT PHONE MOBILE LAYOUTS) ---
@Composable
fun DashboardLayout(
    viewModel: NoteViewModel,
    audioPlayer: AudioPlayerHelper,
    onEditNote: (Note) -> Unit,
    onCreateNote: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isTablet = screenWidth > 640 // Material 3 guidelines for supporting split views

    var activeViewNoteTablets by remember { mutableStateOf<Note?>(null) }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            DashboardHeader(
                viewModel = viewModel,
                onNavigateSettings = onNavigateSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNote,
                containerColor = Color(0xFFD0BCFF),
                contentColor = Color(0xFF1D192B),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Note", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isTablet) {
            // Adaptive Tablet Split view split 40% left, 60% right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.4f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    DashboardCoreContent(
                        viewModel = viewModel,
                        audioPlayer = audioPlayer,
                        onNoteSelected = { note ->
                            if (note.isLocked && !viewModel.isLockedNotebookUnlocked.value) {
                                // Locked state handler
                                Toast.makeText(context, "Note is private! Authenticate first.", Toast.LENGTH_SHORT).show()
                            } else {
                                activeViewNoteTablets = note
                                onEditNote(note)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.6f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    if (activeViewNoteTablets != null) {
                        Text(
                            text = "Split-Pane Active Reader",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacers(8)
                        Text(
                            text = activeViewNoteTablets?.title ?: "No Title",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacers(12)
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Spacers(12)
                        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text(
                                text = RichTextParser.parseRichText(activeViewNoteTablets?.content ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                        Spacers(8)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onEditNote(activeViewNoteTablets!!) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Edit Note")
                            }
                            OutlinedButton(
                                onClick = { activeViewNoteTablets = null }
                            ) {
                                Text("Close Reader", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacers(12)
                                Text("Select a note to launch visual split-screen reading view", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Standard compact phone layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                DashboardCoreContent(
                    viewModel = viewModel,
                    audioPlayer = audioPlayer,
                    onNoteSelected = onEditNote
                )
            }
        }
    }
}

// Shared core lists compiler between mobile & tablet split-pane
@Composable
fun DashboardCoreContent(
    viewModel: NoteViewModel,
    audioPlayer: AudioPlayerHelper,
    onNoteSelected: (Note) -> Unit
) {
    val context = LocalContext.current
    
    val notebooks by viewModel.notebooks.collectAsState()
    val notes by viewModel.filteredNotes.collectAsState()
    val activeCategory by viewModel.activeNotebookId.collectAsState()
    val currentTagFilter by viewModel.selectedTagFilter.collectAsState()

    // Add notebook input system state
    var showAddNotebookDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Horizontal Notebooks Carousel
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notebook Folders",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { showAddNotebookDialog = true },
                        modifier = Modifier.testTag("add_notebook_btn")
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Notebook", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Default folder option
                    item {
                        FilterChip(
                            selected = activeCategory == 0,
                            onClick = { viewModel.activeNotebookId.value = 0 },
                            label = { Text("All Notebooks") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    
                    items(notebooks) { notebook ->
                        val itemColor = try { Color(android.graphics.Color.parseColor(notebook.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                        FilterChip(
                            selected = activeCategory == notebook.id,
                            onClick = { viewModel.activeNotebookId.value = notebook.id },
                            label = { Text(notebook.name) },
                            leadingIcon = {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = itemColor, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = itemColor.copy(alpha = 0.82f),
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
        
        // Tags Filter section if notes tags exist
        val activeTagsList = notes.flatMap { it.tags.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        
        if (activeTagsList.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Suggested #Tags",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacers(4)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            SuggestionChip(
                                onClick = { viewModel.selectedTagFilter.value = null },
                                label = { Text("Clear filter") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (currentTagFilter == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (currentTagFilter == null) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        
                        items(activeTagsList) { tag ->
                            SuggestionChip(
                                onClick = { viewModel.selectedTagFilter.value = tag },
                                label = { Text("#$tag") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (currentTagFilter == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (currentTagFilter == tag) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // Active listing of Notes
        if (notes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.NoteAlt,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacers(12)
                        Text(
                            text = "Workspace Empty",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap the '+' icon to sketch beautiful drawings, checklists, checklists formats or plain rich texts.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(notes) { note ->
                NoteCardItem(
                    note = note,
                    viewModel = viewModel,
                    onClick = { onNoteSelected(note) }
                )
            }
        }
        
        // Aesthetic footer attribution to the developer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 24.dp))
                Spacers(12)
                Text(
                    text = "NexNote • Secure Local Workspace",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Handcrafted by Prince AR Abdur Rahman\n© 2026 NexVora Lab's Ofc",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // Modal to create a folder notebook
    if (showAddNotebookDialog) {
        var newFolderName by remember { mutableStateOf("") }
        var selectedColorCode by remember { mutableStateOf("#2196F3") }
        
        AlertDialog(
            onDismissRequest = { showAddNotebookDialog = false },
            title = { Text("Create Folder Notebook", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Notebook Name") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.testTag("notebook_name_input")
                    )
                    Spacers(12)
                    Text("Select Theme Folder Color:", color = Color.Gray, fontSize = 12.sp)
                    Spacers(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val palettes = listOf("#2196F3", "#00E5FF", "#4CAF50", "#FFC107", "#FF5722", "#E91E63", "#9C27B0")
                        palettes.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (selectedColorCode == hex) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorCode = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.addNotebook(newFolderName, selectedColorCode)
                            showAddNotebookDialog = false
                        }
                    },
                    modifier = Modifier.testTag("notebook_dialog_confirm")
                ) {
                    Text("Create Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNotebookDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF111722)
        )
    }
}

// Customized styled Note list wrapper item
@Composable
fun NoteCardItem(
    note: Note,
    viewModel: NoteViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isExpandedMenu by remember { mutableStateOf(false) }

    val noteColor = if (note.colorHex.isNotBlank() && note.colorHex.startsWith("#") && note.colorHex != "#111722") {
        try { Color(android.graphics.Color.parseColor(note.colorHex)).copy(alpha = 0.9f) } catch(e:Exception){
            if (note.noteType == "CHECKLIST") Color(0xFFFFFFFF) else if (note.noteType == "VOICE") Color(0xFFE8DEF8) else Color(0xFFF7F2FA)
        }
    } else {
        if (note.noteType == "CHECKLIST") Color(0xFFFFFFFF) else if (note.noteType == "VOICE") Color(0xFFE8DEF8) else Color(0xFFF7F2FA)
    }

    val lastModifiedHumanReadable = remember(note.lastModified) {
        val formatter = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        formatter.format(Date(note.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(containerColor = noteColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Meta Row features
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pin Badge inside notes list
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    val typeLabel = when (note.noteType) {
                        "CHECKLIST" -> "Checklist List"
                        "DRAWING" -> "Sketch Canvas"
                        "VOICE" -> "Dictation Audio"
                        else -> "Notebook Draft"
                    }
                    Text(
                        text = typeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Card quick toggle option dropdown dropdown
                Box {
                    IconButton(
                        onClick = { isExpandedMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = isExpandedMenu,
                        onDismissRequest = { isExpandedMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pin Note", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                viewModel.togglePin(note)
                                isExpandedMenu = false
                            },
                            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as PDF", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                isExpandedMenu = false
                                val path = ExportHelper.exportToPdf(context, note)
                                if (path != null) {
                                    Toast.makeText(context, "Saved PDF to Cache: ${path.name}", Toast.LENGTH_LONG).show()
                                    // Trigger sharing dialog directly
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", path)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share your NexNote PDF"))
                                } else {
                                    Toast.makeText(context, "Export PDF Failed!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Note", color = Color.Red) },
                            onClick = {
                                viewModel.deleteNote(note)
                                isExpandedMenu = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }
            
            Spacers(8)
            
            // Title + Contents Body Text
            Text(
                text = note.title.ifBlank { "Untitled Draft" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacers(4)
            
            // Rich parsed content summary line selector
            if (note.noteType == "CHECKLIST") {
                val list = remember(note.checklistJson) { JsonUtils.deserializeChecklist(note.checklistJson) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    list.take(2).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (item.isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (item.isChecked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.text,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = RichTextParser.parseRichText(note.content),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
            
            Spacers(12)
            
            // Floating tag chips list representation
            if (note.tags.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    note.tags.split(",").filter { it.isNotBlank() }.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8DEF8), RoundedCornerShape(12.dp))
                                .border(0.5.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "#${tag.trim()}", fontSize = 10.sp, color = Color(0xFF1D192B))
                        }
                    }
                }
                Spacers(10)
            }
            
            Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
            Spacers(8)
            
            // Mini visual indicator tags (Alarm, Audio dictations, image drawings vectors)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lastModifiedHumanReadable,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.reminderTimestamp != null) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = "Alarm Active", tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                    }
                    if (note.audioUri != null) {
                        Icon(Icons.Filled.Mic, contentDescription = "Voice notes", tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    }
                    if (note.mediaUri != null) {
                        Icon(Icons.Filled.Image, contentDescription = "Photos", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                    if (note.strokesJson.isNotBlank() && note.strokesJson != "[]") {
                        Icon(Icons.Filled.Gesture, contentDescription = "Sketch Drawings", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                    }
                    if (note.isLocked) {
                        Icon(Icons.Filled.Lock, contentDescription = "Locked Drafts", tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// --- DASHBOARD SEARCH HEADER COMPONENT ---
@Composable
fun DashboardHeader(
    viewModel: NoteViewModel,
    onNavigateSettings: () -> Unit
) {
    val searchState by viewModel.searchQuery.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NexNote",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Offline Smart Notebook Platform",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onNavigateSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Workspace Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacers(14)
        
        // Search text input bar indexing filters
        OutlinedTextField(
            value = searchState,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Search title, tags, or notebook indexing...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (searchState.isNotBlank()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_text_input")
        )
    }
}

// --- NOTE ACTIONS & RICH EDITOR VIEW LAYOUT ---
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NoteEditorLayout(
    note: Note?,
    viewModel: NoteViewModel,
    audioPlayer: AudioPlayerHelper,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    // Core parameters state
    var noteId by remember { mutableStateOf(note?.id ?: 0) }
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var notebookId by remember { mutableStateOf(note?.notebookId ?: 0) }
    var colorHex by remember { mutableStateOf(note?.colorHex ?: "#111722") }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }
    var tags by remember { mutableStateOf(note?.tags ?: "") }
    var noteType by remember { mutableStateOf(note?.noteType ?: "TEXT") } // TEXT, DRAWING, CHECKLIST, VOICE
    var reminderTimestamp by remember { mutableStateOf(note?.reminderTimestamp) }
    
    // JSON details state parameters
    var checklistItems by remember { mutableStateOf(JsonUtils.deserializeChecklist(note?.checklistJson)) }
    var strokesList by remember { mutableStateOf(JsonUtils.deserializeStrokes(note?.strokesJson)) }
    
    // Media attachment states URIs
    var currentPhotoUri by remember { mutableStateOf<String?>(note?.mediaUri) }
    var currentAudioUri by remember { mutableStateOf<String?>(note?.audioUri) }

    // Editor tab: TEXT, CANVAS, CHECKLIST, AUDIO, ATTACHMENTS
    var editorTab by remember { mutableStateOf(if (note?.noteType != null) note.noteType else "TEXT") }

    // Auto save triggers when content, title or checklists shifts
    LaunchedEffect(title, content, checklistItems, strokesList, notebookId, colorHex, isPinned, tags, noteType, currentPhotoUri, currentAudioUri, reminderTimestamp) {
        if (title.isNotEmpty() || content.isNotEmpty() || checklistItems.isNotEmpty() || strokesList.isNotEmpty()) {
            val checklistSerialized = JsonUtils.serializeChecklist(checklistItems)
            val drawingSerialized = JsonUtils.serializeStrokes(strokesList)
            
            val updatedNote = Note(
                id = noteId,
                title = title,
                content = content,
                notebookId = notebookId,
                isPinned = isPinned,
                noteType = noteType,
                tags = tags,
                checklistJson = checklistSerialized,
                strokesJson = drawingSerialized,
                mediaUri = currentPhotoUri,
                audioUri = currentAudioUri,
                isLocked = note?.isLocked ?: false,
                colorHex = colorHex,
                reminderTimestamp = reminderTimestamp,
                createdAt = note?.createdAt ?: System.currentTimeMillis(),
                lastModified = System.currentTimeMillis()
            )
            
            if (noteId == 0) {
                // Perform first save creation
                viewModel.insertNote(updatedNote) { newGeneratedId ->
                    noteId = newGeneratedId.toInt()
                }
            } else {
                viewModel.updateNote(updatedNote)
            }
        }
    }

    // Smart rules extraction insights (Offline Assistant)
    val recommendedFolderId = remember(title, content) {
        viewModel.autoCategorizeNoteContent(title, content)
    }
    val suggestedTags = remember(title, content) {
        viewModel.extractSuggestedTags(title, content)
    }
    val duplicateNote = remember(title, content) {
        viewModel.findDuplicateNoteWarning(noteId, title, content)
    }
    val suggestedReminderTime = remember(content) {
        viewModel.parseReminderTriggerString(content)
    }

    // Standard photo picker implementation launcher activity
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentPhotoUri = it.toString()
            Toast.makeText(context, "Offline photo attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (noteId == 0) "Creating Note" else "Nexus Draft Editor",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("editor_back_btn")) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Close & Save", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    // Pinned state toggle
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (isPinned) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Share options text
                    IconButton(onClick = {
                        val sharingText = buildString {
                            appendLine("NexNote Notebook: $title")
                            appendLine("-----------------")
                            appendLine(content)
                            if (noteType == "CHECKLIST") {
                                appendLine("\nChecklist Items:")
                                checklistItems.forEach { item ->
                                    val status = if (item.isChecked) "[x]" else "[ ]"
                                    appendLine("$status ${item.text}")
                                }
                            }
                            appendLine("\n© 2026 NexVora Labs Ofc. Handcrafted by Prince AR Abdur Rahman.")
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, sharingText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Draft Text"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Smart Assistant Warning alerts boxes row
            AnimatedVisibility(visible = duplicateNote != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF3D00).copy(alpha = 0.15f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, "Warning", tint = Color(0xFFFF3D00))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duplicate draft detected! Matches note title or contents.",
                        fontSize = 11.sp,
                        color = Color(0xFFFF3D00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = recommendedFolderId != 0 && recommendedFolderId != notebookId) {
                val recommendedFolder = viewModel.notebooks.value.firstOrNull { it.id == recommendedFolderId }
                if (recommendedFolder != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Lightbulb, "Smart Recommendation", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Assistant: Move draft to folder '${recommendedFolder.name}'?",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { notebookId = recommendedFolder.id },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Organize", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = suggestedReminderTime != null && reminderTimestamp == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Alarm, "Quick Reminder", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Assistant: Set auto clock alarm reminder?",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            suggestedReminderTime?.let { epoch ->
                                val dummyNote = Note(
                                    id = if (noteId == 0) 999 else noteId,
                                    title = title,
                                    content = content
                                )
                                viewModel.scheduleAlarm(dummyNote, epoch)
                                reminderTimestamp = epoch
                                Toast.makeText(context, "Alarm notification Scheduled offline!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Schedule", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // Note title field input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Enter Draft Title...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_title_input")
            )
            
            // Sub-meta selectors (Notebook placement, Tag inputs, alarm triggers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Folder placement chip
                val folders = viewModel.notebooks.collectAsState().value
                val currentFolder = folders.firstOrNull { it.id == notebookId }
                var showFolderPicker by remember { mutableStateOf(false) }
                
                AssistChip(
                    onClick = { showFolderPicker = true },
                    label = { Text(currentFolder?.name ?: "Categorize Folder", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) }
                )
                
                // Add tagging string inputs
                var showTagsEditor by remember { mutableStateOf(false) }
                AssistChip(
                    onClick = { showTagsEditor = true },
                    label = { Text(if (tags.isNotBlank()) "Tags: $tags" else "Attach #Tags", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Filled.Tag, null, tint = MaterialTheme.colorScheme.primary) }
                )

                if (showFolderPicker) {
                    AlertDialog(
                        onDismissRequest = { showFolderPicker = false },
                        title = { Text("Direct Draft To Folder:", color = MaterialTheme.colorScheme.onSurface) },
                        text = {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                item {
                                    ListItem(
                                        headlineContent = { Text("No Category (0)") },
                                        modifier = Modifier.clickable {
                                            notebookId = 0
                                            showFolderPicker = false
                                        }
                                    )
                                }
                                items(folders) { folder ->
                                    ListItem(
                                        headlineContent = { Text(folder.name) },
                                        leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                                        modifier = Modifier.clickable {
                                            notebookId = folder.id
                                            showFolderPicker = false
                                        }
                                    )
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showFolderPicker = false }) { Text("Cancel") }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                if (showTagsEditor) {
                    var inputTagsStr by remember { mutableStateOf(tags) }
                    AlertDialog(
                        onDismissRequest = { showTagsEditor = false },
                        title = { Text("Assign Tags (comma-separated):", color = MaterialTheme.colorScheme.onSurface) },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = inputTagsStr,
                                    onValueChange = { inputTagsStr = it },
                                    placeholder = { Text("e.g. study,work,ideas") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors()
                                )
                                if (suggestedTags.isNotEmpty()) {
                                    Spacers(8)
                                    Text("Suggested Tags from analyzer:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        suggestedTags.forEach { suggested ->
                                            InputChip(
                                                selected = false,
                                                onClick = {
                                                    val parts = inputTagsStr.split(",").map { it.trim().lowercase() }.toMutableList()
                                                    if (!parts.contains(suggested)) {
                                                        parts.add(suggested)
                                                        inputTagsStr = parts.filter { it.isNotBlank() }.joinToString(",")
                                                    }
                                                },
                                                label = { Text("#$suggested") }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                tags = inputTagsStr
                                showTagsEditor = false
                            }) { Text("Apply Tags") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTagsEditor = false }) { Text("Cancel") }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }

            // Quick reminder alarm schedulers setup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val calendar = Calendar.getInstance()
                val datePicker = DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, min ->
                                calendar.set(Calendar.YEAR, year)
                                calendar.set(Calendar.MONTH, month)
                                calendar.set(Calendar.DAY_OF_MONTH, day)
                                calendar.set(Calendar.HOUR_OF_DAY, hour)
                                calendar.set(Calendar.MINUTE, min)
                                val dummyNote = Note(
                                    id = if (noteId == 0) 999 else noteId,
                                    title = title,
                                    content = content
                                )
                                viewModel.scheduleAlarm(dummyNote, calendar.timeInMillis)
                                reminderTimestamp = calendar.timeInMillis
                                Toast.makeText(context, "Alarm Active scheduled!", Toast.LENGTH_SHORT).show()
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                AssistChip(
                    onClick = { datePicker.show() },
                    label = {
                        Text(
                            text = if (reminderTimestamp != null) {
                                val formatter = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                                "Alarm: " + formatter.format(Date(reminderTimestamp!!))
                            } else "Add Alert Reminder",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary) }
                )
                
                if (reminderTimestamp != null) {
                    IconButton(
                        onClick = {
                            val dummyNote = Note(id = noteId, title = title, content = content)
                            viewModel.cancelAlarm(dummyNote)
                            reminderTimestamp = null
                            Toast.makeText(context, "Reminder Cancelled!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel Alarm", tint = Color.Red)
                    }
                }
            }

            Spacers(8)

            // Switch tools Editor Tabs selection chips Row
            ScrollableTabRow(
                selectedTabIndex = when (editorTab) {
                    "CANVAS" -> 1
                    "CHECKLIST" -> 2
                    "AUDIO" -> 3
                    "ATTACHMENTS" -> 4
                    else -> 0
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = editorTab == "TEXT", onClick = { editorTab = "TEXT"; noteType = "TEXT" }) {
                    Text("Rich Body Text", modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tab(selected = editorTab == "CANVAS", onClick = { editorTab = "CANVAS"; noteType = "DRAWING" }) {
                    Text("Stylus Canvas", modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tab(selected = editorTab == "CHECKLIST", onClick = { editorTab = "CHECKLIST"; noteType = "CHECKLIST" }) {
                    Text("Checklists List", modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tab(selected = editorTab == "AUDIO", onClick = { editorTab = "AUDIO"; noteType = "VOICE" }) {
                    Text("Dictaphone Rec", modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tab(selected = editorTab == "ATTACHMENTS", onClick = { editorTab = "ATTACHMENTS" }) {
                    Text("Offline Files", modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Tabs implementation panels
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (editorTab) {
                    "CANVAS" -> {
                        // Drawing support component engine layout
                        DrawingView(
                            strokes = strokesList,
                            onStrokesChanged = { strokesList = it }
                        )
                    }
                    "CHECKLIST" -> {
                        // Interactive checklists list
                        ChecklistLayout(
                            items = checklistItems,
                            onItemsChanged = { checklistItems = it }
                        )
                    }
                    "AUDIO" -> {
                        // Voice recordings captures
                        VoiceRecordView(
                            currentAudioPath = currentAudioUri,
                            audioPlayer = audioPlayer,
                            onAudioAttached = { path -> currentAudioUri = path }
                        )
                    }
                    "ATTACHMENTS" -> {
                        // Attachments, custom picture widgets
                        AttachmentsView(
                            photoUriStr = currentPhotoUri,
                            onAttachPhoto = { mediaLauncher.launch("image/*") },
                            onClearPhoto = { currentPhotoUri = null }
                        )
                    }
                    else -> {
                        // Standard Rich text Editor panel
                        RichTextEditorPanel(
                            textValue = content,
                            onValueChange = { content = it }
                        )
                    }
                }
            }
        }
    }
}

// --- TAB SUB-VIEW: RICH TEXT BODY EDITOR WITH FORMATTING TOOLBARS ---
@Composable
fun RichTextEditorPanel(
    textValue: String,
    onValueChange: (String) -> Unit
) {
    var isLivePreviewActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Text format tools actions bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val toggled = RichTextParser.toggleStyleTag(textValue, 0, textValue.length, "b")
                onValueChange(toggled.first)
            }) {
                Icon(Icons.Filled.FormatBold, "Bold", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                val toggled = RichTextParser.toggleStyleTag(textValue, 0, textValue.length, "i")
                onValueChange(toggled.first)
            }) {
                Icon(Icons.Filled.FormatItalic, "Italic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                val toggled = RichTextParser.toggleStyleTag(textValue, 0, textValue.length, "u")
                onValueChange(toggled.first)
            }) {
                Icon(Icons.Filled.FormatUnderlined, "Underline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                onValueChange(textValue + "<h1></h1>")
            }) {
                Text("H1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            IconButton(onClick = {
                onValueChange(textValue + "<h2></h2>")
            }) {
                Text("H2", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            IconButton(onClick = {
                onValueChange(textValue + "\n• ")
            }) {
                Icon(Icons.Filled.FormatListBulleted, "Bullets", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Preview format parsing toggle switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live Parser", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Switch(
                    checked = isLivePreviewActive,
                    onCheckedChange = { isLivePreviewActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        if (isLivePreviewActive) {
            // Parsed typography display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = RichTextParser.parseRichText(textValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        } else {
            // Editable field
            OutlinedTextField(
                value = textValue,
                onValueChange = onValueChange,
                placeholder = { Text("Draft your thoughts here. You can click on the toolbar format filters above e.g. <b>boldText</b> to style sections inline.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("note_content_input")
            )
        }
    }
}

// --- TAB SUB-VIEW: INTERACTIVE FLUID MULTI-TOOL DRAWING LAYER ---
@Composable
fun DrawingView(
    strokes: List<DrawingStroke>,
    onStrokesChanged: (List<DrawingStroke>) -> Unit
) {
    var selectedColor by remember { mutableStateOf("#6750A4") }
    var selectedWidth by remember { mutableStateOf(8f) }
    var selectedTool by remember { mutableStateOf("PEN") } // PEN, PENCIL, MARKER, ERASER
    var selectedShape by remember { mutableStateOf<String?>(null) } // null = freehand, LINE, RECTANGLE, CIRCLE

    Column(modifier = Modifier.fillMaxSize()) {
        // Painting palette and sliders sidebar/topbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            // Row 1: Brush instruments and clear tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { selectedTool = "PEN"; selectedShape = null },
                        modifier = Modifier.background(
                            if (selectedTool == "PEN" && selectedShape == null) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Gesture,
                            contentDescription = "Pen Draw",
                            tint = if (selectedTool == "PEN" && selectedShape == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { selectedTool = "PENCIL"; selectedShape = null },
                        modifier = Modifier.background(
                            if (selectedTool == "PENCIL" && selectedShape == null) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Pencil Thin",
                            tint = if (selectedTool == "PENCIL" && selectedShape == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { selectedTool = "MARKER"; selectedShape = null },
                        modifier = Modifier.background(
                            if (selectedTool == "MARKER" && selectedShape == null) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = "Marker Transparent",
                            tint = if (selectedTool == "MARKER" && selectedShape == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { selectedTool = "ERASER"; selectedShape = null },
                        modifier = Modifier.background(
                            if (selectedTool == "ERASER") MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = "Eraser",
                            tint = if (selectedTool == "ERASER") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { onStrokesChanged(emptyList()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear Workspace", color = Color.Red, fontSize = 11.sp)
                }
            }

            // Row 2: Geometric widgets shapes controllers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vector Shapes:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                AssistChip(
                    onClick = { selectedShape = if (selectedShape == "LINE") null else "LINE" },
                    label = { Text("Line", fontSize = 10.sp, color = if (selectedShape == "LINE") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if (selectedShape == "LINE") MaterialTheme.colorScheme.tertiary else Color.Transparent)
                )
                AssistChip(
                    onClick = { selectedShape = if (selectedShape == "RECTANGLE") null else "RECTANGLE" },
                    label = { Text("Rectangle", fontSize = 10.sp, color = if (selectedShape == "RECTANGLE") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if (selectedShape == "RECTANGLE") MaterialTheme.colorScheme.tertiary else Color.Transparent)
                )
                AssistChip(
                    onClick = { selectedShape = if (selectedShape == "CIRCLE") null else "CIRCLE" },
                    label = { Text("Circle", fontSize = 10.sp, color = if (selectedShape == "CIRCLE") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if (selectedShape == "CIRCLE") MaterialTheme.colorScheme.tertiary else Color.Transparent)
                )
            }

            // Row 3: Colors picking hex points
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val drawColors = listOf("#6750A4", "#2196F3", "#4CAF50", "#FFC107", "#FF5722", "#E91E63", "#000000")
                Text("Palette:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        // Dynamic canvas contrast color mapping
                    }
                    items(drawColors) { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (selectedColor == hex) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }

            // Row 4: Sizing slider width selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Width: ${selectedWidth.toInt()}dp", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Slider(
                    value = selectedWidth,
                    onValueChange = { selectedWidth = it },
                    valueRange = 2f..36f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // The touch Canvas component
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            DrawingCanvas(
                strokes = strokes,
                selectedColorHex = selectedColor,
                selectedWidth = selectedWidth,
                selectedTool = selectedTool,
                selectedShape = selectedShape,
                onStrokesChanged = onStrokesChanged,
                backgroundColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// --- TAB SUB-VIEW: NATIVE CHECKLISTS COMPILER ---
@Composable
fun ChecklistLayout(
    items: List<ChecklistItem>,
    onItemsChanged: (List<ChecklistItem>) -> Unit
) {
    var nextItemText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top row: add checklist parameters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = nextItemText,
                onValueChange = { nextItemText = it },
                placeholder = { Text("Add task checkbox list item...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("checklist_input")
            )
            IconButton(
                onClick = {
                    if (nextItemText.isNotBlank()) {
                        val newItem = ChecklistItem(
                            id = UUID.randomUUID().toString(),
                            text = nextItemText,
                            isChecked = false
                        )
                        onItemsChanged(items + newItem)
                        nextItemText = ""
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .testTag("checklist_add_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Item", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        
        Spacers(16)
        
        // Lazy layout details rendering lists of checkboxes
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = { checked ->
                                val updated = items.map {
                                    if (it.id == item.id) it.copy(isChecked = checked) else it
                                }
                                onItemsChanged(updated)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.text,
                            color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val filtered = items.filter { it.id != item.id }
                            onItemsChanged(filtered)
                        }
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove Item", tint = Color.Red.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// --- TAB SUB-VIEW: OFFLINE VOICE RECORD CHIPS LAYOUT ---
@Composable
fun VoiceRecordView(
    currentAudioPath: String?,
    audioPlayer: AudioPlayerHelper,
    onAudioAttached: (String?) -> Unit
) {
    val context = LocalContext.current
    val recorderHelper = remember { AudioRecorderHelper(context) }
    
    var isRecordingActive by remember { mutableStateOf(false) }
    var isPlaybackActive by remember { mutableStateOf(audioPlayer.isPlaying()) }
    
    // Track recording duration
    var secondsElapsed by remember { mutableStateOf(0) }
    
    LaunchedEffect(isRecordingActive) {
        if (isRecordingActive) {
            secondsElapsed = 0
            while (isRecordingActive) {
                kotlinx.coroutines.delay(1000)
                secondsElapsed++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Microphone Dictaphone",
                tint = if (isRecordingActive) Color.Red else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .border(
                        width = if (isRecordingActive) 3.dp else 1.dp,
                        color = if (isRecordingActive) Color.Red else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(8.dp)
            )
            
            Spacers(12)
            
            Text(
                text = if (isRecordingActive) "RECORDING ACTIVE: $secondsElapsed seconds" else "NexNote Dictaphone",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRecordingActive) Color.Red else MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Fast offline mic record capture. Sound files store directly under devices directories.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacers(32)
            
            // Core trigger actions button rows
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isRecordingActive) {
                    Button(
                        onClick = {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                val file = recorderHelper.startRecording()
                                if (file != null) {
                                    isRecordingActive = true
                                } else {
                                    Toast.makeText(context, "Mic initiation failed!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Mic permission is required to dictation record! Please permit RECORD_AUDIO.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Start Record")
                    }
                } else {
                    Button(
                        onClick = {
                            val path = recorderHelper.stopRecording()
                            isRecordingActive = false
                            if (path != null) {
                                onAudioAttached(path)
                                Toast.makeText(context, "Voice note captured offline successfully!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Stop / Match Save")
                    }
                }

                if (currentAudioPath != null) {
                    Button(
                        onClick = {
                            if (isPlaybackActive) {
                                audioPlayer.stopAudio()
                                isPlaybackActive = false
                            } else {
                                audioPlayer.playAudio(currentAudioPath) {
                                    isPlaybackActive = false
                                }
                                isPlaybackActive = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(if (isPlaybackActive) "Stop Playback" else "Play Recording", color = MaterialTheme.colorScheme.onTertiary)
                    }
                }
            }
            
            if (currentAudioPath != null) {
                Spacers(24)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, tint = Color(0xFF4CAF50))
                        Column {
                            Text("Attached voice_note_offline.mp4", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            Text("Path: .../files/${File(currentAudioPath).name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        IconButton(onClick = {
                            audioPlayer.stopAudio()
                            onAudioAttached(null)
                            isPlaybackActive = false
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

// --- TAB SUB-VIEW: OFFLINE PHOTOS ATTACHMENTS VIEW ---
@Composable
fun AttachmentsView(
    photoUriStr: String?,
    onAttachPhoto: () -> Unit,
    onClearPhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (photoUriStr != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Try to load photo locally or display standard attachments details card
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacers(8)
                        Text("Photo attachment successfully linked", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(photoUriStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacers(16)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAttachPhoto, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                Text("Replace")
                            }
                            Button(onClick = onClearPhoto, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Photos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(96.dp)
                )
                Spacers(12)
                Text(
                    text = "No media photos linked",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                Text(
                    text = "Attach picture logs or screenshots of your offline notebooks to organize metadata in a single, robust frame.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacers(24)
                Button(
                    onClick = onAttachPhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Select Local Picture Image File")
                }
            }
        }
    }
}

// --- OPTION WORKSPACE SETTINGS SECTION SHEET ---
@Composable
fun SettingsScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    val allNotesList by viewModel.allNotes.collectAsState()
    val allNotebooksList by viewModel.notebooks.collectAsState()
    val isPrivateLockActive by viewModel.isLockedNotebookUnlocked.collectAsState()
    val checkPasscodeActive by viewModel.passcode.collectAsState()

    var customPinInput by remember { mutableStateOf("") }
    var customPinHintInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("NexNote Settings Management", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Private lockers configurations
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔒 Private Notebook Encryption PIN", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Lock NexNote with a 4-digit device credentials PIN to hide and protect secrets and diaries offline.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacers(14)
                    
                    if (checkPasscodeActive != null) {
                        Text("🔒 SECURITY VAULT IS CURRENTLY ACTIVE", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacers(12)
                        Button(
                            onClick = {
                                viewModel.removePasscode()
                                Toast.makeText(context, "Vault PIN security removed!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Disable PIN Lock Secure")
                        }
                    } else {
                        OutlinedTextField(
                            value = customPinInput,
                            onValueChange = { if (it.length <= 4) customPinInput = it },
                            placeholder = { Text("Enter 4-Digit digit passcode e.g. 1989") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("pin_set_input")
                        )
                        Spacers(8)
                        OutlinedTextField(
                            value = customPinHintInput,
                            onValueChange = { customPinHintInput = it },
                            placeholder = { Text("Enter optional hint for emergency recoveries") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("pin_hint_input")
                        )
                        Spacers(12)
                        Button(
                            onClick = {
                                if (customPinInput.length == 4) {
                                    viewModel.setupPasscode(customPinInput, customPinHintInput)
                                    customPinInput = ""
                                    customPinHintInput = ""
                                    Toast.makeText(context, "Local secret PIN vault configured!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.testTag("apply_pin_btn")
                        ) {
                            Text("Deploy Security Lock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 2: Local backup & recovery restore formats
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔄 Offline Backup & Recover Restore", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Zero clouds, zero telemetry analytics. Instantly backup and restore the notebook database via raw JSON locally.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacers(16)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val backupFile = File(downloads, "NexNote_local_backup.json")
                                try {
                                    val serializedNotes = allNotesList.map {
                                        mapOf(
                                            "title" to it.title,
                                            "content" to it.content,
                                            "tags" to it.tags,
                                            "checklistJson" to it.checklistJson,
                                            "noteType" to it.noteType
                                        )
                                    }
                                    val backupJson = com.squareup.moshi.Moshi.Builder()
                                        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                        .build()
                                        .adapter(Any::class.java)
                                        .toJson(mapOf("notes" to serializedNotes))
                                    
                                    FileOutputStream(backupFile).use { out ->
                                        out.write(backupJson.toByteArray())
                                    }
                                    
                                    Toast.makeText(context, "Backup successfully written to Downloads/NexNote_local_backup.json!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.weight(1f).testTag("backup_btn")
                        ) {
                            Text("Export Backup JSON")
                        }

                        Button(
                            onClick = {
                                // Simple restore logic finding the backup file in downloads
                                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val backupFile = File(downloads, "NexNote_local_backup.json")
                                if (backupFile.exists()) {
                                    try {
                                        val backupJson = backupFile.readText()
                                        val data = com.squareup.moshi.Moshi.Builder()
                                            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                            .build()
                                            .adapter(Map::class.java)
                                            .fromJson(backupJson)
                                        
                                        val notesList = data?.get("notes") as? List<Map<String, Any>>
                                        if (notesList != null) {
                                            for (raw in notesList) {
                                                val n = Note(
                                                    title = raw["title"] as? String ?: "",
                                                    content = raw["content"] as? String ?: "",
                                                    tags = raw["tags"] as? String ?: "",
                                                    checklistJson = raw["checklistJson"] as? String ?: "[]",
                                                    noteType = raw["noteType"] as? String ?: "TEXT"
                                                )
                                                viewModel.insertNote(n)
                                            }
                                            Toast.makeText(context, "Successfully restored ${notesList.size} notebook items!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Restore error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Could not find 'NexNote_local_backup.json' in Downloads directory!", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                            modifier = Modifier.weight(1f).testTag("restore_btn")
                        ) {
                            Text("Import Backup JSON")
                        }
                    }
                }
            }

            // Section 3: Developer & Publishers Credits (NexVora Labs)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.LocalActivity, contentDescription = "Credits", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacers(8)
                    Text("Developed with Passion", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("NexNote is an offline-first productivity workspace crafted under beautiful Material 3 guidelines.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacers(14)
                    
                    Text("Developer: Prince AR Abdur Rahman", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Publisher: NexVora Lab's Ofc", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("© 2026 NexVora Lab's Ofc. All Rights Reserved.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    
                    Spacers(16)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1BNn32qoJo/"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Text("Facebook developer")
                        }
                    }
                }
            }
        }
    }
}

// Compact helper dividers
@Composable
fun Spacers(dp: Int) {
    Spacer(modifier = Modifier.height(dp.dp))
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}
