package org.cheburnet.passdpi.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

internal class PassDpiOptionsStorageImpl(
    private val dataStore: DataStore<Preferences>,
) : PassDpiOptionsStorage {
    private val optsPortKey = intPreferencesKey(OPTS_PORT_KEY)
    private val optsDnsIpKey = stringPreferencesKey(OPTS_DNS_IP_KEY)
    private val ipv6EnabledKey = booleanPreferencesKey(OPTS_IPV6_ENABLED_KEY)
    private val optsProxyIpKey = stringPreferencesKey(OPTS_PROXY_IP_KEY)

    private val commandLineArgsKey = stringPreferencesKey(COMMAND_LINE_ARGS_KEY)

    override fun observePort(): Flow<Int> {
        return dataStore.data.map { it.getPort() }.distinctUntilChanged()
    }

    override suspend fun setPort(new: Int) {
        dataStore.edit { it[optsPortKey] = new }
    }

    override fun observeDnsIp(): Flow<String> {
        return dataStore.data.map { it.dnsIp() }.distinctUntilChanged()
    }

    override suspend fun setDnsIp(newDns: String) {
        dataStore.edit { it[optsDnsIpKey] = newDns }
    }

    override suspend fun setEnableIpV6(isEnabled: Boolean) {
        dataStore.edit { it[ipv6EnabledKey] = isEnabled }
    }

    override fun observeIsIpV6Enabled(): Flow<Boolean> {
        return dataStore.data.map { it.ipv6Enabled() }.distinctUntilChanged()
    }

    override suspend fun getVpnOptions(): PassDpiVPNOptions {
        val data = dataStore.data.firstOrNull()
        return PassDpiVPNOptions(
            port = data.getPort(),
            dnsIp = data.dnsIp(),
            enableIpV6 = data.ipv6Enabled()
        )
    }

    override suspend fun saveEditableSettings(settings: EditableSettings) {
        dataStore.edit {
            it[commandLineArgsKey] = settings.commandLineArgs
        }
    }

    override fun observeEditableSettings(): Flow<EditableSettings> {
        return dataStore.data.map {
            EditableSettings(
                commandLineArgs = it.commandLineArgs(),
                proxyIp = it.proxyIp()
            )
        }.distinctUntilChanged()
    }

    override suspend fun getCommandLineArgs(): String {
        return dataStore.data.firstOrNull().commandLineArgs()
    }

    override suspend fun getEditableSettings(): EditableSettings {
        val data = dataStore.data.firstOrNull()
        return EditableSettings(
            commandLineArgs = data.commandLineArgs(),
            proxyIp = data.proxyIp()
        )
    }

    private fun Preferences?.getPort(): Int {
        return this?.get(optsPortKey) ?: DEFAULT_PORT
    }

    private fun Preferences?.dnsIp(): String {
        return this?.get(optsDnsIpKey) ?: DEFAULT_DNS_IP
    }

    private fun Preferences?.ipv6Enabled(): Boolean {
        return this?.get(ipv6EnabledKey) ?: true
    }

    private fun Preferences?.commandLineArgs(): String {
        return this?.get(commandLineArgsKey).orEmpty()
    }

    private fun Preferences?.proxyIp(): String {
        return this?.get(optsProxyIpKey) ?: DEFAULT_PROXY_IP
    }

    companion object {
        private const val OPTS_PORT_KEY = "OPTS_PORT"
        private const val OPTS_DNS_IP_KEY = "OPTS_DNS_IP"
        private const val OPTS_PROXY_IP_KEY = "OPTS_PROXY_IP"
        private const val OPTS_IPV6_ENABLED_KEY = "OPTS_IPV6_ENABLED"

        private const val COMMAND_LINE_ARGS_KEY = "COMMAND_LINE_ARGS"
        internal const val STORE_FILE_NAME = "passdpi.preferences_pb"
    }
}