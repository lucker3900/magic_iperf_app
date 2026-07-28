package com.luckerlucky.magiciperf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val runner by lazy { IperfRunner(applicationContext) }
    private val viewModel: MagicIperfViewModel by viewModels {
        MagicIperfViewModel.Factory(applicationContext, runner)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        setContent {
            com.luckerlucky.magiciperf.ui.theme.MagicIperfTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MagicIperfApp(state = uiState, viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetworkInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicIperfApp(state: UiState, viewModel: MagicIperfViewModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MagicIperfContent(state = state, viewModel = viewModel)
        }
        state.error?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MagicIperfContent(state: UiState, viewModel: MagicIperfViewModel) {
    val scrollState = rememberScrollState()
    var commandFieldFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium)

        // Own-address header (tap to refresh) — mirrors the iOS header rows.
        Column(modifier = Modifier.clickable { viewModel.refreshNetworkInfo() }) {
            Text(
                text = state.wifiIP?.let { stringResource(R.string.wifi_ip, it) }
                    ?: stringResource(R.string.not_connected_wifi),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.wifiIP != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary
            )
            state.hotspotIP?.let {
                Text(
                    text = stringResource(R.string.hotspot_ip, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Input-method tabs: custom command on the left and default.
        TabRow(selectedTabIndex = if (state.customMode) 0 else 1) {
            Tab(
                selected = state.customMode,
                onClick = { if (!state.isRunning) viewModel.updateInputMode(InputMode.CUSTOM) },
                text = { Text(stringResource(R.string.tab_custom)) }
            )
            Tab(
                selected = !state.customMode,
                onClick = { if (!state.isRunning) viewModel.updateInputMode(InputMode.PARAMS) },
                text = { Text(stringResource(R.string.tab_params)) }
            )
        }

        // Engine — applies to both modes.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.iperfVersion == IperfVersion.V3,
                enabled = !state.isRunning,
                onClick = { viewModel.updateIperfVersion(IperfVersion.V3) },
                label = { Text("iperf3") }
            )
            FilterChip(
                selected = state.iperfVersion == IperfVersion.V2,
                enabled = !state.isRunning,
                onClick = { viewModel.updateIperfVersion(IperfVersion.V2) },
                label = { Text("iperf2") }
            )
        }

        if (state.customMode) {
            OutlinedTextField(
                value = state.customArgs,
                onValueChange = viewModel::updateCustomArgs,
                label = { Text(stringResource(R.string.tab_custom)) },
                placeholder = { Text(stringResource(R.string.custom_command_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { commandFieldFocused = it.isFocused },
                singleLine = true,
                enabled = !state.isRunning
            )
            if (commandFieldFocused && state.commandHistory.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.recent_commands),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        state.commandHistory.forEach { command ->
                            Text(
                                text = command,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { viewModel.fillFromHistory(command) },
                                        onLongClick = { viewModel.deleteHistory(command) }
                                    )
                                    .padding(vertical = 8.dp)
                            )
                            if (command != state.commandHistory.last()) HorizontalDivider()
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.custom_command_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                IperfRole.entries.forEach { role ->
                    FilterChip(
                        selected = state.role == role,
                        enabled = !state.isRunning,
                        onClick = { viewModel.updateRole(role) },
                        label = { Text(role.label) }
                    )
                }
            }

            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::updateHost,
                label = { Text(stringResource(R.string.server_address)) },
                placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isRunning && !state.serverMode
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::updatePort,
                    label = { Text(stringResource(R.string.port_label)) },
                    placeholder = { Text(state.defaultPort.toString()) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isRunning
                )
                OutlinedTextField(
                    value = state.durationSeconds,
                    onValueChange = viewModel::updateDuration,
                    label = { Text(stringResource(R.string.duration_label)) },
                    placeholder = { Text(MagicIperfViewModel.DEFAULT_DURATION_SECONDS.toString()) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isRunning && !state.serverMode
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Protocol.entries.forEach { protocol ->
                    FilterChip(
                        selected = state.protocol == protocol,
                        enabled = !state.isRunning,
                        onClick = { viewModel.updateProtocol(protocol) },
                        label = { Text(protocol.name) }
                    )
                }
            }

            OutlinedTextField(
                value = state.bandwidthMbps,
                onValueChange = viewModel::updateBandwidth,
                label = { Text(stringResource(R.string.bandwidth_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isRunning && !state.serverMode && state.protocol == Protocol.UDP
            )

            if (!state.serverMode) {
                FilterChip(
                    selected = state.reverse,
                    enabled = !state.isRunning,
                    onClick = viewModel::toggleReverse,
                    label = { Text(stringResource(R.string.reverse_label)) }
                )
            }
        }

        // Action row: clear + start / red stop.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = viewModel::clearOutput, enabled = !state.isRunning) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.clear))
            }
            if (state.isRunning) {
                Button(
                    onClick = viewModel::stopTest,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.stop))
                }
            } else {
                Button(onClick = viewModel::runTest) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.start_test))
                }
            }
        }

        if (state.showMissingBinaryHint) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.missing_binary_title, state.iperfVersion.displayName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.missing_binary_body,
                            state.iperfVersion.displayName, state.iperfVersion.binaryName
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        OutputCard(state)
    }
}

@Composable
private fun OutputCard(state: UiState) {
    val outputScroll = rememberScrollState()
    LaunchedEffect(state.output) {
        outputScroll.scrollTo(outputScroll.maxValue)
    }

    Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.output_log),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                state.lastSuccessTimestamp?.let {
                    Text(
                        text = stringResource(
                            R.string.last_success,
                            android.text.format.DateFormat.format("MM-dd HH:mm", it).toString()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.output.ifBlank {
                    stringResource(R.string.waiting_to_run, state.iperfVersion.displayName)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(outputScroll)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}
