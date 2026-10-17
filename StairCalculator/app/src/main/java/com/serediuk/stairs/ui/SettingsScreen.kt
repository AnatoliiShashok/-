@file:OptIn(ExperimentalMaterial3Api::class)

package com.serediuk.stairs.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serediuk.stairs.R
import com.serediuk.stairs.calc.LengthUnit

@Composable
fun SettingsScreen(vm: StairsViewModel, onLanguageChanged: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SectionCard(stringResource(R.string.set_language)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("uk" to "Українська", "es" to "Español").forEach { (code, name) ->
                    FilterChip(
                        selected = language == code,
                        onClick = {
                            if (language != code) {
                                vm.setLanguage(code)
                                onLanguageChanged()
                            }
                        },
                        label = { Text(name) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.set_theme)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to R.string.theme_system,
                    "light" to R.string.theme_light,
                    "dark" to R.string.theme_dark
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = theme == code,
                        onClick = { vm.setTheme(code) },
                        label = { Text(stringResource(label), maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.set_units)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LengthUnit.entries.forEach { u ->
                    FilterChip(
                        selected = input.unit == u,
                        onClick = { vm.setUnit(u) },
                        label = { Text(u.symbol) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.set_history)) {
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_clear), color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.set_about),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.author_line, AppInfo.AUTHOR),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.version_line, AppInfo.VERSION),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.clear_title)) },
            text = { Text(stringResource(R.string.clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirm = false
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
