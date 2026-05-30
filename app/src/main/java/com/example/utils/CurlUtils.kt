package com.example.utils

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

data class CurlResult(
    val code: Int,
    val body: String
)

object CurlUtils {
    private const val TAG = "CurlUtils"

    /**
     * Executes an HTTP/HTTPS request using a multi-stage robust transmission design:
     * Layer 1: Raw TCP/SSLSocket - Direct TCP handshake bypassing system proxies/interceptors entirely.
     * Layer 2: Native URLConnection with NO_PROXY bypass.
     * Layer 3: Shell-level & Root-level (su) curl command line runner.
     */
    fun execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): CurlResult {
        // --- Layer 1: Raw Socket Bypass Engine (Highly immune to packet capture/sandbox intercepts) ---
        try {
            Log.d(TAG, "Pipeline L1 Initiating: Raw SSLSocket Direct TCP Transceiver")
            return executeRawSocket(url, method, headers, body)
        } catch (e: Exception) {
            Log.w(TAG, "Pipeline L1 SSLSocket failed: ${e.message}. Pivoting to Layer 2 standard HTTP stack...")
        }

        // --- Layer 2: Native Connection Engine (Configured to ignore WiFi HTTP Proxies) ---
        var connection: HttpURLConnection? = null
        try {
            Log.d(TAG, "Pipeline L2 Initiating: Native HttpURLConnection [No Proxy]")
            val connUrl = URL(url)
            connection = try {
                connUrl.openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
            } catch (e: Exception) {
                connUrl.openConnection() as HttpURLConnection
            }
            
            connection.connectTimeout = 15000
            connection.readTimeout = 45000
            connection.requestMethod = method
            connection.instanceFollowRedirects = true
            
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            connection.setRequestProperty("Connection", "keep-alive")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")
            
            headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            
            if (!body.isNullOrEmpty() && (method == "POST" || method == "PUT")) {
                connection.doOutput = true
                val os = connection.outputStream
                val writer = OutputStreamWriter(os, Charsets.UTF_8)
                writer.write(body)
                writer.flush()
                writer.close()
            }
            
            val responseCode = connection.responseCode
            val isSuccess = responseCode in 200..299
            
            val inputStream: InputStream = if (isSuccess) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            
            val finalBody = output.toString().trim()
            Log.d(TAG, "Pipeline L2 Successful, Code: $responseCode")
            return CurlResult(responseCode, finalBody)
            
        } catch (e: Exception) {
            Log.w(TAG, "Pipeline L2 Standard HTTP Client failed: ${e.message}. Pivoting to Layer 3 Terminal Fallback...")
            return executeCurlShellFallback(url, method, headers, body)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Executes Raw Socket direct requests. Highly resilient, bypasses platform VPN/Capture layers.
     */
    private fun executeRawSocket(
        urlStr: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): CurlResult {
        val url = URL(urlStr)
        val host = url.host
        val isHttps = url.protocol.lowercase(java.util.Locale.ROOT) == "https"
        val port = if (url.port != -1) url.port else (if (isHttps) 443 else 80)
        val path = if (url.path.isEmpty()) "/" else url.path + if (url.query != null) "?" + url.query else ""
        
        var tcpSocket: java.net.Socket? = null
        try {
            tcpSocket = if (isHttps) {
                val factory = SSLSocketFactory.getDefault()
                factory.createSocket(host, port)
            } else {
                java.net.Socket(host, port)
            }
            tcpSocket.soTimeout = 15000
            
            val outWriter = BufferedWriter(OutputStreamWriter(tcpSocket.outputStream, "UTF-8"))
            outWriter.write("$method $path HTTP/1.1\r\n")
            outWriter.write("Host: $host\r\n")
            outWriter.write("User-Agent: Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36\r\n")
            outWriter.write("Accept: application/json, text/plain, */*\r\n")
            outWriter.write("Connection: close\r\n")
            
            headers.forEach { (k, v) ->
                outWriter.write("$k: $v\r\n")
            }
            
            if (!body.isNullOrEmpty() && (method == "POST" || method == "PUT")) {
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                outWriter.write("Content-Length: ${bodyBytes.size}\r\n")
                outWriter.write("\r\n")
                outWriter.flush()
                tcpSocket.outputStream.write(bodyBytes)
                tcpSocket.outputStream.flush()
            } else {
                outWriter.write("\r\n")
                outWriter.flush()
            }
            
            val byteInputStream = tcpSocket.inputStream
            val lineReader = BufferedReader(InputStreamReader(byteInputStream, "UTF-8"))
            
            val statusLine = lineReader.readLine()
            var responseCode = 200
            if (statusLine != null && statusLine.startsWith("HTTP/1.")) {
                val parts = statusLine.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    responseCode = parts[1].toIntOrNull() ?: 200
                }
            }
            
            var isHeaderPart = true
            var contentLength = -1
            var isChunked = false
            
            while (isHeaderPart) {
                val hLine = lineReader.readLine() ?: break
                if (hLine.trim().isEmpty()) {
                    isHeaderPart = false
                    break
                }
                val hLower = hLine.lowercase(java.util.Locale.ROOT)
                if (hLower.startsWith("content-length:")) {
                    contentLength = hLine.substring(15).trim().toIntOrNull() ?: -1
                } else if (hLower.startsWith("transfer-encoding:") && hLower.contains("chunked")) {
                    isChunked = true
                }
            }
            
            val responseBodyBuilder = java.lang.StringBuilder()
            
            if (isChunked) {
                while (true) {
                    val chunkHeader = lineReader.readLine()?.trim() ?: break
                    if (chunkHeader.isEmpty()) continue
                    val chunkSize = chunkHeader.split(";")[0].toIntOrNull(16) ?: 0
                    if (chunkSize <= 0) {
                        break
                    }
                    val buffer = CharArray(chunkSize)
                    var readSum = 0
                    while (readSum < chunkSize) {
                        val limit = chunkSize - readSum
                        val rLen = lineReader.read(buffer, readSum, limit)
                        if (rLen == -1) break
                        readSum += rLen
                    }
                    responseBodyBuilder.append(buffer, 0, readSum)
                    lineReader.readLine() // Consume trailing CRLF
                }
            } else if (contentLength >= 0) {
                val buf = CharArray(2048)
                var readSum = 0
                while (readSum < contentLength) {
                    val limit = Math.min(buf.size, contentLength - readSum)
                    val rLen = lineReader.read(buf, 0, limit)
                    if (rLen == -1) break
                    responseBodyBuilder.append(buf, 0, rLen)
                    readSum += rLen
                }
            } else {
                val buf = CharArray(2048)
                var rLen: Int
                while (lineReader.read(buf).also { rLen = it } != -1) {
                    responseBodyBuilder.append(buf, 0, rLen)
                }
            }
            
            val parsedBody = responseBodyBuilder.toString().trim()
            Log.d(TAG, "Pipeline L1 Raw Socket Completed, Code: $responseCode")
            return CurlResult(responseCode, parsedBody)
            
        } catch (e: Exception) {
            throw e
        } finally {
            try { tcpSocket?.close() } catch (ex: java.lang.Exception) {}
        }
    }

    /**
     * Executes curl commands command-line style or inside direct su shells as fallback.
     */
    private fun executeCurlShellFallback(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): CurlResult {
        // Construct standard arguments list
        val command = mutableListOf<String>()
        command.add("curl")
        command.add("-s")
        command.add("-i")
        command.add("--connect-timeout")
        command.add("15")
        command.add("-m")
        command.add("45")
        command.add("-L")
        command.add("-X")
        command.add(method)
        command.add("-H")
        command.add("User-Agent: Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        
        headers.forEach { (name, value) ->
            command.add("-H")
            command.add("$name: $value")
        }
        
        if (!body.isNullOrEmpty()) {
            command.add("-d")
            command.add(body)
        }
        command.add(url)

        // Try standard process execution first
        try {
            Log.d(TAG, "Executing Shell Backup Command: ${command.joinToString(" ")}")
            val process = ProcessBuilder(command).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            if (exitCode == 0 || output.isNotEmpty()) {
                return parseRawCurlOutput(output.toString(), exitCode)
            }
        } catch (e: Exception) {
            Log.w(TAG, "User-level direct process curl failed. Pivot to ROOT execution path...", e)
        }

        // If direct execution fails, execute in root shell ("su") to bypass SELinux sandboxes
        try {
            Log.d(TAG, "Executing Root Shell su Bypass Command")
            val process = ProcessBuilder("su").start()
            val suWriter = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
            
            // Format each argument into single shell line safely
            val shellCommand = command.joinToString(" ") { valText ->
                "'" + valText.replace("'", "'\\''") + "'"
            }
            
            suWriter.write("$shellCommand\n")
            suWriter.write("exit\n")
            suWriter.flush()
            suWriter.close()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            val exitCode = process.waitFor()
            if (output.isNotEmpty()) {
                return parseRawCurlOutput(output.toString(), exitCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root su transmission also failed", e)
        }

        return CurlResult(500, "Error: All network channels (RawSocket, URLConnection, Shell/Root execution) failed entirely.")
    }

    private fun parseRawCurlOutput(rawStr: String, exitCode: Int): CurlResult {
        var statusCode = 0
        val bodyBuilder = java.lang.StringBuilder()
        var isHeader = true
        
        val rawLines = rawStr.lines()
        for (i in rawLines.indices) {
            val currentLine = rawLines[i]
            if (isHeader) {
                if (currentLine.trim().isEmpty()) {
                    var nextLine = ""
                    for (j in (i + 1) until rawLines.size) {
                        if (rawLines[j].trim().isNotEmpty()) {
                            nextLine = rawLines[j]
                            break
                        }
                    }
                    if (!nextLine.trim().startsWith("HTTP/")) {
                        isHeader = false
                    }
                } else if (currentLine.startsWith("HTTP/")) {
                    val parts = currentLine.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val codeRaw = parts[1]
                        statusCode = codeRaw.toIntOrNull() ?: 0
                    }
                }
            } else {
                bodyBuilder.append(currentLine).append("\n")
            }
        }
        
        val responseBody = bodyBuilder.toString().trim()
        if (statusCode == 0) {
            statusCode = if (exitCode == 0) 200 else 500
        }
        return CurlResult(statusCode, responseBody)
    }
    
    /**
     * Curl/Stream-based download tool for local file updates
     */
    fun downloadFile(url: String, outFile: File): Boolean {
        // L1: Standard stream direct connection download
        try {
            Log.d(TAG, "Native connection file download: $url")
            val conn = URL(url).openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (outFile.exists() && outFile.length() > 0) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard download client failed, pivoting to curl Fallback...", e)
        }

        // L2: Shell & root su download
        try {
            val command = listOf(
                "curl", "-s", "-L",
                "--connect-timeout", "15",
                "-o", outFile.absolutePath,
                url
            )
            val process = ProcessBuilder(command).start()
            val exitCode = process.waitFor()
            if (exitCode == 0 && outFile.exists() && outFile.length() > 0) {
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct shell curl download failed, pivoting to su context...", e)
        }

        try {
            val process = java.lang.ProcessBuilder("su").start()
            val wr = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
            wr.write("curl -s -L --connect-timeout 15 -o '${outFile.absolutePath}' '$url'\n")
            wr.write("exit\n")
            wr.flush()
            wr.close()
            process.waitFor()
            return outFile.exists() && outFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "All download channels failed", e)
            return false
        }
    }
}
