package com.circadian.sense

import android.app.Application
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.UserDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MainApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { FilterDataRoomDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { FilterDataRepository(database.filterDataDao()) }

//    val dataManager by lazy {AuthStateManager.getInstance(this)}
//    val dataManager = AuthStateManager.getInstance(this)

}