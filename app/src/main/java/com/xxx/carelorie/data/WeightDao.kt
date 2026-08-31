package com.xxx.carelorie.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWeight(weightRecord: WeightRecord): Long

    @Query("SELECT * FROM weight_records WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun getWeightForDay(userId: String, date: String): WeightRecord?

    @Query("SELECT * FROM weight_records WHERE userId = :userId ORDER BY date ASC")
    suspend fun getAllWeightRecords(userId: String): List<WeightRecord>

    @Query("SELECT * FROM weight_records WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WeightRecord>

    @Query("UPDATE weight_records SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("DELETE FROM weight_records WHERE userId = :userId")
    suspend fun deleteWeightRecords(userId: String)
}
