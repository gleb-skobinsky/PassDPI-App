package org.cheburnet.passdpi.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import org.cheburnet.passdpi.store.PassDpiOptionsStorageImpl.Companion.STORE_FILE_NAME
import org.koin.core.scope.Scope

actual fun Scope.getDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val context: Context = get()
            context.filesDir.resolve(STORE_FILE_NAME).absolutePath.toPath()
        }
    )
}