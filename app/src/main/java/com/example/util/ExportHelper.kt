package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.model.Note
import java.io.File
import java.io.FileOutputStream

object ExportHelper {

    fun exportToTxt(context: Context, note: Note): File? {
        return try {
            val cleanTitle = note.title.ifBlank { "Untitled_Note" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val file = File(context.cacheDir, "$cleanTitle.txt")
            FileOutputStream(file).use { out ->
                val contentString = buildString {
                    appendLine("NEXNOTE - OFFLINE SMART NOTEBOOK")
                    appendLine("=====================================")
                    appendLine("Title: ${note.title}")
                    appendLine("Type: ${note.noteType}")
                    appendLine("Tags: ${note.tags}")
                    appendLine("Last Modified: ${java.text.DateFormat.getDateTimeInstance().format(note.lastModified)}")
                    appendLine("=====================================")
                    appendLine()
                    appendLine(note.content)
                }
                out.write(contentString.toByteArray())
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToPdf(context: Context, note: Note): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595 x 842 points)
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(33, 150, 243) // Modern NexNote blue
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
                isAntiAlias = true
            }

            val labelPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                isAntiAlias = true
            }

            var yPosition = 50f
            canvas.drawText("NexNote Smart Notebook File", 50f, yPosition, titlePaint)
            yPosition += 30f

            canvas.drawText("Title: ${note.title.ifBlank { "Untitled" }}", 50f, yPosition, labelPaint)
            yPosition += 20f
            
            val dateStr = java.text.DateFormat.getDateTimeInstance().format(note.lastModified)
            canvas.drawText("Tags: ${note.tags.ifBlank { "None" }}  |  Modified: $dateStr", 50f, yPosition, metaPaint)
            yPosition += 20f

            canvas.drawLine(50f, yPosition, 545f, yPosition, Paint().apply { color = Color.LTGRAY; strokeWidth = 1.5f })
            yPosition += 25f

            // Handle multiline wrapping
            val lines = note.content.split("\n")
            val maxTextWidth = 495f // 595 - 2x 50dp margins

            for (rawLine in lines) {
                var line = rawLine
                if (line.isEmpty()) {
                    yPosition += 15f
                    continue
                }

                // Check page height limit
                if (yPosition > 800f) {
                    break
                }

                // Wrap words to avoid clipping
                while (line.isNotEmpty()) {
                    val count = textPaint.breakText(line, true, maxTextWidth, null)
                    val substring = line.substring(0, count)
                    canvas.drawText(substring, 50f, yPosition, textPaint)
                    yPosition += 18f
                    line = line.substring(count)
                    if (yPosition > 820f) break
                }
            }

            // Draw a neat footer credit
            val footerPaint = Paint().apply {
                color = Color.GRAY
                textSize = 9f
                isAntiAlias = true
            }
            canvas.drawText("Created via NexNote Notebook app. Developed by Prince AR Abdur Rahman.", 50f, 810f, footerPaint)

            pdfDocument.finishPage(page)

            val cleanTitle = note.title.ifBlank { "Untitled_Note" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val file = File(context.cacheDir, "$cleanTitle.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
