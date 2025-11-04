package org.cheburnet.passdpi.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.cheburnet.passdpi.store.PassDpiOptionsStorageImpl.Companion.STORE_FILE_NAME
import org.koin.core.scope.Scope
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun Scope.getDataStore(): DataStore<Preferences> = getDataStoreApple()

@OptIn(ExperimentalForeignApi::class)
fun getDataStoreApple(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            (requireNotNull(documentDirectory).path + "/$STORE_FILE_NAME").toPath()
        }
    )
}

fun PassDpiOptionsStorage(): PassDpiOptionsStorage {
    return PassDpiOptionsStorageImpl(getDataStoreApple())
}