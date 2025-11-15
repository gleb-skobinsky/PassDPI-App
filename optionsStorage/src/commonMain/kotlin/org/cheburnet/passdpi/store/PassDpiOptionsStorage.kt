package org.cheburnet.passdpi.store

import kotlinx.coroutines.flow.Flow

interface PassDpiOptionsStorage {

    fun observePort(): Flow<Int>

    suspend fun setPort(new: Int)

    fun observeDnsIp(): Flow<String>

    suspend fun setDnsIp(newDns: String)

    suspend fun setEnableIpV6(isEnabled: Boolean)

    fun observeIsIpV6Enabled(): Flow<Boolean>

    suspend fun getVpnOptions(): PassDpiVPNOptions

    suspend fun saveEditableSettings(
        settings: EditableSettings,
    )

    fun observeEditableSettings(): Flow<EditableSettings>

    suspend fun getCommandLineArgs(): String

    suspend fun getEditableSettings(): EditableSettings
}