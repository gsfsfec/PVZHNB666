package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.utils.CredsParser
import com.example.utils.Credentials
import com.example.utils.LogLevel
import com.example.utils.QuantumNetworkManager
import com.example.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.utils.CurlUtils
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val message: String,
    val level: LogLevel,
    val timestamp: String = getCurrentTime()
)

fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
}

class QuantumViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _announcement = MutableStateFlow<String>("")
    val announcement: StateFlow<String> = _announcement.asStateFlow()

    private val _bootCompleted = MutableStateFlow(false)
    val bootCompleted: StateFlow<Boolean> = _bootCompleted.asStateFlow()

    private val _hasRootAccess = MutableStateFlow(false)
    val hasRootAccess: StateFlow<Boolean> = _hasRootAccess.asStateFlow()

    private val _useRoot = MutableStateFlow(true)
    val useRoot: StateFlow<Boolean> = _useRoot.asStateFlow()

    private val _credentialsLoaded = MutableStateFlow(false)
    val credentialsLoaded: StateFlow<Boolean> = _credentialsLoaded.asStateFlow()

    // Loaded credentials elements
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _refreshToken = MutableStateFlow("")
    val refreshToken: StateFlow<String> = _refreshToken.asStateFlow()

    private val _personaId = MutableStateFlow("")
    val personaId: StateFlow<String> = _personaId.asStateFlow()

    private val _expiresAt = MutableStateFlow(0L)
    val expiresAt: StateFlow<Long> = _expiresAt.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent: StateFlow<Float> = _progressPercent.asStateFlow()

    private val _progressTitle = MutableStateFlow("")
    val progressTitle: StateFlow<String> = _progressTitle.asStateFlow()

    private val networkManager = QuantumNetworkManager { msg, level ->
        addLog(msg, level)
    }

    init {
        // Run initial diagnostics asynchronously 
        checkDeviceSustains()
        fetchRemoteAnnouncement()
    }

    fun setBootCompleted() {
        _bootCompleted.value = true
    }

    fun addLog(msg: String, level: LogLevel = LogLevel.RAW) {
        val entry = LogEntry(msg, level)
        val currentList = _logs.value.toMutableList()
        currentList.add(entry)
        // Keep logs bounded to 800 items to avoid memory explosion
        if (currentList.size > 800) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }

    fun clearConsole() {
        _logs.value = emptyList()
        addLog("====== Quantum OS Terminal Clear ======", LogLevel.COMMAND)
    }

    private fun checkDeviceSustains() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = ShellUtils.isRootAvailable()
            _hasRootAccess.value = isRoot
            _useRoot.value = isRoot
            addLog("系统诊断：ROOT 权限就绪状态 = $isRoot", if (isRoot) LogLevel.SUCCESS else LogLevel.WARNING)
        }
    }

    fun autoInitSystemAndLoadCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⚙ 自动化引擎就绪进程已启动...", LogLevel.INFO)
            val isRoot = ShellUtils.isRootAvailable()
            _hasRootAccess.value = isRoot
            _useRoot.value = isRoot
            addLog("主控板诊断：ROOT 权限申请状态：${if (isRoot) "✔ 成功获得授权" else "❌ 未获取ROOT（免ROOT运行）"}", if (isRoot) LogLevel.SUCCESS else LogLevel.WARNING)
            
            // Auto scan and load local session files
            delay(500)
            loadSessionCredentials(forceRefreshStartup = true)
        }
    }

    fun autoRefreshOnResume() {
        viewModelScope.launch {
            if (_token.value.isNotEmpty()) {
                addLog("🔄 应用返回前台，量子常驻会话有效，无需重复同步。", LogLevel.SUCCESS)
                return@launch
            }
            addLog("🔄 检测到应用重入前台，开始同步本地会话凭证...", LogLevel.INFO)
            loadSessionCredentials()
        }
    }

    fun fetchRemoteAnnouncement() {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis() / 1000
            val announceUrl = "https://gist.githubusercontent.com/gsfsfec/4cc801bddaa2ea7e077db9c359ee5bda/raw/announce.txt?t=$timestamp"
            
            try {
                val res = CurlUtils.execute(announceUrl, "GET")
                if (res.code == 200 && res.body.isNotBlank()) {
                    _announcement.value = res.body.trim()
                } else {
                    _announcement.value = "PVZ 量子重构单元·天启协议\n仅供学习交流 | 禁止倒卖"
                }
            } catch (e: Exception) {
                _announcement.value = "PVZ 量子重构单元·天启协议\n仅供学习交流 | 禁止倒卖\n(连接公告服务器超时)"
            }
        }
    }

    /**
     * Replicates: auto_load_all_credentials
     */
    fun loadSessionCredentials(manualData: String? = null, forceRefreshStartup: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExecuting.value = true
            addLog("→ 正在扫描本地文件和会话配置以检测登录态...", LogLevel.INFO)
            
            val creds = CredsParser.loadCredentialsFromDevice(
                useRoot = _useRoot.value,
                simulatedData = manualData
            )

            if (creds != null) {
                _token.value = creds.token
                _refreshToken.value = creds.refreshToken
                _expiresAt.value = creds.expiresAt
                _personaId.value = creds.personaId
                _credentialsLoaded.value = true

                addLog("✔ 登录凭证成功匹配并载入内存！", LogLevel.SUCCESS)
                addLog("角色ID (personaId)：${creds.personaId}", LogLevel.INFO)
                addLog("量子令牌 (access_token)：${creds.token.take(20)}...", LogLevel.INFO)
                
                // Print expiration
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateStr = format.format(Date(creds.expiresAt))
                addLog("截止时间：$dateStr", LogLevel.INFO)

                // If forceRefreshStartup is requested on app launch, perform real token renewal once
                if (forceRefreshStartup && creds.refreshToken.isNotEmpty()) {
                    performStartupTokenRefresh(creds.refreshToken)
                }
            } else {
                _credentialsLoaded.value = false
                addLog("❌ 凭证匹配失败。请检查是否已登录过游戏，或者手动贴入凭证内容。", LogLevel.ERROR)
            }
            _isExecuting.value = false
        }
    }

    /**
     * Performs a real token refresh from Nucleus Server using native unblockable transport.
     */
    private suspend fun performStartupTokenRefresh(refreshVal: String): Boolean {
        addLog("→ 正在向 EA Nucleus 授权服务器发起令牌自动续期...", LogLevel.INFO)
        addLog("POST https://eadp.ea.com/accounts/api/v1/anonymous/login", LogLevel.COMMAND)

        return withContext(Dispatchers.IO) {
            val headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "X-Expand-Results" to "true",
                "X-Include-Underage" to "true"
            )
            val body = "grant_type=refresh_token" +
                    "&refresh_token=$refreshVal" +
                    "&client_id=ea-pvzheroes-production" +
                    "&client_secret=78a158d0-9f4b-4c8a-901e-2b3c4d5e6f7a" +
                    "&redirect_uri=nucleus:rest"

            try {
                val res = CurlUtils.execute("https://eadp.ea.com/accounts/api/v1/anonymous/login", "POST", headers, body)
                val code = res.code
                val responseBody = res.body
                addLog("← [HTTPS_CODE $code] 接收授权服务响应", if (code == 200) LogLevel.SUCCESS else LogLevel.ERROR)

                if (code == 200) {
                    val json = JSONObject(responseBody)
                    val access = json.optString("access_token", "")
                    val refresh = json.optString("refresh_token", "")
                    val expires = json.optLong("expires_in", 0L)
                    
                    if (access.isNotEmpty() && refresh.isNotEmpty()) {
                        _token.value = access
                        _refreshToken.value = refresh
                        _expiresAt.value = System.currentTimeMillis() + (expires * 1000)
                        _credentialsLoaded.value = true
                        addLog("✔ 启动令牌自动续期已成功注入并存储！", LogLevel.SUCCESS)
                        addLog("全新量子令牌: ${access.take(15)}...", LogLevel.INFO)
                        true
                    } else {
                        addLog("❌ 令牌解析失败 - 响应报文字段不全", LogLevel.ERROR)
                        false
                    }
                } else {
                    addLog("❌ 令牌续期失败（服务器拒绝 $code）：$responseBody", LogLevel.ERROR)
                    addLog("💡 系统将继续直接使用本地已解出的默认卡凭证进行注入发包。", LogLevel.WARNING)
                    false
                }
            } catch (e: Exception) {
                addLog("❌ 续期失败：网络异常或连接服务受阻: ${e.localizedMessage}", LogLevel.ERROR)
                addLog("💡 系统将继续直接使用本地已解出的默认卡凭证进行注入发包。", LogLevel.WARNING)
                false
            }
        }
    }

    /**
     * Saves credentials configured manually via text fields.
     */
    fun setManualCredentials(tokenValue: String, refreshValue: String, personaValue: String, expiresAtMs: Long) {
        if (tokenValue.isBlank() || refreshValue.isBlank() || personaValue.isBlank()) {
            addLog("❌ 凭证配置不可有空白项！", LogLevel.ERROR)
            return
        }
        _token.value = tokenValue.trim()
        _refreshToken.value = refreshValue.trim()
        _personaId.value = personaValue.trim()
        _expiresAt.value = if (expiresAtMs > 0) expiresAtMs else (System.currentTimeMillis() + 3600000)
        _credentialsLoaded.value = true
        addLog("✔ 凭证已手动配置，强制激活！", LogLevel.SUCCESS)
    }

    /**
     * Replicates: auto_refresh_token using robust suspend architecture
     */
    suspend fun suspendRefreshAuthToken(): Boolean {
        val refreshVal = _refreshToken.value
        if (refreshVal.isEmpty()) {
            addLog("❌ 本地内存中无登录续期令牌 (refresh_token)，无法自动续期！", LogLevel.ERROR)
            return false
        }
        return performStartupTokenRefresh(refreshVal)
    }

    fun triggerTokenRefresh(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            addLog("💡 正在执行手动网络令牌续期进程...", LogLevel.INFO)
            _isExecuting.value = true
            val success = suspendRefreshAuthToken()
            _isExecuting.value = false
            onComplete(success)
        }
    }

    fun autoRefreshOnTransition(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (_token.value.isNotEmpty()) {
                addLog("✔ 常驻量子通道处于就绪态，直接调用启动时刷新后的量子令牌...", LogLevel.SUCCESS)
                onComplete(true)
                return@launch
            }
            addLog("🔄 检测到进入功能分页面，同步最新会话凭证...", LogLevel.INFO)
            val creds = CredsParser.loadCredentialsFromDevice(useRoot = _useRoot.value)
            if (creds != null) {
                _token.value = creds.token
                _refreshToken.value = creds.refreshToken
                _expiresAt.value = creds.expiresAt
                _personaId.value = creds.personaId
                _credentialsLoaded.value = true
            }
            val success = _token.value.isNotEmpty()
            onComplete(success)
        }
    }

    /**
     * Checks if local token is valid (We return true as long as token is present, ignoring time since refresh logic is removed)
     */
    fun checkTokenValid(): Boolean {
        return _token.value.isNotEmpty()
    }

    /**
     * Ensures token is valid before we invoke APIs (Disabled refresh logic, directly use current credentials)
     */
    private suspend fun ensureValidToken(): Boolean {
        if (_token.value.isNotEmpty()) {
            addLog("✔ 量子发包：直接加载现有角色凭证序列...", LogLevel.SUCCESS)
            return true
        }
        addLog("⚠ 当前内存凭证为空，正在尝试重读本地游戏文件...", LogLevel.WARNING)
        val creds = CredsParser.loadCredentialsFromDevice(useRoot = _useRoot.value)
        if (creds != null) {
            _token.value = creds.token
            _refreshToken.value = creds.refreshToken
            _expiresAt.value = creds.expiresAt
            _personaId.value = creds.personaId
            _credentialsLoaded.value = true
            return true
        }
        addLog("❌ 无法获取有效的本地登录态，请登录一次游戏或执行调试手动贴入凭证！", LogLevel.ERROR)
        return false
    }

    /**
     * Replicates Option 1: Diamond Quantum Generation (钻石量子生成)
     */
    fun runDiamondQuantumGeneration(rounds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isExecuting.value) return@launch
            _isExecuting.value = true
            _progressTitle.value = "生成钻石量子中"
            _progressPercent.value = 0f

            addLog("=====================================", LogLevel.COMMAND)
            addLog("🚀 启动「钻石量子生成」模块，循环批次：$rounds", LogLevel.WARNING)
            addLog("=====================================", LogLevel.COMMAND)

            val tokenValid = ensureValidToken()
            if (!tokenValid || _token.value.isEmpty() || _personaId.value.isEmpty()) {
                addLog("❌ 自动化令牌刷新协议失效，终端中止执行。请重新登录游戏。", LogLevel.ERROR)
                _isExecuting.value = false
                return@launch
            }

            val t = _token.value
            val pId = _personaId.value

            for (i in 1..rounds) {
                _progressPercent.value = (i - 1).toFloat() / rounds.toFloat()
                addLog("\n--- 开始第 $i / $rounds 批次量子振荡 ---", LogLevel.INFO)
                
                val utcTime = System.currentTimeMillis()

                // Step 1: Update Daily Progress
                var step1Success = false
                networkManager.updateDailyLogin(t, pId, utcTime) { success, _ ->
                    step1Success = success
                }
                // Wait for async response
                while (true) {
                    delay(50)
                    // Network call is asynchronous, we wait for log responses or use completion synchronizers.
                    // Since okhttp Callback completes on background thread, we can synchronize using state variables:
                    break // This is handled below via a cleaner linear dispatcher
                }

                // Let's implement actual synchronized requests using OkHttp synchronous execution
                val success1 = performSyncDailyLogin(t, pId, utcTime)
                if (!success1) {
                    addLog("⚠ 提示：第 $i 批次身份认证受阻，继续尝试注入...", LogLevel.WARNING)
                }

                delay(500)

                // Step 2: Inject gems
                val success2 = performSyncLeagueRewards(t, pId, utcTime)
                if (!success2) {
                    addLog("❌ 第 $i 批次钻石量子注入失败", LogLevel.ERROR)
                } else {
                    addLog("✔ 第 $i 批次钻石量子注入成功！", LogLevel.SUCCESS)
                }

                _progressPercent.value = i.toFloat() / rounds.toFloat()
                delay(500)
            }

            _progressPercent.value = 1f
            addLog("\n⚡ 钻石量子全量发生操作已就绪。", LogLevel.SUCCESS)
            _isExecuting.value = false
        }
    }

    private fun performSyncDailyLogin(token: String, personaId: String, scaleTime: Long): Boolean {
        val url = "https://pvz-heroes.awspopcap.com/updateDailyLoginProgress?userId=$personaId"
        val payload = "{\"day\":1,\"forStreak\":true,\"currentStreakSetCompleted\":false}"
        val headers = mapOf(
            "Content-Type" to "application/json",
            "EADP-AUTH-TOKEN" to token,
            "EADP-PERSONA-ID" to personaId,
            "X-EADP-Client-Id" to "pvzheroes-2015-google-client",
            "X-Pvzh-Platform" to "Android",
            "X-Pvzh-Content-Version" to "45a337051e72592e53c9bf8a4b590639",
            "X-Pvzh-Client-Version" to "1.64.6",
            "X-Pvzh-UTC" to scaleTime.toString()
        )
        return try {
            val res = CurlUtils.execute(url, "POST", headers, payload)
            addLog("✔ [Identity Validation] HTTP Code: ${res.code}", if (res.code == 200) LogLevel.SUCCESS else LogLevel.WARNING)
            res.code == 200
        } catch (e: Exception) {
            addLog("❌ [Identity Validation] Failed: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    private fun performSyncLeagueRewards(token: String, personaId: String, scaleTime: Long): Boolean {
        val url = "https://pvz-heroes.awspopcap.com/pvp/v1/leagueRewards/sync?playerId=$personaId"
        val payload = "{\"tickets\":0,\"gems\":10000,\"sparks\":0,\"packs\":[],\"specificCards\":[]}"
        val headers = mapOf(
            "Content-Type" to "application/json",
            "EADP-AUTH-TOKEN" to token,
            "EADP-PERSONA-ID" to personaId,
            "X-EADP-Client-Id" to "pvzheroes-2015-google-client",
            "X-Pvzh-Platform" to "Android",
            "X-Pvzh-Content-Version" to "45a337051e72592e53c9bf8a4b590639",
            "X-Pvzh-Client-Version" to "1.64.6",
            "X-Pvzh-UTC" to scaleTime.toString()
        )
        return try {
            val res = CurlUtils.execute(url, "POST", headers, payload)
            addLog("✔ [Inject Action] HTTP Code: ${res.code}", if (res.code == 200) LogLevel.SUCCESS else LogLevel.ERROR)
            res.code == 200
        } catch (e: Exception) {
            addLog("❌ [Inject Action] Failed: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    /**
     * Replicates Option 2: Card All-Unlock (卡牌全量解锁)
     */
    fun runCardAllUnlock(cardCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isExecuting.value) return@launch
            _isExecuting.value = true
            _progressTitle.value = "解锁所有卡牌中"
            _progressPercent.value = 0.3f

            addLog("=====================================", LogLevel.COMMAND)
            addLog("🚀 启动「限时特种卡牌全量写入」模块，设值：$cardCount", LogLevel.WARNING)
            addLog("=====================================", LogLevel.COMMAND)

            val tokenValid = ensureValidToken()
            if (!tokenValid || _token.value.isEmpty() || _personaId.value.isEmpty()) {
                addLog("❌ 自动化令牌刷新协议失效，终端中止执行。", LogLevel.ERROR)
                _isExecuting.value = false
                return@launch
            }

            _progressPercent.value = 0.6f
            val t = _token.value
            val pId = _personaId.value
            val scaleTime = System.currentTimeMillis()

            networkManager.commitSoftPurchaseCards(t, pId, scaleTime, cardCount) { success, code ->
                _progressPercent.value = 1f
                _isExecuting.value = false
            }
        }
    }

    /**
     * Replicates Option 3: Full Heroes Unlock Injection (全英雄权限解锁)
     * Handles both local folders writing (requires root) and user manual SD downloads.
     */
    fun runHeroesUnlockAndBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isExecuting.value) return@launch
            _isExecuting.value = true
            _progressTitle.value = "加载并部署英雄协议"
            _progressPercent.value = 0f

            addLog("=====================================", LogLevel.COMMAND)
            addLog("🚀 启动「全英雄权限解锁」协议注射器...", LogLevel.WARNING)
            addLog("=====================================", LogLevel.COMMAND)

            // Step 1: Download files
            _progressPercent.value = 0.1f
            delay(500)
            
            networkManager.downloadHeroFiles(
                onProgress = { file, progress ->
                    // Average the two files progress
                    _progressPercent.value = 0.1f + (progress * 0.4f)
                },
                onComplete = { file1, file2 ->
                    if (file1 == null || file2 == null) {
                        addLog("❌ 核心协议下载失败，操作撤回。", LogLevel.ERROR)
                        _progressPercent.value = 1f
                        _isExecuting.value = false
                    } else {
                        // Execute local directory writing and backups
                        executeLocalAndRootInjection(file1, file2)
                    }
                }
            )
        }
    }

    private fun executeLocalAndRootInjection(file1: File, file2: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _progressTitle.value = "执行协议匹配与注入"
            _progressPercent.value = 0.6f
            
            val isRoot = _useRoot.value && _hasRootAccess.value
            addLog("状态诊断：试图执行 ROOT 自动重定位 = $isRoot", LogLevel.INFO)

            // Ensure public cache folders exist for manual users or backups
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "刷卡文件")
            val backupDir = File(downloadsDir, "源文件")
            try {
                downloadsDir.mkdirs()
                backupDir.mkdirs()

                // Copy files to public storage according to script specifications
                val pubFile1 = File(downloadsDir, "PlayerInventory")
                val pubFile2 = File(downloadsDir, "PlayerInventoryDelta")
                file1.copyTo(pubFile1, overwrite = true)
                file2.copyTo(pubFile2, overwrite = true)

                val bakFile1 = File(backupDir, "PlayerInventory")
                val bakFile2 = File(backupDir, "PlayerInventoryDelta")
                file1.copyTo(bakFile1, overwrite = true)
                file2.copyTo(bakFile2, overwrite = true)

                addLog("✔ 已成功备份核心授权到公共目录：", LogLevel.SUCCESS)
                addLog("→ ${pubFile1.absolutePath}", LogLevel.RAW)
                addLog("→ ${pubFile2.absolutePath}", LogLevel.RAW)

            } catch (e: Exception) {
                addLog("⚠ 公共下载目录备份受限(非关键)：${e.message}", LogLevel.WARNING)
                addLog("提示：如果遇到外部存储写授权问题，可使用内置 ROOT 模式直接强制读写。", LogLevel.INFO)
            }

            _progressPercent.value = 0.8f

            if (isRoot) {
                addLog("🔧 正在通过 ROOT 权限直接写回游戏目录...", LogLevel.INFO)
                
                // Construct command line mimicking script to copy and copy permissions.
                val gameBaseDir = "/data/data/com.ea.gp.pvzheroes/files/PlayerData"
                val cmdString = """
                    if [ -d "$gameBaseDir" ]; then
                        player_folders=""
                        for item in $gameBaseDir/*/; do
                            if [ -d "${'$'}{item}" ] && echo "${'$'}{item}" | grep -qE "/[0-9]+/$"; then
                                player_folders="${'$'}{player_folders} ${'$'}{item}"
                            fi
                        done
                        
                        if [ -z "${'$'}player_folders" ]; then
                            echo "[ERROR] No character profiles matched under PlayerData folders."
                            exit 2
                        fi
                        
                        for folder in ${'$'}player_folders; do
                            echo "[INJECT] Initiating folder: ${'$'}{folder##*/}"
                            cp -af "${file1.absolutePath}" "${'$'}{folder}PlayerInventory"
                            cp -af "${file2.absolutePath}" "${'$'}{folder}PlayerInventoryDelta"
                            chmod 666 "${'$'}{folder}PlayerInventory" "${'$'}{folder}PlayerInventoryDelta" 2>/dev/null
                        done
                        echo "[SUCCESS] Inject Complete."
                    else
                        echo "[ERROR] PlayerData game base catalog is missing. Is game installed?"
                        exit 1
                    fi
                """.trimIndent()

                val rootResult = ShellUtils.runCommand(useRoot = true, command = cmdString)
                
                if (rootResult.isSuccess) {
                    addLog("✔ ROOT 自动注入执行成功！", LogLevel.SUCCESS)
                    addLog(rootResult.stdout, LogLevel.RAW)
                } else {
                    addLog("❌ ROOT 注入失败（ExitCode ${rootResult.exitCode}）：", LogLevel.ERROR)
                    addLog(rootResult.stderr, LogLevel.WARNING)
                    addLog(rootResult.stdout, LogLevel.RAW)
                    addLog("\n💡 免 Root 技术提示：请手动将上述生成的 /Download/刷卡文件 复制置入 /data/data/com.ea.gp.pvzheroes/files/PlayerData/[玩家数字ID]/ 文件夹内。", LogLevel.INFO)
                }
            } else {
                addLog("❌ ROOT 权限关闭或不存在，自动安全跳过 ROOT 直写入。", LogLevel.WARNING)
                addLog("✔ 已自动将该批文件部署至您的共享下载夹：", LogLevel.SUCCESS)
                addLog("📁 /sdcard/Download/刷卡文件/", LogLevel.WARNING)
                addLog("👉 请使用“MT管理器”或具备 Root 授权的软体，手动将该资料夹下的 PlayerInventory 与 PlayerInventoryDelta 覆写至：", LogLevel.INFO)
                addLog("🎯 /data/data/com.ea.gp.pvzheroes/files/PlayerData/【您的玩家数字ID】/ 目录下，并赋予修改权限（666/可读写）。", LogLevel.SUCCESS)
            }

            _progressPercent.value = 1f
            _isExecuting.value = false
        }
    }
}
