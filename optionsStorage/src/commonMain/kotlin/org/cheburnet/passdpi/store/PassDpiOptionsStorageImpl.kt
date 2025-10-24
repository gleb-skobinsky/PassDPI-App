package org.cheburnet.passdpi.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

internal class PassDpiOptionsStorageImpl(
    private val dataStore: DataStore<Preferences>,
) : PassDpiOptionsStorage {
    private val optsPortKey = intPreferencesKey(OPTS_PORT_KEY)

    override suspend fun getPort(): Int {
        return dataStore.data.firstOrNull().getPort()
    }

    override fun observePort(): Flow<Int> {
        return dataStore.data.map {
            it.getPort()
        }
    }

    override suspend fun setPort(new: Int) {
        dataStore.edit {
            it[optsPortKey] = new
        }
    }

    private fun Preferences?.getPort(): Int {
        return this?.get(optsPortKey) ?: DEFAULT_PORT
    }

    companion object {
        private const val OPTS_PORT_KEY = "OPTS_PORT"
        internal const val STORE_FILE_NAME = "passdpi.preferences_pb"
    }
}