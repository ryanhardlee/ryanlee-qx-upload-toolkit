package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TestLogDao {
    @Query("SELECT * FROM test_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<TestLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TestLog): Long

    @Update
    suspend fun updateLog(log: TestLog)

    @Query("DELETE FROM test_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}
