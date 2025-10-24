package org.cheburnet.passdpi.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.core.scope.Scope
import org.koin.dsl.module

val PassDpiStoreModule = module {
    single<PassDpiOptionsStorage> {
        PassDpiOptionsStorageImpl(getDataStore())
    }
}

expect fun Scope.getDataStore(): DataStore<Preferences>