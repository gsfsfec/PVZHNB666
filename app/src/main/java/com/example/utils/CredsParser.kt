package com.example.utils

import android.util.Log
import java.io.File

data class Credentials(
    val token: String,
    val refreshToken: String,
    val expiresAt: Long,
    val personaId: String
)

object CredsParser {
    private const val TAG = "CredsParser"
    
    // Constant credentials configs
    const val CLIENT_ID = "ea-pvzheroes-production"
    const val CLIENT_SECRET = "78a158d0-9f4b-4c8a-901e-2b3c4d5e6f7a"
    const val AUTH_URL = "https://eadp.ea.com/accounts/api/v1/anonymous/login"
    
    val TOKEN_FILE_PATH = "/data/data/com.ea.gp.pvzheroes/files/Nimble/live/persistence/[COMPONENT]com.ea.nimble.cpp.nexusservice.dat"

    /**
     * Replicates the bash logic:
     * TEMP_TEXT=$(strings "$TOKEN_FILE")
     * token=$(echo "$TEMP_TEXT" | grep -o '"access_token":"[^"]*"' | head -1 | sed 's/"access_token":"//g' | tr -d '"')
     * etc.
     */
    fun loadCredentialsFromDevice(useRoot: Boolean, simulatedData: String? = null): Credentials? {
        val rawSource: String
        if (simulatedData != null) {
            rawSource = simulatedData
        } else {
            // Check if file is readable or request root copy to standard temp dir
            val file = File(TOKEN_FILE_PATH)
            if (!file.exists() && !useRoot) {
                Log.d(TAG, "File does not exist and root is disabled")
                return null
            }
            
            rawSource = if (file.canRead()) {
                file.readText(Charsets.ISO_8859_1) // dat files usually binary, use single byte encoding to simulate strings
            } else if (useRoot) {
                // Read via cat with root permissions
                val result = ShellUtils.runCommand(useRoot = true, command = "cat \"$TOKEN_FILE_PATH\"")
                if (result.isSuccess) {
                    result.stdout
                } else {
                    Log.e(TAG, "Root cat failed: ${result.stderr}")
                    return null
                }
            } else {
                return null
            }
        }

        // Clean binary data - replicate bash strings utility (keep printable ASCII characters)
        val cleanedText = cleanToPrintableAscii(rawSource)

        // Regex parsing exactly like grep -o
        val tokenRegex = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val refreshRegex = "\"refresh_token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val expiresRegex = "\"accessTokenExpiresAt\"\\s*:\\s*([0-9]+)".toRegex()
        val personaRegex = "\"personaId\"\\s*:\\s*([0-9]+)".toRegex()

        val tokenMatch = tokenRegex.find(cleanedText)
        val refreshMatch = refreshRegex.find(cleanedText)
        val expiresMatch = expiresRegex.find(cleanedText)
        val personaMatch = personaRegex.find(cleanedText)

        val token = tokenMatch?.groupValues?.get(1) ?: ""
        val refreshToken = refreshMatch?.groupValues?.get(1) ?: ""
        val expiresAt = expiresMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val personaId = personaMatch?.groupValues?.get(1) ?: ""

        if (token.isEmpty() || refreshToken.isEmpty() || personaId.isEmpty()) {
            Log.w(TAG, "Credentials parsing returned incompleted results: tokenLen=${token.length}, refreshLen=${refreshToken.length}, expiresAt=$expiresAt, personaId=$personaId")
            return null
        }

        return Credentials(token, refreshToken, expiresAt, personaId)
    }

    private fun cleanToPrintableAscii(input: String): String {
        val sb = StringBuilder()
        var runLength = 0
        val currentRun = StringBuilder()

        for (char in input) {
            if (char.code in 32..126 || char == '\n' || char == '\r' || char == '\t') {
                currentRun.append(char)
                runLength++
            } else {
                if (runLength >= 4) { // Replicate "strings" command behavior (minimum 4 printable characters)
                    sb.append(currentRun)
                }
                currentRun.setLength(0)
                runLength = 0
            }
        }
        if (runLength >= 4) {
            sb.append(currentRun)
        }
        return sb.toString()
    }
}
