package com.circadian.sense

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

class FilterDataRepository (private val filterDataDao: FilterDataDao) {

    // Observed Flow will notify the observer when the data has changed
    val allData: Flow<List<FilterData>> = filterDataDao.getData()

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(filterData: FilterData){
        filterDataDao.insert(filterData)
    }

}