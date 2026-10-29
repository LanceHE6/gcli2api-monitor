package com.hycer.gcli2api.monitor.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hycer.gcli2api.monitor.data.ApiClient
import com.hycer.gcli2api.monitor.data.ConfigManager
import com.hycer.gcli2api.monitor.data.MonitorSummary
import com.hycer.gcli2api.monitor.widget.MonitorWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 若是通过 Shortcut 点击拉起，则触发添加逻辑
        if (intent?.action == "com.hycer.gcli2api.monitor.ACTION_PIN_WIDGET") {
            pinWidgetToHome()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF1E1E2E),
                    surface = Color(0xFF2A2B3D),
                    primary = Color(0xFF89B4FA)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun pinWidgetToHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val myProvider = ComponentName(this, MonitorWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val success = appWidgetManager.requestPinAppWidget(myProvider, null, null)
                if (!success) {
                    Toast.makeText(this, "系统未响应，请检查 HyperOS 是否开启「桌面快捷方式」权限", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "当前桌面启动器不支持直接锁定小部件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = this
        val scope = rememberCoroutineScope()

        val isAlreadyConfigured = remember { ConfigManager.isConfigured(context) }
        var showConfigCard by remember { mutableStateOf(!isAlreadyConfigured) }
        var baseUrl by remember { mutableStateOf(ConfigManager.getBaseUrl(context)) }
        var apiKey by remember { mutableStateOf(ConfigManager.getApiKey(context)) }
        var showApiKey by remember { mutableStateOf(false) }

        var summary by remember { mutableStateOf<MonitorSummary?>(ConfigManager.getCachedSummary(context)) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showPermissionDialog by remember { mutableStateOf(false) }

        fun refreshData() {
            isLoading = true
            errorMessage = null
            scope.launch {
                val res = ApiClient.fetchSummary(context)
                res.onSuccess {
                    summary = it
                    ConfigManager.saveCachedSummary(context, it)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    MonitorWidgetProvider.updateAllWidgets(context, appWidgetManager)
                }.onFailure {
                    errorMessage = it.message
                }
                isLoading = false
            }
        }

        fun handlePinWidget() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val myProvider = ComponentName(context, MonitorWidgetProvider::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    val requested = appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    // HyperOS 若未赋予“桌面快捷方式”权限，请求通常会被静默丢弃
                    showPermissionDialog = true
                } else {
                    Toast.makeText(context, "当前系统不支持一键添加，请在桌面空白处长按添加", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 首次打开若已配置且无缓存，则自动刷新一次
        LaunchedEffect(Unit) {
            if (ConfigManager.isConfigured(context) && summary == null) {
                refreshData()
            }
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("添加桌面组件提示") },
                text = {
                    Text(
                        "已向系统发起添加小部件请求。\n\n若桌面未弹出确认窗口，是因为 HyperOS (小米系统) 默认拦截了第三方应用的「桌面快捷方式」权限。\n\n是否前往权限设置开启？"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "请在系统设置中找到本应用开启权限", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("去权限设置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("已弹出/知道了")
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：标题 + 添加到桌面 + 刷新 + 设置
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API 额度监控",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    FilledTonalButton(
                        onClick = { handlePinWidget() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("添加组件", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalButton(
                        onClick = { refreshData() },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (isLoading) "..." else "刷新", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = { showConfigCard = !showConfigCard },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (showConfigCard) "收起" else "设置", fontSize = 12.sp)
                    }
                }
            }

            // 服务配置卡片
            if (showConfigCard) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "服务接入配置",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text("服务地址 (如 http://192.168.1.4:7861)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API Key / Bearer Token") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showApiKey = !showApiKey }) {
                                        Text(
                                            text = if (showApiKey) "隐藏" else "显示",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        ConfigManager.saveConfig(context, baseUrl, apiKey)
                                        showConfigCard = false
                                        Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                                        refreshData()
                                    }
                                ) {
                                    Text("保存并应用")
                                }

                                if (isAlreadyConfigured) {
                                    TextButton(onClick = { showConfigCard = false }) {
                                        Text("取消", color = Color(0xFFA6ADC8))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Text(
                        text = "请求失败: $errorMessage",
                        color = Color(0xFFF38BA8),
                        fontSize = 14.sp
                    )
                }
            }

            // 总览概况卡片
            if (summary != null) {
                val s = summary!!
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF313244))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "总览概况",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "更新于 ${s.lastUpdated}",
                                    color = Color(0xFFA6ADC8),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "活跃账号: ${s.validAccounts} / ${s.totalAccounts}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val gwPct = Math.round(s.avgGeminiWeeklyRemaining * 100)
                            val g5hPct = Math.round(s.avgGemini5hRemaining * 100)
                            val cwPct = Math.round(s.avgClaudeWeeklyRemaining * 100)
                            val c5hPct = Math.round(s.avgClaude5hRemaining * 100)

                            val gMin = minOf(gwPct, g5hPct)
                            val cMin = minOf(cwPct, c5hPct)

                            Text(
                                text = "Gemini 系列总均额: 周 $gwPct%  |  5h $g5hPct%",
                                color = if (gMin < 20) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Claude 系列总均额: 周 $cwPct%  |  5h $c5hPct%",
                                color = if (cMin < 20) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "各账号额度详情",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                items(s.accountDetails) { acc ->
                    val accDisplayName = if (acc.email.contains("@")) {
                        acc.email.substringBefore("@")
                    } else {
                        acc.email.substringBefore(".")
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = accDisplayName,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "全名: ${acc.email}",
                                color = Color(0xFFA6ADC8),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val gwPct = Math.round(acc.geminiWeeklyRemaining * 100)
                            val g5hPct = Math.round(acc.gemini5hRemaining * 100)
                            val cwPct = Math.round(acc.claudeWeeklyRemaining * 100)
                            val c5hPct = Math.round(acc.claude5hRemaining * 100)

                            val gwResetStr = if (!acc.geminiWeeklyReset.isNullOrEmpty() && acc.geminiWeeklyReset != "N/A") " [周重置: ${acc.geminiWeeklyReset}]" else ""
                            val g5hResetStr = if (!acc.gemini5hReset.isNullOrEmpty() && acc.gemini5hReset != "N/A") " [5h重置: ${acc.gemini5hReset}]" else ""

                            val cwResetStr = if (!acc.claudeWeeklyReset.isNullOrEmpty() && acc.claudeWeeklyReset != "N/A") " [周重置: ${acc.claudeWeeklyReset}]" else ""
                            val c5hResetStr = if (!acc.claude5hReset.isNullOrEmpty() && acc.claude5hReset != "N/A") " [5h重置: ${acc.claude5hReset}]" else ""

                            Text(
                                text = "Gemini:",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "  周额度: $gwPct%$gwResetStr",
                                color = if (gwPct < 20) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "  5h额度: $g5hPct%$g5hResetStr",
                                color = if (g5hPct < 20) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                fontSize = 12.5.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Claude / 3P:",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "  周额度: $cwPct%$cwResetStr",
                                color = if (cwPct < 20) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "  5h额度: $c5hPct%$c5hResetStr",
                                color = if (c5hPct < 20) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
