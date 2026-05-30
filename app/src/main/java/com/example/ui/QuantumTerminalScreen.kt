package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.utils.LogLevel
import com.example.viewmodel.QuantumViewModel
import kotlinx.coroutines.launch

@Composable
fun QuantumTerminalScreen(
    viewModel: QuantumViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val logs by viewModel.logs.collectAsState()
    val announcement by viewModel.announcement.collectAsState()
    val hasRoot by viewModel.hasRootAccess.collectAsState()
    val useRoot by viewModel.useRoot.collectAsState()
    val credentialsLoaded by viewModel.credentialsLoaded.collectAsState()

    val token by viewModel.token.collectAsState()
    val refreshToken by viewModel.refreshToken.collectAsState()
    val personaId by viewModel.personaId.collectAsState()
    val expiresAt by viewModel.expiresAt.collectAsState()

    val isExecuting by viewModel.isExecuting.collectAsState()
    val progressPercent by viewModel.progressPercent.collectAsState()
    val progressTitle by viewModel.progressTitle.collectAsState()

    // Page state variable: "mainPage", "cardPage", "diamondPage"
    var currentPage by remember { mutableStateOf("mainPage") }

    // Inputs inside children pages
    var cardsCountText by remember { mutableStateOf("999999") }
    var diamondRoundsText by remember { mutableStateOf("1") }

    // Manual Credentials expanded area
    var isManualCredsExpanded by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    var manualRefreshToken by remember { mutableStateOf("") }
    var manualPersonaId by remember { mutableStateOf("") }

    // Info dialogue overlay toggles
    var showSponsorDialog by remember { mutableStateOf(false) }

    // Auto sync state editors when VM logs credentials
    LaunchedEffect(token, refreshToken, personaId) {
        if (token.isNotEmpty()) {
            manualToken = token
            manualRefreshToken = refreshToken
            manualPersonaId = personaId
        }
    }

    // Monitor App Resume/Re-entry to trigger automatic token refresh
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.autoRefreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Standard Terminal Auto scroll state
    val logListState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch {
                logListState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App Global Background Image
        Image(
            painter = painterResource(id = R.drawable.img_global_bg_1780052602908),
            contentDescription = "Cosmic Sky Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Scaffolding Layer
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.systemBars
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        slideInHorizontally { width -> if (targetState == "mainPage") -width else width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> if (targetState == "mainPage") width else -width } + fadeOut()
                    },
                    label = "page_transition"
                ) { state ->
                    when (state) {
                        "mainPage" -> {
                            MainPageContent(
                                announcement = announcement,
                                hasRoot = hasRoot,
                                credentialsLoaded = credentialsLoaded,
                                personaId = personaId,
                                isExecuting = isExecuting,
                                isManualCredsExpanded = isManualCredsExpanded,
                                manualToken = manualToken,
                                manualRefreshToken = manualRefreshToken,
                                manualPersonaId = manualPersonaId,
                                progressTitle = progressTitle,
                                progressPercent = progressPercent,
                                onToggleCreds = { isManualCredsExpanded = !isManualCredsExpanded },
                                onManualTokenChange = { manualToken = it },
                                onManualRefreshTokenChange = { manualRefreshToken = it },
                                onManualPersonaIdChange = { manualPersonaId = it },
                                onSaveManualCreds = {
                                    viewModel.setManualCredentials(manualToken, manualRefreshToken, manualPersonaId, expiresAt)
                                },
                                onSyncCreds = {
                                    viewModel.loadSessionCredentials()
                                },
                                onRefreshCreds = {
                                    viewModel.triggerTokenRefresh()
                                },
                                onSwitchPage = { currentPage = it },
                                onUnlockHeroes = { viewModel.runHeroesUnlockAndBackup() },
                                onJoinQQGroup = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&card_type=group&uin=792870590"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/cgi-bin/qm/qr?k=W_X48rN4Wv8RclgN-rS3g-4xY5L5L15A&authKey=792870590"))
                                            context.startActivity(intent)
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, "未找到QQ客户端，可手动添加群: 792870590", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onSponsor = { showSponsorDialog = true }
                            )
                        }

                        "cardPage" -> {
                            LaunchedEffect(Unit) {
                                viewModel.autoRefreshOnTransition()
                            }
                            CardUnlockPage(
                                cardsCountText = cardsCountText,
                                isExecuting = isExecuting,
                                logs = logs,
                                logListState = logListState,
                                onCardsCountChange = { cardsCountText = it },
                                onExecute = {
                                    val count = cardsCountText.toIntOrNull() ?: 999999
                                    viewModel.runCardAllUnlock(count)
                                },
                                onGoBack = { currentPage = "mainPage" }
                            )
                        }

                        "diamondPage" -> {
                            LaunchedEffect(Unit) {
                                viewModel.autoRefreshOnTransition()
                            }
                            DiamondGenerationPage(
                                roundsText = diamondRoundsText,
                                isExecuting = isExecuting,
                                logs = logs,
                                logListState = logListState,
                                onRoundsChange = { diamondRoundsText = it },
                                onExecute = {
                                    val rounds = diamondRoundsText.toIntOrNull() ?: 1
                                    viewModel.runDiamondQuantumGeneration(rounds)
                                },
                                onGoBack = { currentPage = "mainPage" }
                            )
                        }
                    }
                }
            }
        }
    }

    // Sponsorship Image Dialog (1:1 perfect original image restoration)
    if (showSponsorDialog) {
        Dialog(
            onDismissRequest = { showSponsorDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(0.75f) // Aspect ratio matching 1200x1600 original image perfectly
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSponsorDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_sponsor_qr_1780052569573),
                    contentDescription = "WeChat Pay Donation QR Code Original",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun MainPageContent(
    announcement: String,
    hasRoot: Boolean,
    credentialsLoaded: Boolean,
    personaId: String,
    isExecuting: Boolean,
    isManualCredsExpanded: Boolean,
    manualToken: String,
    manualRefreshToken: String,
    manualPersonaId: String,
    progressTitle: String,
    progressPercent: Float,
    onToggleCreds: () -> Unit,
    onManualTokenChange: (String) -> Unit,
    onManualRefreshTokenChange: (String) -> Unit,
    onManualPersonaIdChange: (String) -> Unit,
    onSaveManualCreds: () -> Unit,
    onSyncCreds: () -> Unit,
    onRefreshCreds: () -> Unit,
    onSwitchPage: (String) -> Unit,
    onUnlockHeroes: () -> Unit,
    onJoinQQGroup: () -> Unit,
    onSponsor: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Main Header Panel
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "功能面板",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (hasRoot) Color(0xFF00FFCC) else Color(0xFFFF9900))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasRoot) "已自动申请ROOT权限" else "免ROOT运行模式",
                    color = if (hasRoot) Color(0xFF00FFCC) else Color(0xFFFFCC33),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Announcement Window
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Notice",
                        tint = Color(0xFFA880FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "公告说明",
                        color = Color(0xFFA880FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = announcement.ifBlank { "正在加载公告..." },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Credentials & Diagnostics Display Area
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = "Keys",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "凭证状态面板",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Credentials diagnostics badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (credentialsLoaded) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color(0xFFFF5555).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (credentialsLoaded) "已载入 (ACTIVE)" else "未检测到凭证",
                            color = if (credentialsLoaded) Color(0xFF00FFCC) else Color(0xFFFF5555),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (credentialsLoaded) {
                    Text(
                        text = "玩家标识 (ID)：$personaId",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "未检测到本地登录凭证，请登录英雄启动后尝试或手动贴入。",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                // Credentials setup actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncCreds,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Scan", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重扫本地", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onRefreshCreds,
                        modifier = Modifier.weight(1f),
                        enabled = credentialsLoaded && !isExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("手动刷新", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onToggleCreds,
                        modifier = Modifier.weight(0.7f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isManualCredsExpanded) Icons.Rounded.Close else Icons.Rounded.Edit,
                            contentDescription = "Manual setup",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("调试", fontSize = 11.sp)
                    }
                }

                // Expandable manual settings credentials inputs
                if (isManualCredsExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = onManualTokenChange,
                            label = { Text("Access Token") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                            )
                        )

                        OutlinedTextField(
                            value = manualRefreshToken,
                            onValueChange = onManualRefreshTokenChange,
                            label = { Text("Refresh Token") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                            )
                        )

                        OutlinedTextField(
                            value = manualPersonaId,
                            onValueChange = onManualPersonaIdChange,
                            label = { Text("Persona ID") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                            )
                        )

                        Button(
                            onClick = onSaveManualCreds,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6C63FF),
                                contentColor = Color.White
                            )
                        ) {
                            Text("应用配置", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Progress Bar (when running heroes or async functions)
        if (isExecuting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(progressTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(progressPercent * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00E5FF),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
        }

        // Functional Button Panel Wrap (Exactly styled and transparent like HTML buttons)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Function 1: 刷卡
            Button(
                onClick = { onSwitchPage("cardPage") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("刷卡", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }

            // Function 2: 刷钻石
            Button(
                onClick = { onSwitchPage("diamondPage") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("刷钻石", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }

            // Function 3: 刷英雄
            Button(
                onClick = onUnlockHeroes,
                enabled = !isExecuting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text(
                    text = if (isExecuting) "正在重构中..." else "刷英雄",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }

            // Function 4: 加入群聊
            Button(
                onClick = onJoinQQGroup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("加入群聊", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }

            // Function 5: 赞助
            Button(
                onClick = onSponsor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("赞赏", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Web Panel Footer style
        Text(
            text = "极简功能面板 · 纯净体验",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}

@Composable
fun CardUnlockPage(
    cardsCountText: String,
    isExecuting: Boolean,
    logs: List<com.example.viewmodel.LogEntry>,
    logListState: androidx.compose.foundation.lazy.LazyListState,
    onCardsCountChange: (String) -> Unit,
    onExecute: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Sub Header
        Text(
            text = "刷卡功能",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        // Number Input Box for card quantity
        OutlinedTextField(
            value = cardsCountText,
            onValueChange = onCardsCountChange,
            placeholder = { Text("卡牌将写入数量", color = Color.White.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(0.9f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // Status Card showing state and response
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Get last execution logs info
                val lastLog = logs.lastOrNull { it.level == LogLevel.SUCCESS || it.level == LogLevel.ERROR }
                val codeText = if (lastLog != null) {
                    if (lastLog.level == LogLevel.SUCCESS) "200（请求成功）" else "400（参数异常或认证过期）"
                } else "--"

                Text(
                    text = "状态：${if (isExecuting) "执行中..." else "等待执行"}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "最终状态码：$codeText",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "日志跟踪控制流：",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // Real-time progressive terminal logs inside sub-window
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(
                        state = logListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            val color = when (log.level) {
                                LogLevel.SUCCESS -> Color(0xFF00FFCC)
                                LogLevel.ERROR -> Color(0xFFFF5555)
                                LogLevel.WARNING -> Color(0xFFFFCC33)
                                LogLevel.INFO -> Color(0xFF00E5FF)
                                LogLevel.COMMAND -> Color(0xFFA880FF)
                                else -> Color.White
                            }
                            Text(
                                text = "[${log.timestamp}] ${log.message}",
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Sub Buttons wrap
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onExecute,
                enabled = !isExecuting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("开始执行", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }

            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("返回主页", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "极简功能面板 · 纯净体验",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}

@Composable
fun DiamondGenerationPage(
    roundsText: String,
    isExecuting: Boolean,
    logs: List<com.example.viewmodel.LogEntry>,
    logListState: androidx.compose.foundation.lazy.LazyListState,
    onRoundsChange: (String) -> Unit,
    onExecute: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Sub Header
        Text(
            text = "刷钻石功能",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        // Number Input Box for batch loops
        OutlinedTextField(
            value = roundsText,
            onValueChange = onRoundsChange,
            placeholder = { Text("刷取钻石批次轮数", color = Color.White.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(0.9f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // Status Card showing state and response
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "系统运行状态面板：",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Real-time progressive logs of diamond injection directly scrollable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("等待任务启动中...", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            state = logListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs) { log ->
                                val color = when (log.level) {
                                    LogLevel.SUCCESS -> Color(0xFF00FFCC)
                                    LogLevel.ERROR -> Color(0xFFFF5555)
                                    LogLevel.WARNING -> Color(0xFFFFCC33)
                                    LogLevel.INFO -> Color(0xFF00E5FF)
                                    LogLevel.COMMAND -> Color(0xFFA880FF)
                                    else -> Color.White
                                }
                                Text(
                                    text = "[${log.timestamp}] ${log.message}",
                                    color = color,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub Buttons wrap
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onExecute,
                enabled = !isExecuting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("开始执行", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }

            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("返回主页", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "极简功能面板 · 纯净体验",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}
