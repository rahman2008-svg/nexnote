package com.example.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

object RichTextParser {

    fun parseRichText(text: String): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            // Regex to match tags <b>, <i>, <u>, <h1>, <h2>, <h3>
            val regex = Regex("(<(b|i|u|h1|h2|h3)>)(.*?)(</\\2>)")
            val matches = regex.findAll(text)
            
            for (match in matches) {
                val startText = text.substring(currentIndex, match.range.first)
                append(startText)
                
                val tag = match.groupValues[1]
                val innerText = match.groupValues[3]
                
                val styleStart = this.length
                append(innerText)
                
                when (tag) {
                    "<b>" -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), styleStart, this.length)
                    "<i>" -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), styleStart, this.length)
                    "<u>" -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), styleStart, this.length)
                    "<h1>" -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp), 
                        styleStart, 
                        this.length
                    )
                    "<h2>" -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp), 
                        styleStart, 
                        this.length
                    )
                    "<h3>" -> addStyle(
                        SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), 
                        styleStart, 
                        this.length
                    )
                }
                currentIndex = match.range.last + 1
            }
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    // Helper functions to inject styling tags into note body text
    fun toggleStyleTag(currentText: String, selectionStart: Int, selectionEnd: Int, tagSymbol: String): Pair<String, Int> {
        if (selectionStart < 0 || selectionEnd < 0) return Pair(currentText + "<$tagSymbol></$tagSymbol>", currentText.length + tagSymbol.length + 2)
        
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        
        val selectedText = currentText.substring(start, end)
        val before = currentText.substring(0, start)
        val after = currentText.substring(end)
        
        val newText = "$before<$tagSymbol>$selectedText</$tagSymbol>$after"
        val newCursor = start + tagSymbol.length + 2 + selectedText.length + tagSymbol.length + 3
        
        return Pair(newText, newCursor)
    }
}
