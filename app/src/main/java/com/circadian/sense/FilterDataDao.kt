package com.circadian.sense

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDataDao {

    @Query("SELECT * FROM FilterData ORDER BY t")
    fun getData(): Flow<List<FilterData>>

//    @Query("SELECT * FROM FilterData")
//    fun loadAll(): List<FilterData>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(filterData: FilterData) : Long

    @Query("DELETE FROM FilterData")
    suspend fun deleteAll()

}