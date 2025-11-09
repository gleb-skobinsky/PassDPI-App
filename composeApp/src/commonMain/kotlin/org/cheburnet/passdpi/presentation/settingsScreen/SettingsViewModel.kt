package org.cheburnet.passdpi.presentation.settingsScreen

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cheburnet.passdpi.store.EditableSettings
import org.cheburnet.passdpi.store.PassDpiOptionsStorage

@Stable
class SettingsViewModel(
    private val optionsStorage: PassDpiOptionsStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsScreenState.Initial)
    val state = _state.asStateFlow()

    init {
        subscribeToSettings()
    }

    fun updateCommandLineArgs(newValue: String) {
        _state.update { it.copy(commandLineArgs = newValue) }
    }

    private var saveSettingsJob: Job? = null
    fun saveSettings() {
        if (saveSettingsJob?.isActive == true) return
        viewModelScope.launch {
            val currentState = _state.value
            optionsStorage.saveEditableSettings(currentState.toStorageSettings())
        }
    }

    private fun subscribeToSettings() {
        optionsStorage.observeEditableSettings().onEach { settings ->
            _state.value = settings.toState()
        }.launchIn(viewModelScope)
    }

    private fun EditableSettings.toState() = SettingsScreenState(
        commandLineArgs = commandLineArgs
    )

    private fun SettingsScreenState.toStorageSettings() = EditableSettings(
        commandLineArgs = commandLineArgs
    )
}