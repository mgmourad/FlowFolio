package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(settings, onBack, viewModel::setStorageMode, viewModel::setAskEachImport, viewModel::setTheme)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: UserSettings,
    onBack: () -> Unit,
    onStorageMode: (StorageMode) -> Unit,
    onAskEachImport: (Boolean) -> Unit,
    onTheme: (ThemePreference) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.settings_import_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            StorageOptions(settings.storageMode, onStorageMode)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_ask_each), modifier = Modifier.weight(1f))
                Switch(checked = settings.askStorageEachImport, onCheckedChange = onAskEachImport)
            }
            Text(
                stringResource(R.string.settings_theme_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            listOf(
                ThemePreference.SYSTEM to R.string.theme_system,
                ThemePreference.LIGHT to R.string.theme_light,
                ThemePreference.DARK to R.string.theme_dark
            ).forEach { (theme, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(settings.theme == theme, role = Role.RadioButton) {
                        onTheme(theme)
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.theme == theme, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
