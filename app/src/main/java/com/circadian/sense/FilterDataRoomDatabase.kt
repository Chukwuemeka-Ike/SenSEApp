package com.circadian.sense

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Annotates class to be a Room Database with a table (entity) of the FilterData class
@Database(entities = [FilterData::class], version = 1, exportSchema = false)
public abstract class FilterDataRoomDatabase : RoomDatabase() {
    abstract fun filterDataDao(): FilterDataDao

    private class FilterDataDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // If the database doesn't exist, populate it with some fake numbers
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.filterDataDao())
                }
            }
        }

//        suspend fun loadDatabase(filt)

        suspend fun populateDatabase(filterDataDao: FilterDataDao) {
            // Delete all content in the database
            filterDataDao.deleteAll()

            // Add sample data
            filterDataDao.insert(FilterData(0f, 65f, 70.1f))
            filterDataDao.insert(FilterData(0.1f, 64.9f, 68.02f))
            filterDataDao.insert(FilterData(0.2f, 62.3f, 66.70f))
            filterDataDao.insert(FilterData(0.3f, 59f, 59.70f))
            filterDataDao.insert(FilterData(0.4f, 64f, 64.0f))
            filterDataDao.insert(FilterData(0.5f, 73f, 72.3f))
            filterDataDao.insert(FilterData(0.6f, 72f, 72.3f))
            filterDataDao.insert(FilterData(0.7f, 71f, 72.3f))
            filterDataDao.insert(FilterData(0.8f, 70f, 72.3f))
            filterDataDao.insert(FilterData(0.9f, 69f, 72.3f))
            filterDataDao.insert(FilterData(1.0f, 68f, 72.3f))
            filterDataDao.insert(FilterData(1.1f, 67f, 72.3f))
        }
    }

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: FilterDataRoomDatabase? = null

        fun getDatabase(
            context: Context,
            scope: CoroutineScope
        ): FilterDataRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FilterDataRoomDatabase::class.java,
                    "filter_data_database"
                ).addCallback(FilterDataDatabaseCallback(scope)).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }

    }
}