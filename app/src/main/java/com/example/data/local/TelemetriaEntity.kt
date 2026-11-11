package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetria_lecturas")
data class TelemetriaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val temp: Float,
    val hum: Int,
    val estado: String,
    val dupla: String,
    val timestamp: Long
)
