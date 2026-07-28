package com.luckerlucky.magiciperf

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class Protocol { TCP, UDP }

enum class IperfVersion(val binaryName: String, val displayName: String, val defaultPort: Int) {
    V2(binaryName = "iperf2", displayName = "iperf2", defaultPort = 5001),
    V3(binaryName = "iperf3", displayName = "iperf3", defaultPort = 5201)
}

enum class IperfRole(val label: String) {
    CLIENT("client -c"),
    SERVER("server -s")
}

data class IperfParams(
    val serverHost: String,
    val port: Int,
    val durationSeconds: Int = 10,
    val protocol: Protocol = Protocol.TCP,
    val bandwidthMbps: Int? = null,
    val reverse: Boolean = false,
    val iperfVersion: IperfVersion = IperfVersion.V3,
    val role: IperfRole = IperfRole.CLIENT,
    val customArgs: List<String>? = null
)

data class IperfResult(
    val rawOutput: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MissingBinaryException(message: String) : Exception(message)

/**
 * Runs the bundled iperf binaries as child processes, streaming output line
 * by line. Unlike the iOS app (which must embed the engines in-process),
 * a child process dies on stop and the OS reclaims its sockets — so stop is
 * simply Process.destroy(), with none of iOS's leftover-listener hazards.
 */
class IperfRunner(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    @Volatile
    private var currentProcess: Process? = null

    /** Interrupt the current run: kill the child, OS frees its sockets. */
    fun requestStop() {
        currentProcess?.destroy()
    }

    private fun binaryFile(version: IperfVersion): File =
        File(context.filesDir, version.binaryName)

    private suspend fun ensureBinaryReady(version: IperfVersion): Result<Unit> = withContext(dispatcher) {
        val targetBinary = binaryFile(version)
        if (targetBinary.exists() && targetBinary.canExecute()) {
            return@withContext Result.success(Unit)
        }

        return@withContext runCatching {
            context.assets.open(version.binaryName).use { input ->
                targetBinary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetBinary.setExecutable(true)
            targetBinary.setReadable(true, false)
            Unit
        }.recoverCatching { throwable ->
            throw MissingBinaryException(
                context.getString(
                    R.string.missing_binary_error,
                    version.displayName, version.binaryName, throwable.message ?: "?"
                )
            )
        }
    }

    suspend fun runTest(
        params: IperfParams,
        onUpdate: (String) -> Unit = {}
    ): Result<IperfResult> = withContext(dispatcher) {
        ensureBinaryReady(params.iperfVersion).getOrElse { return@withContext Result.failure(it) }

        val binaryPath = binaryFile(params.iperfVersion).absolutePath
        val command = buildList {
            add(binaryPath)
            val custom = params.customArgs
            if (custom != null) {
                addAll(custom)
            } else if (params.role == IperfRole.SERVER) {
                add("-s")
                add("-p"); add(params.port.toString())
                add("-i"); add("1")
                // An iperf2 server only accepts UDP when started with -u;
                // an iperf3 server rejects -u (the client decides).
                if (params.iperfVersion == IperfVersion.V2 && params.protocol == Protocol.UDP) add("-u")
            } else {
                add("-c"); add(params.serverHost)
                add("-p"); add(params.port.toString())
                add("-t"); add(params.durationSeconds.toString())
                add("-i"); add("1")
                if (params.reverse) add("-R")
                if (params.protocol == Protocol.UDP) {
                    add("-u")
                    params.bandwidthMbps?.let { add("-b"); add("${it}M") }
                }
            }
        }

        Timber.d("Running ${params.iperfVersion.displayName}: ${command.joinToString(" ")}")

        return@withContext runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            currentProcess = process

            // Stream accumulated output on every line so the UI shows
            // per-second intervals live (the -i 1 above feeds this).
            val outputBuilder = StringBuilder()
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.useLines { lines ->
                    lines.forEach { line ->
                        outputBuilder.appendLine(line)
                        onUpdate(outputBuilder.toString())
                    }
                }

                // Servers run until stopped; clients get their duration plus
                // generous margin before being declared hung.
                if (params.role == IperfRole.SERVER && params.customArgs == null) {
                    process.waitFor()
                } else {
                    val timeoutSeconds = maxOf(params.durationSeconds.toLong() + 60, 120L)
                    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                        process.destroy()
                        throw IllegalStateException(
                            context.getString(R.string.exec_timeout, params.iperfVersion.displayName)
                        )
                    }
                }
            } finally {
                currentProcess = null
            }

            val exitCode = process.exitValue()
            val finalOutput = outputBuilder.toString().trim()

            if (exitCode != 0) {
                var message = context.getString(
                    R.string.exit_code_prefix, params.iperfVersion.displayName, exitCode.toString()
                )
                if (finalOutput.isNotEmpty()) message += ":\n$finalOutput"
                alignmentHint(finalOutput)?.let { message += "\n$it" }
                throw IllegalStateException(message)
            }

            // Both engines can exit 0 after a failed run — scan for markers
            // so those are reported as failures instead of a bogus success.
            failureReason(finalOutput)?.let { reason ->
                var message = "${params.iperfVersion.displayName} $reason"
                if (finalOutput.isNotEmpty()) message += "\n$finalOutput"
                throw IllegalStateException(message)
            }

            IperfResult(
                rawOutput = finalOutput.ifEmpty {
                    context.getString(R.string.no_output, params.iperfVersion.displayName)
                }
            )
        }
    }

    /** Alignment hint when the failure text smells like a mismatched peer. */
    private fun alignmentHint(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("client only") -> context.getString(R.string.hint_client_only)
            lower.contains("server only") -> context.getString(R.string.hint_server_only)
            lower.contains("unable to receive control message") ||
                lower.contains("unable to receive parameters") ->
                context.getString(R.string.hint_wrong_generation)
            lower.contains("connection refused") || lower.contains("unable to connect") ||
                lower.contains("connect failed") ->
                context.getString(R.string.alignment_checklist)
            else -> null
        }
    }

    /** Known failure markers, most specific first (mirrors the iOS list). */
    private fun failureReason(output: String): String? {
        val lower = output.lowercase()
        val markers = listOf(
            "connection refused" to
                (context.getString(R.string.marker_refused) + " " + context.getString(R.string.alignment_checklist)),
            "unable to receive control message" to context.getString(R.string.marker_mismatch),
            "operation timed out" to context.getString(R.string.marker_timeout),
            "no route to host" to context.getString(R.string.marker_no_route),
            "network is unreachable" to context.getString(R.string.marker_net_unreachable),
            "host is unreachable" to context.getString(R.string.marker_host_unreachable),
            "name or service not known" to context.getString(R.string.marker_resolve),
            "nodename nor servname" to context.getString(R.string.marker_resolve),
            "the server is busy" to context.getString(R.string.marker_busy),
            "unable to connect" to context.getString(R.string.marker_unable_connect),
            "unable to receive" to context.getString(R.string.marker_unable_receive),
            "control socket has closed" to context.getString(R.string.marker_ctrl_closed),
            "broken pipe" to context.getString(R.string.marker_broken_pipe),
            "write failed" to context.getString(R.string.marker_write_failed),
            "read failed" to context.getString(R.string.marker_read_failed),
            "shutdown failed" to context.getString(R.string.marker_shutdown_failed),
            "iperf3: error" to context.getString(R.string.marker_iperf3_error),
            " failed" to context.getString(R.string.marker_generic_failed),
        )
        return markers.firstOrNull { lower.contains(it.first) }?.second
    }
}
