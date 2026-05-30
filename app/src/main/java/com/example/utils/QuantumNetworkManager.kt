package com.example.utils

import android.util.Log
import com.example.model.QuantumCards
import org.json.JSONObject
import java.io.File

class QuantumNetworkManager(
    private val logCallback: (String, LogLevel) -> Unit
) {
    private fun postLog(text: String, level: LogLevel = LogLevel.RAW) {
        logCallback(text, level)
    }

    /**
     * Refreshes the Nucleus session authentication tokens using the refresh_token via Curl.
     */
    fun refreshAuthToken(
        refreshToken: String,
        onSuccess: (newToken: String, newRefresh: String, expiresInSec: Long) -> Unit,
        onFailure: () -> Unit
    ) {
        postLog("→ 正在向 EA Nucleus 授权服务器发起令牌刷新协议...", LogLevel.INFO)
        postLog("POST ${CredsParser.AUTH_URL}", LogLevel.COMMAND)

        Thread {
            val headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "X-Expand-Results" to "true",
                "X-Include-Underage" to "true"
            )
            val body = "grant_type=refresh_token" +
                    "&refresh_token=$refreshToken" +
                    "&client_id=${CredsParser.CLIENT_ID}" +
                    "&client_secret=${CredsParser.CLIENT_SECRET}" +
                    "&redirect_uri=nucleus:rest"

            val res = CurlUtils.execute(CredsParser.AUTH_URL, "POST", headers, body)
            val code = res.code
            val responseBody = res.body
            postLog("← [HTTPS_CODE $code] 接收授权响应序列", if (code == 200) LogLevel.SUCCESS else LogLevel.ERROR)

            if (code == 200) {
                try {
                    val json = JSONObject(responseBody)
                    val access = json.optString("access_token", "")
                    val refresh = json.optString("refresh_token", "")
                    val expires = json.optLong("expires_in", 0L)
                    
                    if (access.isNotEmpty() && refresh.isNotEmpty()) {
                        postLog("✔ 令牌重置并注入成功！", LogLevel.SUCCESS)
                        postLog("全新令牌: ${access.take(15)}...", LogLevel.INFO)
                        onSuccess(access, refresh, expires)
                    } else {
                        postLog("❌ 令牌解析失败 - 空字段", LogLevel.ERROR)
                        onFailure()
                    }
                } catch (e: Exception) {
                    postLog("❌ 解析 JSON 失败: ${e.message}", LogLevel.ERROR)
                    onFailure()
                }
            } else {
                postLog("❌ 续期失败：服务器返回 $code ($responseBody)", LogLevel.ERROR)
                onFailure()
            }
        }.start()
    }

    /**
     * Step 1 of Diamond Generation: Update daily progress via Curl.
     */
    fun updateDailyLogin(
        token: String,
        personaId: String,
        utcTime: Long,
        onComplete: (success: Boolean, httpCode: Int) -> Unit
    ) {
        val url = "https://pvz-heroes.awspopcap.com/updateDailyLoginProgress?userId=$personaId"
        postLog("→ 正在向超算云发起身份协议校验 [DailyProgress]...", LogLevel.INFO)
        postLog("POST $url", LogLevel.COMMAND)

        val jsonBody = JSONObject().apply {
            put("day", 1)
            put("forStreak", true)
            put("currentStreakSetCompleted", false)
        }.toString()

        val headers = getHeaders(token, personaId, utcTime)

        Thread {
            val res = CurlUtils.execute(url, "POST", headers, jsonBody)
            val code = res.code
            val body = res.body
            postLog("✔ [Identity Response] 状态码：$code", if (code == 200) LogLevel.SUCCESS else LogLevel.WARNING)
            if (code != 200) {
                postLog("服务器调试：$body", LogLevel.RAW)
            }
            onComplete(code == 200, code)
        }.start()
    }

    /**
     * Step 2 of Diamond Generation: Sync League Rewards via Curl.
     */
    fun syncLeagueRewards(
        token: String,
        personaId: String,
        utcTime: Long,
        onComplete: (success: Boolean, httpCode: Int) -> Unit
    ) {
        val url = "https://pvz-heroes.awspopcap.com/pvp/v1/leagueRewards/sync?playerId=$personaId"
        postLog("→ 钻石量子发生器注入中 [Gems +10k0]...", LogLevel.INFO)
        postLog("POST $url", LogLevel.COMMAND)

        val jsonBody = JSONObject().apply {
            put("tickets", 0)
            put("gems", 10000)
            put("sparks", 0)
            put("packs", org.json.JSONArray())
            put("specificCards", org.json.JSONArray())
        }.toString()

        val headers = getHeaders(token, personaId, utcTime)

        Thread {
            val res = CurlUtils.execute(url, "POST", headers, jsonBody)
            val code = res.code
            val body = res.body
            postLog("✔ [Inject Response] 状态码：$code", if (code == 200) LogLevel.SUCCESS else LogLevel.WARNING)
            if (code == 200) {
                postLog("⚡ 10000 钻石量子已成功归档到量子数据库！", LogLevel.SUCCESS)
            } else {
                postLog("服务器调试：$body", LogLevel.RAW)
            }
            onComplete(code == 200, code)
        }.start()
    }

    /**
     * Card All-Unlock Route via Curl
     */
    fun commitSoftPurchaseCards(
        token: String,
        personaId: String,
        utcTime: Long,
        cardCount: Int,
        onComplete: (success: Boolean, httpCode: Int) -> Unit
    ) {
        val url = "https://pvz-heroes.awspopcap.com/persistence/v2/inventory/commitSoftPurchase"
        postLog("→ 开始部署卡牌全量注入协议 [Count: $cardCount]...", LogLevel.INFO)
        postLog("POST $url", LogLevel.COMMAND)

        try {
            val cardsJsonMap = QuantumCards.buildCardsJsonMap(cardCount)
            val cardsObj = JSONObject()
            cardsJsonMap.forEach { (k, v) -> cardsObj.put(k, v) }

            val payload = JSONObject().apply {
                put("Sku", "deckRecipe")
                put("EventId", JSONObject.NULL)
                put("Cards", cardsObj)
                put("ExpectedCost", 10)
                put("KeyName", "default")
            }.toString()

            val headers = getHeaders(token, personaId, utcTime)

            Thread {
                val res = CurlUtils.execute(url, "POST", headers, payload)
                val code = res.code
                val body = res.body
                postLog("✔ [Card Unlock Response] 状态码：$code", if (code == 200) LogLevel.SUCCESS else LogLevel.WARNING)
                if (code == 200) {
                    postLog("⚡ 全量卡牌协议加载成功，解锁全部 ${QuantumCards.CARD_IDS.size} 种特种卡片！", LogLevel.SUCCESS)
                } else {
                    postLog("服务器调试：$body", LogLevel.RAW)
                }
                onComplete(code == 200, code)
            }.start()
        } catch (e: Exception) {
            postLog("❌ 构建卡牌数据体失败: ${e.message}", LogLevel.ERROR)
            onComplete(false, -1)
        }
    }

    /**
     * Helper to download files via Curl with progress tracking simulation.
     */
    fun downloadHeroFiles(
        onProgress: (String, Float) -> Unit,
        onComplete: (File?, File?) -> Unit
    ) {
        val invUrl1 = "https://ghfast.top/https://raw.githubusercontent.com/gsfsfec/bookish-carnival/main/PlayerInventory.txt"
        val invUrl2 = "https://ghfast.top/https://raw.githubusercontent.com/gsfsfec/bookish-carnival/main/PlayerInventoryDelta.txt"

        val tempFile1 = File.createTempFile("PlayerInventory", ".tmp")
        val tempFile2 = File.createTempFile("PlayerInventoryDelta", ".tmp")

        postLog("→ 开始量子下载 [PlayerInventory]", LogLevel.INFO)
        onProgress("PlayerInventory", 0.1f)
        
        Thread {
            val success1 = CurlUtils.downloadFile(invUrl1, tempFile1)
            if (!success1) {
                postLog("❌ 英雄协议本 1 下载失败", LogLevel.ERROR)
                onComplete(null, null)
                return@Thread
            }
            onProgress("PlayerInventory", 1.0f)

            postLog("→ 开始量子下载 [PlayerInventoryDelta]", LogLevel.INFO)
            onProgress("PlayerInventoryDelta", 0.1f)
            
            val success2 = CurlUtils.downloadFile(invUrl2, tempFile2)
            if (!success2) {
                postLog("❌ 英雄协议增量 2 下载失败", LogLevel.ERROR)
                onComplete(null, null)
            } else {
                onProgress("PlayerInventoryDelta", 1.0f)
                postLog("✔ 核心英雄协议文件全部下载成功！", LogLevel.SUCCESS)
                onComplete(tempFile1, tempFile2)
            }
        }.start()
    }

    private fun getHeaders(token: String, personaId: String, utcTime: Long): Map<String, String> {
        return mapOf(
            "Content-Type" to "application/json",
            "EADP-AUTH-TOKEN" to token,
            "EADP-PERSONA-ID" to personaId,
            "X-EADP-Client-Id" to "pvzheroes-2015-google-client",
            "X-Pvzh-Platform" to "Android",
            "X-Pvzh-Content-Version" to "45a337051e72592e53c9bf8a4b590639",
            "X-Pvzh-Client-Version" to "1.64.6",
            "X-Pvzh-UTC" to utcTime.toString()
        )
    }
}
