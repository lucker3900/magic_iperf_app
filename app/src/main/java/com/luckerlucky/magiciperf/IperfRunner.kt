package com.luckerlucky.magiciperf

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class Protocol { TCP, UDP }

data class IperfParams(
    val serverHost: String,
    val port: Int = 5201,
    val durationSeconds: Int = 10,
    val protocol: Protocol = Protocol.TCP,
    val bandwidthMbps: Int? = null,
    val reverse: Boolean = false
)

data class IperfResult(
    val rawOutput: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MissingBinaryException(message: String) : Exception(message)

class IperfRunner(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val binaryName = "iperf3"
    private val binaryFile: File
        get() = File(context.filesDir, binaryName)

    /**
     * Ensure iperf3 binary is available and executable.
     * If assets do not contain the binary, propagate a typed error for UI hinting.
     */
    private suspend fun ensureBinaryReady(): Result<Unit> = withContext(dispatcher) {
        if (binaryFile.exists() && binaryFile.canExecute()) {
            return@withContext Result.success(Unit)
        }

        return@withContext runCatching {
            context.assets.open(binaryName).use { input ->
                binaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            binaryFile.setExecutable(true)
            binaryFile.setReadable(true, false)
        }.recoverCatching { throwable ->
            throw MissingBinaryException("未找到 iperf3 可执行文件，请先放入 assets/iperf3 并重新安装应用。原始错误: ${throwable.message}")
        }
    }

    suspend fun runTest(params: IperfParams): Result<IperfResult> = withContext(dispatcher) {
        ensureBinaryReady().getOrElse { return@withContext Result.failure(it) }

        val command = buildList {
            add(binaryFile.absolutePath)
            add("-c")
            add(params.serverHost)
            add("-p")
            add(params.port.toString())
            add("-t")
            add(params.durationSeconds.toString())
            if (params.reverse) add("-R")
            if (params.protocol == Protocol.UDP) {
                add("-u")
                params.bandwidthMbps?.let {
                    add("-b")
                    add("${it}M")
                }
            }
        }

        Timber.d("Running iperf3 with command: ${command.joinToString(" ")}")

        return@withContext runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Read output while process runs to avoid buffer fill-up
            val outputBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    outputBuilder.appendLine(line)
                    if (!isActive) {
                        process.destroy()
                        return@forEach
                    }
                }
            }

            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroy()
                throw IllegalStateException("iperf3 执行超时")
            }

            val exitCode = process.exitValue()
            val finalOutput = outputBuilder.toString()

            if (exitCode != 0) {
                throw IllegalStateException("iperf3 退出码 $exitCode: $finalOutput")
            }

            IperfResult(
                rawOutput = finalOutput.trim().ifEmpty { "iperf3 没有返回输出" }
            )
        }
    }
}
