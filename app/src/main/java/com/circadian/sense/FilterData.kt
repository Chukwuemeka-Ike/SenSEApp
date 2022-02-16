package com.circadian.sense

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FilterData")
class FilterData(@PrimaryKey val t: Float, val y: Float, val yHat: Float)
//@ColumnInfo(name="t") val t: Float