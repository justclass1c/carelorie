package com.xxx.carelorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val date: String, // format: YYYY-MM-DD
    val weight: Float,
    val isSynced: Boolean = false
)
