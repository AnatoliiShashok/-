package com.serediuk.stairs.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val type: String,
    val heightMm: Double,
    val runMm: Double,
    val treadMm: Double,
    val riserMm: Double,
    val widthMm: Double,
    val risers: Int,
    val realRiserMm: Double,
    val angleDeg: Double,
    val thicknessMm: Double = 0.0,
    val run2Mm: Double = 0.0
)
