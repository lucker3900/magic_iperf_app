package com.luckerlucky.magiciperf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val host: String = "",
    val port: String = "5201",
    val durationSeconds: String = "10",
    val bandwidthMbps: String = "",
    val protocol: Protocol = Protocol.TCP,
    val reverse: Boolean = false,
    val isRunning: Boolean = false,
    val output: String = "",
    val error: String? = null,
    val showMissingBinaryHint: Boolean = false,
    val lastSuccessTimestamp: Long? = null,
    val iperfVersion: IperfVersion = IperfVersion.IPERF3,
    val customArgs: String = "",
    val useCustomArgs: Boolean = false
)

class MagicIperfViewModel(
    private val runner: IperfRunner
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var runningJob: Job? = null

    fun updateHost(value: String) {
        _uiState.update { it.copy(host = value) }
    }

    fun updatePort(value: String) {
        _uiState.update { it.copy(port = value.filter { char -> char.isDigit() }) }
    }

    fun updateDuration(value: String) {
        _uiState.update { it.copy(durationSeconds = value.filter { char -> char.isDigit() }) }
    }

    fun updateBandwidth(value: String) {
        _uiState.update { it.copy(bandwidthMbps = value.filter { char -> char.isDigit() }) }
    }

    fun updateProtocol(protocol: Protocol) {
        _uiState.update { it.copy(protocol = protocol) }
    }

    fun toggleReverse(value: Boolean) {
        _uiState.update { it.copy(reverse = value) }
    }

    fun updateIperfVersion(version: IperfVersion) {
        _uiState.update { it.copy(iperfVersion = version) }
    }

    fun updateCustomArgs(value: String) {
        _uiState.update { it.copy(customArgs = value) }
    }

    fun toggleCustomArgs(value: Boolean) {
        _uiState.update { it.copy(useCustomArgs = value) }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = "", error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun stopTest() {
        runningJob?.cancel()
        runningJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun runTest() {
        val current = _uiState.value

        if (current.customArgs.isNotBlank()) {
            _uiState.update {
                it.copy(isRunning = true, error = null, showMissingBinaryHint = false, output = "")
            }
            runningJob = viewModelScope.launch {
                try {
                    val flow = runner.runCustomCommandStream(current.customArgs, current.iperfVersion)
                    collectStream(flow)
                } catch (e: MissingBinaryException) {
                    _uiState.update {
                        it.copy(isRunning = false, error = e.message, showMissingBinaryHint = true)
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isRunning = false, error = e.message ?: "测试失败")
                    }
                }
            }
        } else {
            val port = current.port.toIntOrNull()
            val duration = current.durationSeconds.toIntOrNull()
            val bandwidth = current.bandwidthMbps.takeIf { it.isNotBlank() }?.toIntOrNull()

            if (current.host.isBlank()) {
                _uiState.update { it.copy(error = "请输入服务器地址") }
                return
            }
            if (port == null || port <= 0) {
                _uiState.update { it.copy(error = "端口格式不正确") }
                return
            }
            if (duration == null || duration <= 0) {
                _uiState.update { it.copy(error = "持续时间格式不正确") }
                return
            }

            _uiState.update {
                it.copy(isRunning = true, error = null, showMissingBinaryHint = false, output = "")
            }

            val params = IperfParams(
                serverHost = current.host,
                port = port,
                durationSeconds = duration,
                protocol = current.protocol,
                bandwidthMbps = bandwidth,
                reverse = current.reverse
            )

            runningJob = viewModelScope.launch {
                try {
                    val flow = runner.runTestStream(params, current.iperfVersion)
                    collectStream(flow)
                } catch (e: MissingBinaryException) {
                    _uiState.update {
                        it.copy(isRunning = false, error = e.message, showMissingBinaryHint = true)
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isRunning = false, error = e.message ?: "测试失败")
                    }
                }
            }
        }
    }

    private suspend fun collectStream(flow: kotlinx.coroutines.flow.Flow<String>) {
        val sb = StringBuilder()
        flow.collect { line ->
            sb.appendLine(line)
            _uiState.update { it.copy(output = sb.toString()) }
        }
        _uiState.update {
            it.copy(isRunning = false, lastSuccessTimestamp = System.currentTimeMillis())
        }
    }

    companion object {
        fun Factory(runner: IperfRunner): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MagicIperfViewModel(runner)
            }
        }
    }
}
