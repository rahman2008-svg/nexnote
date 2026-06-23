package com.example.util

import com.example.data.model.ChecklistItem
import com.example.data.model.DrawingStroke
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object JsonUtils {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun serializeChecklist(list: List<ChecklistItem>): String {
        val type = Types.newParameterizedType(List::class.java, ChecklistItem::class.java)
        val adapter = moshi.adapter<List<ChecklistItem>>(type)
        return adapter.toJson(list)
    }

    fun deserializeChecklist(json: String?): List<ChecklistItem> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, ChecklistItem::class.java)
            val adapter = moshi.adapter<List<ChecklistItem>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeStrokes(list: List<DrawingStroke>): String {
        val type = Types.newParameterizedType(List::class.java, DrawingStroke::class.java)
        val adapter = moshi.adapter<List<DrawingStroke>>(type)
        return adapter.toJson(list)
    }

    fun deserializeStrokes(json: String?): List<DrawingStroke> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, DrawingStroke::class.java)
            val adapter = moshi.adapter<List<DrawingStroke>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
