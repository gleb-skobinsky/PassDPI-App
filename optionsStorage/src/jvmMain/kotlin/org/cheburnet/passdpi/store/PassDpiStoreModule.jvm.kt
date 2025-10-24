package org.cheburnet.passdpi.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import org.cheburnet.passdpi.store.PassDpiOptionsStorageImpl.Companion.STORE_FILE_NAME
import org.koin.core.scope.Scope
import java.io.File

actual fun Scope.getDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val file = File(System.getProperty("java.io.tmpdir"), STORE_FILE_NAME)
            file.absolutePath.toPath()
        }
    )
}