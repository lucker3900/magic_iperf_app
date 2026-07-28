package com.luckerlucky.magiciperf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which input method is active — mirrors the iOS tab split: custom command
 *  is the default and sits on the left; the two are mutually exclusive. */
enum class InputMode { CUSTOM, PARAMS }

data class UiState(
    val inputMode: InputMode = InputMode.CUSTOM,
    val host: String = "",
    val port: String = "",
    val durationSeconds: String = "",
    val bandwidthMbps: String = "",
    val protocol: Protocol = Protocol.TCP,
    val reverse: Boolean = false,
    val role: IperfRole = IperfRole.CLIENT,
    val iperfVersion: IperfVersion = IperfVersion.V3,
    val customArgs: String = "",
    val commandHistory: List<String> = emptyList(),
    val wifiIP: String? = null,
    val hotspotIP: String? = null,
    val isRunning: Boolean = false,
    val output: String = "",
    val error: String? = null,
    val showMissingBinaryHint: Boolean = false,
    val lastSuccessTimestamp: Long? = null
) {
    val customMode: Boolean get() = inputMode == InputMode.CUSTOM
    val serverMode: Boolean get() = role == IperfRole.SERVER
    val defaultPort: Int get() = iperfVersion.defaultPort
}

class MagicIperfViewModel(
    private val appContext: Context,
    private val runner: IperfRunner
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UiState(
            commandHistory = loadHistory(),
            wifiIP = NetworkInfo.wifiIPv4(),
            hotspotIP = NetworkInfo.hotspotIPv4()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Bumped per run so a late streaming update can't overwrite the result. */
    private var runToken = 0
    private var stopRequested = false
    /** Note lines ([port switched] / [peer command]) kept ahead of streamed text. */
    private var runPrefix = ""

    private val prefs get() = appContext.getSharedPreferences("magiciperf", Context.MODE_PRIVATE)

    fun updateInputMode(mode: InputMode) = _uiState.update { it.copy(inputMode = mode) }
    fun updateHost(value: String) = _uiState.update { it.copy(host = value) }
    fun updatePort(value: String) = _uiState.update { it.copy(port = value.filter(Char::isDigit)) }
    fun updateDuration(value: String) = _uiState.update { it.copy(durationSeconds = value.filter(Char::isDigit)) }
    fun updateBandwidth(value: String) = _uiState.update { it.copy(bandwidthMbps = value.filter(Char::isDigit)) }
    fun updateCustomArgs(value: String) = _uiState.update { it.copy(customArgs = value) }
    fun updateIperfVersion(version: IperfVersion) = _uiState.update { it.copy(iperfVersion = version) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearOutput() = _uiState.update { it.copy(output = "", error = null) }

    fun updateProtocol(protocol: Protocol) = _uiState.update {
        val clearBandwidth = protocol == Protocol.TCP || it.serverMode
        it.copy(protocol = protocol, bandwidthMbps = if (clearBandwidth) "" else it.bandwidthMbps)
    }

    fun updateRole(role: IperfRole) = _uiState.update {
        if (role == IperfRole.SERVER) it.copy(role = role, reverse = false, bandwidthMbps = "")
        else it.copy(role = role)
    }

    fun toggleReverse() = _uiState.update { it.copy(reverse = !it.reverse) }

    fun refreshNetworkInfo() = _uiState.update {
        it.copy(wifiIP = NetworkInfo.wifiIPv4(), hotspotIP = NetworkInfo.hotspotIPv4())
    }

    // ---- command history (tap-to-fill, persisted, deduplicated) ----

    private fun loadHistory(): List<String> =
        prefs.getString("command_history", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun recordHistory(command: String) {
        val history = (_uiState.value.commandHistory.toMutableList()).apply {
            removeAll { it == command }
            add(0, command)
            while (size > HISTORY_LIMIT) removeAt(size - 1)
        }
        _uiState.update { it.copy(commandHistory = history) }
        prefs.edit().putString("command_history", history.joinToString("\n")).apply()
    }

    fun deleteHistory(command: String) {
        val history = _uiState.value.commandHistory.filter { it != command }
        _uiState.update { it.copy(commandHistory = history) }
        prefs.edit().putString("command_history", history.joinToString("\n")).apply()
    }

    fun fillFromHistory(command: String) = _uiState.update { it.copy(customArgs = command) }

    // ---- pre-run analysis (mirrors the iOS ViewModel) ----

    private fun targetHost(customList: List<String>?): String? {
        val state = _uiState.value
        if (customList != null) {
            customList.forEachIndexed { i, arg ->
                if ((arg == "-c" || arg == "--client") && i + 1 < customList.size) return customList[i + 1]
                if (arg.startsWith("--client=")) return arg.removePrefix("--client=")
            }
            return null
        }
        val trimmed = state.host.trim()
        return if (state.role == IperfRole.CLIENT && trimmed.isNotEmpty()) trimmed else null
    }

    private fun selfTargetError(customList: List<String>?): String? {
        val target = targetHost(customList) ?: return null
        val lower = target.lowercase()
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1") {
            return appContext.getString(R.string.self_target_localhost, target)
        }
        if (NetworkInfo.ownIPv4Addresses().contains(target)) {
            return appContext.getString(R.string.self_target_own, target)
        }
        return null
    }

    private fun effectivePort(customList: List<String>?): Int? {
        val state = _uiState.value
        if (customList != null) {
            customList.forEachIndexed { i, arg ->
                if ((arg == "-p" || arg == "--port" || arg == "--listenport") && i + 1 < customList.size) {
                    return customList[i + 1].toIntOrNull()
                }
                if (arg.startsWith("--port=")) return arg.removePrefix("--port=").toIntOrNull()
                if (arg.startsWith("-p") && arg.length > 2 && arg.drop(2).all(Char::isDigit)) {
                    return arg.drop(2).toIntOrNull()
                }
            }
            return state.defaultPort
        }
        return state.port.toIntOrNull() ?: state.defaultPort
    }

    private fun nextFreePort(after: Int, udp: Boolean): Int? {
        if (after >= 65535) return null
        for (candidate in (after + 1)..minOf(after + 20, 65535)) {
            val taken = if (udp) PortProbe.udpPortInUse(candidate) else PortProbe.tcpPortInUse(candidate)
            if (!taken) return candidate
        }
        return null
    }

    fun stopTest() {
        if (!_uiState.value.isRunning || stopRequested) return
        stopRequested = true
        _uiState.update { it.copy(output = it.output + "\n" + appContext.getString(R.string.stopping) + "\n") }
        runner.requestStop()
    }

    fun runTest() {
        val state = _uiState.value
        refreshNetworkInfo()

        val customList: List<String>?
        if (state.customMode) {
            val trimmed = state.customArgs.trim()
            if (trimmed.isEmpty()) {
                _uiState.update { it.copy(error = appContext.getString(R.string.err_enter_custom)) }
                return
            }
            val tokens = ArgumentTokenizer.tokenize(trimmed)
            if (tokens.isNullOrEmpty()) {
                _uiState.update { it.copy(error = appContext.getString(R.string.err_bad_custom)) }
                return
            }
            customList = tokens
            recordHistory(trimmed)
        } else {
            customList = null
            if (state.role == IperfRole.CLIENT && state.host.isBlank()) {
                _uiState.update { it.copy(error = appContext.getString(R.string.err_enter_host)) }
                return
            }
            if (state.port.isNotEmpty() && state.port.toIntOrNull() !in 1..65535) {
                _uiState.update { it.copy(error = appContext.getString(R.string.err_port_range)) }
                return
            }
            if (state.role == IperfRole.CLIENT && state.durationSeconds.isNotEmpty() &&
                state.durationSeconds.toIntOrNull() !in 1..86400
            ) {
                _uiState.update { it.copy(error = appContext.getString(R.string.err_duration_range)) }
                return
            }
            if (state.role == IperfRole.CLIENT && state.protocol == Protocol.UDP &&
                state.bandwidthMbps.isNotEmpty() && state.bandwidthMbps.toIntOrNull() !in 1..100_000
            ) {
                _uiState.update { it.copy(error = appContext.getString(R.string.err_bandwidth_range)) }
                return
            }
        }

        selfTargetError(customList)?.let { message ->
            _uiState.update { it.copy(error = message) }
            return
        }

        // Server-run port precheck + seamless sidestep, mirroring iOS: in
        // parameter mode a taken port switches to the next free one; custom
        // commands are never rewritten — the refusal names a free port.
        var portNote = ""
        val serverShape = customList?.let { it.contains("-s") || it.contains("--server") }
            ?: (state.role == IperfRole.SERVER)
        if (serverShape) {
            val p = effectivePort(customList)
            if (p != null) {
                val udpListener = state.iperfVersion == IperfVersion.V2 &&
                    (customList?.let { it.contains("-u") || it.contains("--udp") }
                        ?: (state.protocol == Protocol.UDP))
                val taken = if (udpListener) PortProbe.udpPortInUse(p) else PortProbe.tcpPortInUse(p)
                if (taken) {
                    val free = nextFreePort(p, udpListener)
                    when {
                        customList == null && free != null -> {
                            _uiState.update { it.copy(port = free.toString()) }
                            portNote += appContext.getString(
                                R.string.port_switched_note, p.toString(), free.toString()
                            ) + "\n"
                        }
                        free != null -> {
                            _uiState.update {
                                it.copy(error = appContext.getString(
                                    R.string.port_in_use_custom, p.toString(), free.toString()
                                ))
                            }
                            return
                        }
                        else -> {
                            _uiState.update {
                                it.copy(error = appContext.getString(
                                    R.string.port_in_use_fallback, p.toString()
                                ))
                            }
                            return
                        }
                    }
                }
            }

            // Copy-ready peer command: engine binary, this phone's address,
            // the port actually in use, protocol flags.
            val current = _uiState.value
            val ip = NetworkInfo.preferredPeerTargetIP() ?: "<phone-IP>"
            val actualPort = effectivePort(customList) ?: current.defaultPort
            val engine = if (current.iperfVersion == IperfVersion.V2) "iperf" else "iperf3"
            val udpIntent = customList?.let { it.contains("-u") || it.contains("--udp") }
                ?: (current.protocol == Protocol.UDP)
            var peerCommand = "$engine -c $ip -p $actualPort"
            if (udpIntent) peerCommand += " -u -b 100M"
            peerCommand += " -i 1 -t 10"
            portNote += appContext.getString(R.string.peer_command_note, peerCommand) + "\n"
        }

        val current = _uiState.value
        // Cross-default-port heuristic: warn before the user burns a run.
        val effPort = effectivePort(customList)
        if (effPort != null &&
            ((current.iperfVersion == IperfVersion.V3 && effPort == IperfVersion.V2.defaultPort) ||
                (current.iperfVersion == IperfVersion.V2 && effPort == IperfVersion.V3.defaultPort))
        ) {
            val other = if (current.iperfVersion == IperfVersion.V3) "iperf2" else "iperf3"
            portNote += appContext.getString(
                R.string.port_default_warning,
                effPort.toString(), other, current.iperfVersion.displayName
            ) + "\n"
        }

        runPrefix = portNote
        stopRequested = false
        val startLog = portNote +
            appContext.getString(R.string.testing, current.iperfVersion.displayName) + "\n"
        // Reset the log on every run so re-testing gives immediate feedback.
        _uiState.update {
            it.copy(isRunning = true, error = null, showMissingBinaryHint = false, output = startLog)
        }

        val params = IperfParams(
            serverHost = current.host.trim().ifBlank { "custom" },
            port = current.port.toIntOrNull() ?: current.defaultPort,
            durationSeconds = current.durationSeconds.toIntOrNull() ?: DEFAULT_DURATION_SECONDS,
            protocol = current.protocol,
            bandwidthMbps = current.bandwidthMbps.toIntOrNull()
                .takeIf { current.role == IperfRole.CLIENT && current.protocol == Protocol.UDP },
            reverse = current.role == IperfRole.CLIENT && current.reverse,
            iperfVersion = current.iperfVersion,
            role = current.role,
            customArgs = customList
        )

        runToken += 1
        val token = runToken
        viewModelScope.launch {
            val result = runner.runTest(params) { text ->
                // Live per-second output; the prefix note lines stay on top.
                if (runToken == token) {
                    _uiState.update { it.copy(output = runPrefix + text) }
                }
            }
            runToken += 1  // invalidate any in-flight streaming update
            if (stopRequested) {
                // User stopped: show whatever was captured, no error.
                val captured = result.getOrNull()?.rawOutput
                    ?: (result.exceptionOrNull()?.message ?: "")
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        output = runPrefix + captured + "\n" + appContext.getString(R.string.stopped)
                    )
                }
            } else {
                result.fold(
                    onSuccess = { iperfResult ->
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                output = runPrefix + iperfResult.rawOutput,
                                lastSuccessTimestamp = iperfResult.timestamp
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                error = throwable.message ?: appContext.getString(R.string.test_failed),
                                output = runPrefix + (throwable.message ?: ""),
                                showMissingBinaryHint = throwable is MissingBinaryException
                            )
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val DEFAULT_DURATION_SECONDS = 10
        private const val HISTORY_LIMIT = 10

        fun Factory(appContext: Context, runner: IperfRunner): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MagicIperfViewModel(appContext, runner) }
            }
    }
}
