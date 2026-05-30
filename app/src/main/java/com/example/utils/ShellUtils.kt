package com.example.utils

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object ShellUtils {
    private const val TAG = "QuantumShell"

    /**
     * Checks if Root (superuser) is available on the device.
     */
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/sbin/su",
            "/usr/bin/su",
            "/vendor/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec("which su")
            val reader = process.inputStream.bufferedReader()
            val line = reader.readLine()
            line != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a command using sh or su.
     */
    fun runCommand(useRoot: Boolean, command: String): ShellResult {
        Log.d(TAG, "Running command (root=$useRoot): $command")
        var process: Process? = null
        var os: DataOutputStream? = null
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        try {
            process = Runtime.getRuntime().exec(if (useRoot) "su" else "sh")
            os = DataOutputStream(process.outputStream)
            
            // Execute command then exit shell
            os.write(command.toByteArray(Charsets.UTF_8))
            os.writeBytes("\n")
            os.writeBytes("exit\n")
            os.flush()

            val outThread = Thread {
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdoutBuilder.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stdout reader failed", e)
                }
            }

            val errThread = Thread {
                try {
                    process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stderrBuilder.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stderr reader failed", e)
                }
            }

            outThread.start()
            errThread.start()

            val exitCode = process.waitFor()
            outThread.join(1500)
            errThread.join(1500)

            return ShellResult(
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trim(),
                stderr = stderrBuilder.toString().trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            return ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Unknown Shell Exception"
            )
        } finally {
            try { os?.close() } catch (ignored: Exception) {}
            try { process?.destroy() } catch (ignored: Exception) {}
        }
    }
}
