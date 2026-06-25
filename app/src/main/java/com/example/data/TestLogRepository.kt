package com.example.data

import kotlinx.coroutines.flow.Flow

class TestLogRepository(private val testLogDao: TestLogDao) {
    val allLogs: Flow<List<TestLog>> = testLogDao.getAllLogs()

    suspend fun insert(log: TestLog): Long = testLogDao.insertLog(log)

    suspend fun update(log: TestLog) = testLogDao.updateLog(log)

    suspend fun deleteById(id: Int) = testLogDao.deleteLogById(id)
}
