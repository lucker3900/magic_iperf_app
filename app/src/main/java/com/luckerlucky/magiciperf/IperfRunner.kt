package com.luckerlucky.magiciperf

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class Protocol { TCP, UDP }
enum class IperfVersion { IPERF3, IPERF2 }

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
    private val context: Context
) {

    private fun nativeLibNameFor(version: IperfVersion): String = when (version) {
        IperfVersion.IPERF3 -> "libiperf3.so"
        IperfVersion.IPERF2 -> "libiperf2.so"
    }

    private fun labelFor(version: IperfVersion): String = when (version) {
        IperfVersion.IPERF3 -> "iperf3"
        IperfVersion.IPERF2 -> "iperf2"
    }

    private fun findBinary(version: IperfVersion): File {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val binary = File(nativeLibDir, nativeLibNameFor(version))
        if (!binary.exists() || !binary.canExecute()) {
            val label = labelFor(version)
            throw MissingBinaryException(
                "缺少 $label 可执行文件\n请将针对设备架构编译的 $label 放到 jniLibs/arm64-v8a/${nativeLibNameFor(version)}，重新安装后再试。"
            )
        }
        return binary
    }

    fun runTestStream(params: IperfParams, version: IperfVersion = IperfVersion.IPERF3): Flow<String> {
        val binary = findBinary(version)
        val command = buildList {
            add(binary.absolutePath)
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
        val label = labelFor(version)
        Timber.d("Running $label with command: ${command.joinToString(" ")}")
        return executeCommandStream(command, label)
    }

    fun runCustomCommandStream(rawArgs: String, version: IperfVersion): Flow<String> {
        val binary = findBinary(version)
        val command = buildList {
            add(binary.absolutePath)
            addAll(rawArgs.trim().split("\\s+".toRegex()))
        }
        val label = labelFor(version)
        Timber.d("Running $label custom command: ${command.joinToString(" ")}")
        return executeCommandStream(command, label)
    }

    private fun executeCommandStream(command: List<String>, label: String): Flow<String> = flow {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
                if (!currentCoroutineContext().isActive) {
                    process.destroy()
                    break
                }
            }

            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroy()
                throw IllegalStateException("$label 执行超时")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0 && currentCoroutineContext().isActive) {
                // Don't throw for non-zero exit if we already emitted output
            }
        } catch (e: Exception) {
            process.destroy()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
