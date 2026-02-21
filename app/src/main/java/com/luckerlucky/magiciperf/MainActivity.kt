package com.luckerlucky.magiciperf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckerlucky.magiciperf.BuildConfig
import com.luckerlucky.magiciperf.ui.theme.MagicIperfTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val runner by lazy { IperfRunner(applicationContext) }
    private val viewModel: MagicIperfViewModel by viewModels {
        MagicIperfViewModel.Factory(runner)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        setContent {
            MagicIperfTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MagicIperfApp(
                    state = uiState,
                    onHostChange = viewModel::updateHost,
                    onPortChange = viewModel::updatePort,
                    onDurationChange = viewModel::updateDuration,
                    onBandwidthChange = viewModel::updateBandwidth,
                    onProtocolChange = viewModel::updateProtocol,
                    onReverseToggle = viewModel::toggleReverse,
                    onStart = viewModel::runTest,
                    onStop = viewModel::stopTest,
                    onClearOutput = viewModel::clearOutput,
                    onDismissError = viewModel::clearError,
                    onIperfVersionChange = viewModel::updateIperfVersion,
                    onCustomArgsChange = viewModel::updateCustomArgs,
                    onToggleCustomArgs = viewModel::toggleCustomArgs
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicIperfApp(
    state: UiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onBandwidthChange: (String) -> Unit,
    onProtocolChange: (Protocol) -> Unit,
    onReverseToggle: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearOutput: () -> Unit,
    onDismissError: () -> Unit,
    onIperfVersionChange: (IperfVersion) -> Unit,
    onCustomArgsChange: (String) -> Unit,
    onToggleCustomArgs: (Boolean) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Magic iPerf", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MagicIperfContent(
                state = state,
                onHostChange = onHostChange,
                onPortChange = onPortChange,
                onDurationChange = onDurationChange,
                onBandwidthChange = onBandwidthChange,
                onProtocolChange = onProtocolChange,
                onReverseToggle = onReverseToggle,
                onStart = onStart,
                onStop = onStop,
                onClearOutput = onClearOutput,
                onIperfVersionChange = onIperfVersionChange,
                onCustomArgsChange = onCustomArgsChange,
                onToggleCustomArgs = onToggleCustomArgs
            )
        }
    }
}

@Composable
private fun MagicIperfContent(
    state: UiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onBandwidthChange: (String) -> Unit,
    onProtocolChange: (Protocol) -> Unit,
    onReverseToggle: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearOutput: () -> Unit,
    onIperfVersionChange: (IperfVersion) -> Unit,
    onCustomArgsChange: (String) -> Unit,
    onToggleCustomArgs: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // iperf version selector
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            VersionChip(
                selected = state.iperfVersion == IperfVersion.IPERF3,
                label = "iperf3",
                onClick = { onIperfVersionChange(IperfVersion.IPERF3) }
            )
            VersionChip(
                selected = state.iperfVersion == IperfVersion.IPERF2,
                label = "iperf2",
                onClick = { onIperfVersionChange(IperfVersion.IPERF2) }
            )
        }

        // Custom args input
        OutlinedTextField(
            value = state.customArgs,
            onValueChange = onCustomArgsChange,
            label = { Text("自定义参数（可选）") },
            placeholder = { Text("-c 10.0.0.5 -t 30 -i 1 -b 10M") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 2,
            enabled = !state.isRunning
        )

        if (state.customArgs.isNotBlank()) {
            Text(
                text = "已启用自定义参数，除 iperf 版本外其余选项已禁用。",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Standard options (disabled when custom args are used)
        val standardEnabled = !state.isRunning && state.customArgs.isBlank()

        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            label = { Text("服务器地址") },
            placeholder = { Text("例如：iperf.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = standardEnabled
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.port,
                onValueChange = onPortChange,
                label = { Text("端口") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = standardEnabled
            )
            OutlinedTextField(
                value = state.durationSeconds,
                onValueChange = onDurationChange,
                label = { Text("持续时间 (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = standardEnabled
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ProtocolChip(
                selected = state.protocol == Protocol.TCP,
                label = "TCP",
                onClick = { onProtocolChange(Protocol.TCP) },
                enabled = standardEnabled
            )
            ProtocolChip(
                selected = state.protocol == Protocol.UDP,
                label = "UDP",
                onClick = { onProtocolChange(Protocol.UDP) },
                enabled = standardEnabled
            )
        }

        OutlinedTextField(
            value = state.bandwidthMbps,
            onValueChange = onBandwidthChange,
            label = { Text("带宽 (Mbps，UDP 可选)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = standardEnabled
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                label = {
                    Text(if (state.reverse) "反向测试 (服务器下行)" else "正向测试 (服务器上行)")
                },
                onClick = { onReverseToggle(!state.reverse) },
                enabled = standardEnabled
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClearOutput, enabled = !state.isRunning) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
                if (state.isRunning) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止")
                    }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("开始测试")
                    }
                }
            }
        }

        if (state.showMissingBinaryHint) {
            val versionLabel = if (state.iperfVersion == IperfVersion.IPERF2) "iperf2" else "iperf3"
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "缺少 $versionLabel 可执行文件",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "请将针对设备架构编译的 $versionLabel 放到 jniLibs/arm64-v8a/lib${versionLabel}.so，重新安装后再试。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Output log card with auto-scroll
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "输出日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.lastSuccessTimestamp?.let {
                        Text(
                            text = "上次成功: ${android.text.format.DateFormat.format("MM-dd HH:mm", it)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val logScrollState = rememberScrollState()

                // Auto-scroll to bottom when output changes
                LaunchedEffect(state.output) {
                    logScrollState.animateScrollTo(logScrollState.maxValue)
                }

                Text(
                    text = state.output.ifBlank { "等待执行..." },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(logScrollState),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun RowScope.VersionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun RowScope.ProtocolChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.weight(1f),
        enabled = enabled
    )
}
