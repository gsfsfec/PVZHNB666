package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BootScreen(
    onBootFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val statusText = remember { mutableStateOf("正在初始化核心模块...") }
    val currentStep = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Step-by-step progress update exactly matching script loading times
        statusText.value = "正在初始化核心模块..."
        progress.animateTo(
            targetValue = 0.2f,
            animationSpec = tween(precisionSpeed(350), easing = FastOutSlowInEasing)
        )
        delay(150)
        
        statusText.value = "量子核心  已匹配到环境..."
        progress.animateTo(
            targetValue = 0.45f,
            animationSpec = tween(precisionSpeed(400), easing = FastOutSlowInEasing)
        )
        delay(100)
        
        statusText.value = "权限协议协议池  安全校验已就绪..."
        progress.animateTo(
            targetValue = 0.7f,
            animationSpec = tween(precisionSpeed(300), easing = FastOutSlowInEasing)
        )
        delay(120)
        
        statusText.value = "数据网关  远程连接已同步配对..."
        progress.animateTo(
            targetValue = 0.9f,
            animationSpec = tween(precisionSpeed(300), easing = FastOutSlowInEasing)
        )
        delay(100)

        statusText.value = "核心引擎重构功能就绪，启动终端控制台..."
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(precisionSpeed(200))
        )
        delay(250)
        onBootFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(24.dp).widthIn(max = 500.dp)
        ) {
            // Header
            Text(
                "=========================================",
                color = PurpleNeon,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "               天启量子引擎 · 启动中",
                color = CyanNeon,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "=========================================",
                color = PurpleNeon,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                "  " + statusText.value,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Beautiful terminal-like progressive bar
            val blocksCount = (progress.value * 25).toInt()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QuantumTerminalBg)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "  [",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                
                // Active cyan blocks
                Row(modifier = Modifier.weight(1f)) {
                    repeat(blocksCount) {
                        Text(
                            text = "▰",
                            color = CyanNeon,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                    repeat(25 - blocksCount) {
                        Text(
                            text = " ",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }

                Text(
                    text = "] ${(progress.value * 100).toInt()}%",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(50.dp))
            
            // Console footer
            Text(
                "PVZ Quantum Engine, V1.64.6 Optimized",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

private fun precisionSpeed(base: Int): Int {
    return base
}
