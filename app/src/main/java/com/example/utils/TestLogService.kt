package com.example.utils

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.TestLog
import com.example.data.TestLogRepository

class TestLogService(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val repository = TestLogRepository(database.testLogDao())

    val allLogs = repository.allLogs

    suspend fun insert(log: TestLog) {
        repository.insert(log)
    }

    suspend fun update(log: TestLog) {
        repository.update(log)
    }

    suspend fun deleteById(id: Int) {
        repository.deleteById(id)
    }
}
