package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_logs")
data class TestLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceFileName: String,
    val patchMode: String,
    val fpsMode: String,
    val exportResult: String,
    val userNotes: String = "",
    val qualityResult: String = "" // e.g. "Good", "Excellent", "Compressed", "Flop"
)
