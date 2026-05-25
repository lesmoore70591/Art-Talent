package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contentType: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
