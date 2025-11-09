package org.cheburnet.passdpi.presentation.settingsScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.cheburnet.passdpi.navigation.LocalNavigator
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigator.navigateUp()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.saveSettings()
                },
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Text("Save settings")
            }
        }
    ) { paddings ->
        Column(
            Modifier
                .padding(paddings)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.commandLineArgs,
                onValueChange = {
                    viewModel.updateCommandLineArgs(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        }
    }
}