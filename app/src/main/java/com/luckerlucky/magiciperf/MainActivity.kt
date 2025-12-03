package com.luckerlucky.magiciperf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
                    onClearOutput = viewModel::clearOutput,
                    onDismissError = viewModel::clearError
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
    onClearOutput: () -> Unit,
    onDismissError: () -> Unit
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
                onClearOutput = onClearOutput
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
    onClearOutput: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "一个轻量级的 iPerf3 客户端，可快速验证 TCP/UDP 吞吐。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            label = { Text("服务器地址") },
            placeholder = { Text("例如：iperf.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isRunning
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.port,
                onValueChange = onPortChange,
                label = { Text("端口") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.isRunning
            )
            OutlinedTextField(
                value = state.durationSeconds,
                onValueChange = onDurationChange,
                label = { Text("持续时间 (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.isRunning
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ProtocolChip(
                selected = state.protocol == Protocol.TCP,
                label = "TCP",
                onClick = { onProtocolChange(Protocol.TCP) }
            )
            ProtocolChip(
                selected = state.protocol == Protocol.UDP,
                label = "UDP",
                onClick = { onProtocolChange(Protocol.UDP) }
            )
        }

        OutlinedTextField(
            value = state.bandwidthMbps,
            onValueChange = onBandwidthChange,
            label = { Text("带宽 (Mbps，UDP 可选)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isRunning
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
                enabled = !state.isRunning
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClearOutput, enabled = !state.isRunning) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
                Button(onClick = onStart, enabled = !state.isRunning) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (state.isRunning) "运行中..." else "开始测试")
                }
            }
        }

        if (state.showMissingBinaryHint) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.material3.MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "缺少 iperf3 可执行文件",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "请将针对设备架构编译的 iperf3 放到 assets/iperf3，重新安装后再试。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

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
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.lastSuccessTimestamp?.let {
                        Text(
                            text = "上次成功: ${android.text.format.DateFormat.format("MM-dd HH:mm", it)}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.output.ifBlank { "等待执行 iperf3..." },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun ProtocolChip(
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
