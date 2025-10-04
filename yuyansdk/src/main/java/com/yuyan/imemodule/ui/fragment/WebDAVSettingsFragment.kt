package com.yuyan.imemodule.ui.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import com.yuyan.imemodule.BuildConfig
import com.yuyan.imemodule.R
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.sync.WebDAVSyncManager
import com.yuyan.imemodule.ui.fragment.base.CsPreferenceFragment
import com.yuyan.imemodule.utils.addPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WebDAV 同步设置界面
 */
class WebDAVSettingsFragment : CsPreferenceFragment() {
    
    private val prefs get() = AppPrefs.getInstance().webdav
    private var progressDialog: AlertDialog? = null
    
    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(preferenceManager.context)
        onPreferenceUiCreated(preferenceScreen)
    }
    
    private fun onPreferenceUiCreated(screen: PreferenceScreen) {
        // 检查是否为离线版本
        if (BuildConfig.offline) {
            screen.addPreference("⚠️ 离线版本提示", "WebDAV 云同步功能仅在在线版本中可用") {
                AlertDialog.Builder(requireContext())
                    .setTitle("离线版本")
                    .setMessage("""
                        当前为离线版本，WebDAV 云同步功能不可用。
                        
                        如需使用云同步功能，请安装在线版本：
                        • 支持 WebDAV 云同步
                        • 支持手写输入
                        • 支持在线语音识别
                        
                        离线版本特点：
                        • 完全离线运行
                        • 保护隐私安全
                        • 无需网络连接
                    """.trimIndent())
                    .setPositiveButton("确定", null)
                    .show()
            }
            return
        }
        
        // 启用/禁用 WebDAV 同步
        val enabledPref = screen.addPreference("启用 WebDAV 同步", if (prefs.enabled.getValue()) "✅ 已启用" else "❌ 已禁用") {
            val currentValue = prefs.enabled.getValue()
            AlertDialog.Builder(requireContext())
                .setTitle("WebDAV 同步")
                .setMessage(if (currentValue) "确定要禁用 WebDAV 同步吗？" else "启用 WebDAV 同步功能")
                .setPositiveButton(if (currentValue) "禁用" else "启用") { _, _ ->
                    prefs.enabled.setValue(!currentValue)
                    refreshScreen()
                    Toast.makeText(requireContext(), if (!currentValue) "已启用" else "已禁用", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        // 服务器设置
        screen.addPreference("🌐 服务器地址", if (prefs.serverUrl.getValue().isBlank()) "未设置" else prefs.serverUrl.getValue()) {
            showInputDialog(
                title = "服务器地址",
                hint = "https://dav.example.com",
                currentValue = prefs.serverUrl.getValue(),
                inputType = InputType.TYPE_TEXT_VARIATION_URI
            ) { value ->
                var normalizedValue = value.trim()
                
                // 坚果云特殊处理
                if (normalizedValue.contains("jianguoyun.com")) {
                    // 自动修复坚果云URL格式
                    if (!normalizedValue.startsWith("https://")) {
                        normalizedValue = "https://$normalizedValue"
                    }
                    if (!normalizedValue.contains("dav.jianguoyun.com")) {
                        normalizedValue = normalizedValue.replace("jianguoyun.com", "dav.jianguoyun.com")
                    }
                    if (!normalizedValue.endsWith("/dav/")) {
                        normalizedValue = normalizedValue.trimEnd('/') + "/dav/"
                    }
                    
                    Toast.makeText(requireContext(), "已自动修复坚果云URL格式", Toast.LENGTH_SHORT).show()
                }
                
                prefs.serverUrl.setValue(normalizedValue)
                refreshScreen()
            }
        }
        
        // 用户名
        screen.addPreference("👤 用户名", if (prefs.username.getValue().isBlank()) "未设置" else prefs.username.getValue()) {
            val hint = if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
                "your-email@example.com (坚果云要求完整邮箱)"
            } else {
                "your_username"
            }
            
            showInputDialog(
                title = "用户名",
                hint = hint,
                currentValue = prefs.username.getValue()
            ) { value ->
                var normalizedValue = value.trim()
                
                // 坚果云特殊处理
                if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
                    // 去除所有空格
                    normalizedValue = normalizedValue.replace("\\s+".toRegex(), "")
                    
                    // 验证邮箱格式
                    val hasAtSymbol = normalizedValue.contains("@")
                    val hasDotSymbol = normalizedValue.contains(".")
                    
                    if (!hasAtSymbol || !hasDotSymbol) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("⚠️ 格式提醒")
                            .setMessage("""
                                坚果云要求用户名必须是完整的邮箱地址
                                
                                当前输入: '$value'
                                处理后: '$normalizedValue'
                                
                                请确保包含 @ 和 . 符号
                                正确格式: your-email@example.com
                                
                                如果确认格式正确，请点击"确定"保存
                            """.trimIndent())
                            .setPositiveButton("确定") { _, _ ->
                                prefs.username.setValue(normalizedValue)
                                refreshScreen()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                        return@showInputDialog
                    }
                }
                
                prefs.username.setValue(normalizedValue)
                refreshScreen()
            }
        }
        
        // 密码
        screen.addPreference("🔑 密码", if (prefs.password.getValue().isBlank()) "未设置" else "••••••••") {
            val hint = if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
                "应用密码 (不是登录密码)"
            } else {
                "your_password"
            }
            
            showInputDialog(
                title = "密码",
                hint = hint,
                currentValue = prefs.password.getValue(),
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ) { value ->
                // 坚果云特殊处理
                if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
                    // 检查密码长度
                    if (value.length < 6) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("⚠️ 密码提醒")
                            .setMessage("""
                                坚果云应用密码通常比较长（6位以上）
                                
                                当前密码长度: ${value.length} 位
                                
                                请确认使用的是应用密码而不是登录密码
                                应用密码可以在坚果云设置中生成
                                
                                如果确认是应用密码，请点击"确定"保存
                            """.trimIndent())
                            .setPositiveButton("确定") { _, _ ->
                                prefs.password.setValue(value)
                                refreshScreen()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                        return@showInputDialog
                    }
                }
                
                prefs.password.setValue(value)
                refreshScreen()
            }
        }
        
        // 远程路径
        screen.addPreference("📁 远程路径", prefs.remotePath.getValue()) {
            showInputDialog(
                title = "远程路径",
                hint = "/yuyan_ime_backup/",
                currentValue = prefs.remotePath.getValue()
            ) { value ->
                val normalizedValue = if (value.startsWith("/")) value else "/$value"
                val finalValue = if (normalizedValue.endsWith("/")) normalizedValue else "$normalizedValue/"
                prefs.remotePath.setValue(finalValue)
                refreshScreen()
            }
        }
        
        // 智能连接测试（自动检测服务器类型）
        screen.addPreference("🔌 智能连接测试", "自动检测服务器类型并使用相应测试模式") {
            testSmartConnection()
        }
        
        // 仅在 WiFi 下同步
        screen.addPreference("📶 仅 WiFi 同步", if (prefs.syncOnWifiOnly.getValue()) "✅ 仅 WiFi" else "⚠️ 允许移动网络") {
            val currentValue = prefs.syncOnWifiOnly.getValue()
            prefs.syncOnWifiOnly.setValue(!currentValue)
            refreshScreen()
            Toast.makeText(
                requireContext(), 
                if (!currentValue) "仅在 WiFi 下同步" else "允许移动网络同步", 
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // 忽略SSL证书（用于自建服务器）
        screen.addPreference("🔒 忽略SSL证书", if (prefs.ignoreSSLCert.getValue()) "⚠️ 已忽略SSL验证" else "✅ 验证SSL证书") {
            val currentValue = prefs.ignoreSSLCert.getValue()
            if (!currentValue) {
                // 显示警告对话框
                AlertDialog.Builder(requireContext())
                    .setTitle("⚠️ 忽略SSL证书")
                    .setMessage("""
                        忽略SSL证书验证会降低安全性！
                        
                        仅在以下情况下使用：
                        • 自建服务器使用自签名证书
                        • 内网环境测试
                        • 确定服务器安全可信
                        
                        注意：此选项会跳过SSL证书验证，
                        可能导致中间人攻击风险。
                        
                        确定要忽略SSL证书验证吗？
                    """.trimIndent())
                    .setPositiveButton("确定忽略") { _, _ ->
                        prefs.ignoreSSLCert.setValue(true)
                        refreshScreen()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                prefs.ignoreSSLCert.setValue(false)
                refreshScreen()
            }
        }
        
        // 自动同步
        screen.addPreference("🔄 自动同步", if (prefs.autoSync.getValue()) "✅ 已启用" else "❌ 已禁用") {
            val currentValue = prefs.autoSync.getValue()
            prefs.autoSync.setValue(!currentValue)
            refreshScreen()
            Toast.makeText(
                requireContext(), 
                if (!currentValue) "已启用自动同步" else "已禁用自动同步", 
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // 手动上传
        screen.addPreference("⬆️ 上传备份") {
            performSync(WebDAVSyncManager.SyncOperation.UPLOAD)
        }
        
        // 手动下载
        screen.addPreference("⬇️ 下载备份", "⚠️ 将覆盖本地数据，请谨慎操作") {
            performSync(WebDAVSyncManager.SyncOperation.DOWNLOAD)
        }
        
        // 智能同步
        screen.addPreference("🔄 智能同步", "自动判断上传或下载") {
            performSync(WebDAVSyncManager.SyncOperation.AUTO)
        }
        
        // 清理旧备份
        screen.addPreference("🗑️ 清理旧备份", "保留最近 5 个备份") {
            AlertDialog.Builder(requireContext())
                .setTitle("清理旧备份")
                .setMessage("保留最近 5 个备份，删除其余备份文件？")
                .setPositiveButton("确定") { _, _ ->
                    cleanupOldBackups()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        // 同步状态
        val lastSyncTime = WebDAVSyncManager.getLastSyncTimeFormatted()
        val success = prefs.lastSyncSuccess.getValue()
        screen.addPreference("📊 同步状态", "上次同步: $lastSyncTime ${if (success) "✅" else "❌"}") {
            showSyncStatus()
        }
        
        // 坚果云配置助手
        if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
            screen.addPreference("🔧 坚果云配置助手") {
                showJianguoyunHelper()
            }
            
            // 详细调试测试
            screen.addPreference("🔍 详细调试测试", "显示完整的调试信息") {
                performDetailedDebugTest()
            }
        }
        
        // 使用说明
        screen.addPreference("❓ 使用说明") {
            showHelp()
        }
    }
    
    private fun showInputDialog(
        title: String,
        hint: String,
        currentValue: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        onSave: (String) -> Unit
    ) {
        val editText = EditText(requireContext()).apply {
            this.hint = hint
            setText(currentValue)
            this.inputType = inputType
            setPadding(50, 30, 50, 30)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val value = editText.text.toString().trim()
                onSave(value)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun testSmartConnection() {
        // 检查配置完整性
        val serverUrl = prefs.serverUrl.getValue()
        val username = prefs.username.getValue()
        val password = prefs.password.getValue()
        
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            AlertDialog.Builder(requireContext())
                .setTitle("❌ 配置不完整")
                .setMessage("请先配置服务器地址、用户名和密码")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        showProgressDialog("正在进行智能连接测试...")
        
        lifecycleScope.launch {
            try {
                val client = com.yuyan.imemodule.sync.WebDAVClient(
                    prefs.serverUrl.getValue(),
                    prefs.username.getValue(),
                    prefs.password.getValue(),
                    prefs.ignoreSSLCert.getValue()
                )
                
                val result = client.testSmartConnection()
                
                dismissProgressDialog()
                
                if (isAdded) {
                    result.fold(
                        onSuccess = {
                            val serverUrl = prefs.serverUrl.getValue()
                            val isJianguoyun = serverUrl.contains("jianguoyun.com")
                            val protocol = when {
                                serverUrl.startsWith("https://") -> "HTTPS"
                                serverUrl.startsWith("http://") -> "HTTP"
                                else -> "未知"
                            }
                            
                            val serverType = if (isJianguoyun) "坚果云" else "标准WebDAV"
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("✅ 智能连接测试成功")
                                .setMessage("""
                                    连接测试成功！
                                    
                                    检测结果：
                                    • 服务器类型：$serverType
                                    • 协议类型：$protocol
                                    • 服务器地址：$serverUrl
                                    • SSL证书：${if (prefs.ignoreSSLCert.getValue()) "已忽略验证" else "正常验证"}
                                    
                                    现在可以正常使用WebDAV同步功能。
                                """.trimIndent())
                                .setPositiveButton("确定", null)
                                .show()
                        },
                        onFailure = { error ->
                            val serverUrl = prefs.serverUrl.getValue()
                            val isJianguoyun = serverUrl.contains("jianguoyun.com")
                            val serverType = if (isJianguoyun) "坚果云" else "标准WebDAV"
                            
                            val errorMessage = when {
                                error.message?.contains("cert", ignoreCase = true) == true -> {
                                    """
                                    ❌ SSL证书验证失败
                                    
                                    错误信息：${error.message}
                                    
                                    检测结果：
                                    • 服务器类型：$serverType
                                    • 协议类型：HTTPS
                                    • 问题：SSL证书验证失败
                                    
                                    解决方案：
                                    1. 启用"忽略SSL证书"选项
                                    2. 联系服务器管理员更新证书
                                    3. 使用有效的SSL证书
                                    
                                    建议：对于自建服务器，可以启用"忽略SSL证书"选项。
                                    """.trimIndent()
                                }
                                error.message?.contains("401") == true -> {
                                    """
                                    ❌ 认证失败 (401 Unauthorized)
                                    
                                    错误信息：${error.message}
                                    
                                    检测结果：
                                    • 服务器类型：$serverType
                                    • 问题：用户名或密码错误
                                    
                                    解决方案：
                                    1. 检查用户名是否正确
                                    2. 检查密码是否正确
                                    3. 确认用户账户状态
                                    
                                    ${if (isJianguoyun) "坚果云提示：请使用应用密码而不是登录密码" else ""}
                                    """.trimIndent()
                                }
                                error.message?.contains("403") == true -> {
                                    """
                                    ❌ 权限被拒绝 (403 Forbidden)
                                    
                                    错误信息：${error.message}
                                    
                                    检测结果：
                                    • 服务器类型：$serverType
                                    • 问题：用户没有写入权限
                                    
                                    解决方案：
                                    1. 联系服务器管理员添加写入权限
                                    2. 检查WebDAV服务器配置
                                    3. 确认用户账户权限设置
                                    """.trimIndent()
                                }
                                error.message?.contains("404") == true -> {
                                    """
                                    ❌ 目录不存在 (404 Not Found)
                                    
                                    错误信息：${error.message}
                                    
                                    检测结果：
                                    • 服务器类型：$serverType
                                    • 问题：远程路径不存在
                                    
                                    解决方案：
                                    1. 检查远程路径是否正确
                                    2. 在服务器上创建该目录
                                    3. 使用正确的路径格式
                                    """.trimIndent()
                                }
                                else -> {
                                    """
                                    ❌ 连接测试失败
                                    
                                    错误信息：${error.message}
                                    
                                    检测结果：
                                    • 服务器类型：$serverType
                                    • 问题：网络连接或配置问题
                                    
                                    可能原因：
                                    1. 网络连接问题
                                    2. 服务器不可达
                                    3. 防火墙阻止连接
                                    4. 服务器配置错误
                                    
                                    建议：
                                    1. 检查网络连接
                                    2. 验证服务器地址
                                    3. 检查防火墙设置
                                    4. 联系服务器管理员
                                    """.trimIndent()
                                }
                            }
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("❌ 智能连接测试失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    )
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                if (isAdded) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("❌ 测试异常")
                        .setMessage("智能连接测试过程中发生异常：${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun testConnection() {
        // 先验证坚果云配置
        if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
            val username = prefs.username.getValue()
            val password = prefs.password.getValue()
            
            // 添加调试信息
            android.util.Log.d("WebDAVSettings", "Server URL: ${prefs.serverUrl.getValue()}")
            android.util.Log.d("WebDAVSettings", "Username: '$username' (length: ${username.length})")
            android.util.Log.d("WebDAVSettings", "Password length: ${password.length}")
            
            // 详细分析用户名中的每个字符
            android.util.Log.d("WebDAVSettings", "Username character codes:")
            username.forEachIndexed { index, char ->
                android.util.Log.d("WebDAVSettings", "  [$index]: '$char' (code: ${char.code})")
            }
            
            // 检查用户名格式
            val trimmedUsername = username.trim()
            android.util.Log.d("WebDAVSettings", "Trimmed username: '$trimmedUsername'")
            android.util.Log.d("WebDAVSettings", "Trimmed username character codes:")
            trimmedUsername.forEachIndexed { index, char ->
                android.util.Log.d("WebDAVSettings", "  [$index]: '$char' (code: ${char.code})")
            }
            
            // 检查各种 @ 符号变体
            val atSymbols = listOf("@", "＠", "﹫", "⒜") // 不同编码的@符号
            val hasAtSymbol = atSymbols.any { trimmedUsername.contains(it) }
            
            android.util.Log.d("WebDAVSettings", "Contains standard @: ${trimmedUsername.contains("@")}")
            android.util.Log.d("WebDAVSettings", "Contains any @ variant: $hasAtSymbol")
            android.util.Log.d("WebDAVSettings", "Contains .: ${trimmedUsername.contains(".")}")
            
            // 检查是否有其他类似的字符
            atSymbols.forEach { symbol ->
                android.util.Log.d("WebDAVSettings", "Contains '$symbol': ${trimmedUsername.contains(symbol)}")
            }
            
            if (!hasAtSymbol || !trimmedUsername.contains(".")) {
                val characterDetails = trimmedUsername.mapIndexed { index, char ->
                    "[$index]: '$char' (U+${char.code.toString(16).uppercase()})"
                }.joinToString("\n")
                
                AlertDialog.Builder(requireContext())
                    .setTitle("⚠️ 配置问题")
                    .setMessage("""
                        坚果云要求用户名必须是完整的邮箱地址
                        
                        当前用户名: '$username'
                        去除空格后: '$trimmedUsername'
                        用户名长度: ${username.length}
                        包含@符号: $hasAtSymbol
                        包含.符号: ${trimmedUsername.contains(".")}
                        
                        字符详情:
                        $characterDetails
                        
                        请确保用户名包含 @ 和 . 符号
                        正确格式: your-email@example.com
                        
                        如果确认格式正确，请点击"强制测试"
                    """.trimIndent())
                    .setPositiveButton("强制测试") { _, _ ->
                        // 跳过验证，直接测试连接
                        performActualConnectionTest()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
            
            // 检查密码长度（应用密码通常比较长）
            android.util.Log.d("WebDAVSettings", "Password length: ${password.length}")
            if (password.length < 6) {
                AlertDialog.Builder(requireContext())
                    .setTitle("⚠️ 配置问题")
                    .setMessage("""
                        应用密码通常比较长（6位以上）
                        
                        当前密码长度: ${password.length} 位
                        
                        请确认使用的是应用密码而不是登录密码
                        如果确认是应用密码，请点击"确定"继续测试
                    """.trimIndent())
                    .setPositiveButton("确定", null)
                    .setNegativeButton("取消") { _, _ -> return@setNegativeButton }
                    .show()
                return
            }
        }
        
        performActualConnectionTest()
    }
    
    private fun checkDirectoryPermissions() {
        showProgressDialog("正在检查目录权限...")
        
        lifecycleScope.launch {
            try {
                val client = com.yuyan.imemodule.sync.WebDAVClient(
                    prefs.serverUrl.getValue(),
                    prefs.username.getValue(),
                    prefs.password.getValue()
                )
                
                val remotePath = prefs.remotePath.getValue()
                val result = client.checkDirectoryPermissions(remotePath)
                
                dismissProgressDialog()
                
                if (isAdded) {
                    result.fold(
                        onSuccess = {
                            AlertDialog.Builder(requireContext())
                                .setTitle("✅ 权限检查成功")
                                .setMessage("""
                                    目录权限检查成功！
                                    
                                    检查详情：
                                    • 远程路径：$remotePath
                                    • 用户：${prefs.username.getValue()}
                                    • 权限：读写权限正常
                                    • 状态：可以正常同步文件
                                    
                                    现在可以正常使用WebDAV同步功能。
                                """.trimIndent())
                                .setPositiveButton("确定", null)
                                .show()
                        },
                        onFailure = { error ->
                            val errorMessage = when {
                                error.message?.contains("403") == true -> {
                                    """
                                    ❌ 权限被拒绝 (403 Forbidden)
                                    
                                    错误信息：${error.message}
                                    
                                    问题分析：
                                    用户 "${prefs.username.getValue()}" 没有对目录 "$remotePath" 的写入权限
                                    
                                    解决方案：
                                    1. 联系服务器管理员，为用户添加写入权限
                                    2. 检查WebDAV服务器配置
                                    3. 确认用户账户权限设置
                                    4. 尝试使用不同的远程路径
                                    
                                    常见原因：
                                    • 用户只有读取权限，没有写入权限
                                    • WebDAV服务器配置限制
                                    • 目录权限设置问题
                                    """.trimIndent()
                                }
                                error.message?.contains("404") == true -> {
                                    """
                                    ❌ 目录不存在 (404 Not Found)
                                    
                                    错误信息：${error.message}
                                    
                                    问题分析：
                                    远程路径 "$remotePath" 不存在
                                    
                                    解决方案：
                                    1. 检查远程路径是否正确
                                    2. 在服务器上创建该目录
                                    3. 使用正确的路径格式
                                    
                                    建议：
                                    • 确保路径以 "/" 开头
                                    • 检查路径拼写是否正确
                                    • 确认服务器上存在该目录
                                    """.trimIndent()
                                }
                                error.message?.contains("401") == true -> {
                                    """
                                    ❌ 认证失败 (401 Unauthorized)
                                    
                                    错误信息：${error.message}
                                    
                                    问题分析：
                                    用户名或密码错误
                                    
                                    解决方案：
                                    1. 检查用户名是否正确
                                    2. 检查密码是否正确
                                    3. 确认用户账户状态
                                    """.trimIndent()
                                }
                                else -> {
                                    """
                                    ❌ 权限检查失败
                                    
                                    错误信息：${error.message}
                                    
                                    可能原因：
                                    1. 网络连接问题
                                    2. 服务器配置问题
                                    3. 权限设置问题
                                    
                                    建议：
                                    1. 检查网络连接
                                    2. 联系服务器管理员
                                    3. 检查WebDAV配置
                                    """.trimIndent()
                                }
                            }
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("❌ 权限检查失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    )
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                if (isAdded) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("❌ 检查异常")
                        .setMessage("权限检查过程中发生异常：${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun testSSLConnection() {
        showProgressDialog("正在测试连接...")
        
        lifecycleScope.launch {
            try {
                val client = com.yuyan.imemodule.sync.WebDAVClient(
                    prefs.serverUrl.getValue(),
                    prefs.username.getValue(),
                    prefs.password.getValue()
                )
                
                val result = client.testSSLConnection()
                
                dismissProgressDialog()
                
                if (isAdded) {
                    result.fold(
                        onSuccess = {
                            val serverUrl = prefs.serverUrl.getValue()
                            val protocol = if (serverUrl.startsWith("https://")) "HTTPS" else "HTTP"
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("✅ 连接成功")
                                .setMessage("""
                                    ${protocol}连接测试成功！
                                    
                                    测试详情：
                                    • 服务器地址：$serverUrl
                                    • 协议类型：$protocol
                                    • 网络连接正常
                                    • 可以连接服务器
                                    
                                    现在可以正常使用WebDAV同步功能。
                                """.trimIndent())
                                .setPositiveButton("确定", null)
                                .show()
                        },
                        onFailure = { error ->
                            val errorMessage = when {
                                error.message?.contains("cert", ignoreCase = true) == true -> {
                                    """
                                    ❌ SSL证书验证失败
                                    
                                    错误信息：${error.message}
                                    
                                    可能原因：
                                    1. 服务器使用自签名证书
                                    2. SSL证书已过期
                                    3. 证书链不完整
                                    4. 证书颁发机构不受信任
                                    
                                    解决方案：
                                    1. 联系服务器管理员更新证书
                                    2. 使用有效的SSL证书
                                    3. 或者使用HTTP连接（不推荐）
                                    
                                    注意：应用已尝试使用不安全的SSL连接，
                                    如果仍然失败，请检查服务器配置。
                                    """.trimIndent()
                                }
                                else -> {
                                    """
                                    ❌ SSL连接测试失败
                                    
                                    错误信息：${error.message}
                                    
                                    可能原因：
                                    1. 网络连接问题
                                    2. 服务器不可达
                                    3. 防火墙阻止连接
                                    4. SSL配置错误
                                    
                                    建议：
                                    1. 检查网络连接
                                    2. 验证服务器地址
                                    3. 检查防火墙设置
                                    4. 联系服务器管理员
                                    """.trimIndent()
                                }
                            }
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("❌ SSL连接失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    )
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                if (isAdded) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("❌ 测试异常")
                        .setMessage("测试过程中发生异常：${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun testJianguoyunSpecific() {
        showProgressDialog("正在进行坚果云专门测试...")
        
        lifecycleScope.launch {
            try {
                val client = com.yuyan.imemodule.sync.WebDAVClient(
                    prefs.serverUrl.getValue(),
                    prefs.username.getValue(),
                    prefs.password.getValue()
                )
                
                val result = client.testJianguoyunWebDAV()
                
                dismissProgressDialog()
                
                if (isAdded) {
                    result.fold(
                        onSuccess = {
                            AlertDialog.Builder(requireContext())
                                .setTitle("✅ 坚果云测试成功")
                                .setMessage("""
                                    坚果云WebDAV连接测试成功！
                                    
                                    测试详情：
                                    • 使用标准WebDAV PROPFIND协议
                                    • 认证信息验证通过
                                    • 服务器响应正常
                                    
                                    现在可以正常使用WebDAV同步功能。
                                """.trimIndent())
                                .setPositiveButton("确定", null)
                                .show()
                        },
                        onFailure = { error ->
                            val errorMessage = """
                                ❌ 坚果云专门测试失败
                                
                                错误信息：${error.message}
                                
                                可能原因：
                                1. 用户名或密码错误
                                2. 应用密码权限不足
                                3. 服务器地址格式错误
                                4. 网络连接问题
                                
                                建议：
                                1. 检查用户名是否为完整邮箱地址
                                2. 确认使用应用密码而不是登录密码
                                3. 验证应用密码有读写权限
                                4. 检查服务器地址：https://dav.jianguoyun.com/dav/
                            """.trimIndent()
                            
                            AlertDialog.Builder(requireContext())
                                .setTitle("❌ 坚果云测试失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    )
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                if (isAdded) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("❌ 测试异常")
                        .setMessage("测试过程中发生异常：${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun performActualConnectionTest() {
        showProgressDialog("正在测试连接...")
        
        lifecycleScope.launch {
            val result = WebDAVSyncManager.testConnection()
            
            dismissProgressDialog()
            
            if (isAdded) {
                result.fold(
                    onSuccess = {
                        AlertDialog.Builder(requireContext())
                            .setTitle("✅ 连接成功")
                            .setMessage("WebDAV 服务器连接正常")
                            .setPositiveButton("确定", null)
                            .show()
                    },
                    onFailure = { error ->
                        val errorMessage = when {
                            error.message?.contains("closed", ignoreCase = true) == true -> {
                                """
                                ❌ 连接被关闭
                                
                                可能原因：
                                1. 网络连接不稳定
                                2. 服务器超时或重启
                                3. 防火墙阻止连接
                                4. 坚果云服务器限制
                                5. 网络代理问题
                                
                                解决方案：
                                1. 检查网络连接是否稳定
                                2. 尝试切换网络（WiFi/移动数据）
                                3. 检查防火墙设置
                                4. 稍后重试（服务器可能临时不可用）
                                5. 检查代理设置
                                
                                提示：应用已自动重试3次，如果仍然失败，
                                请检查网络环境或稍后再试。
                                """.trimIndent()
                            }
                            error.message?.contains("401") == true -> {
                                if (prefs.serverUrl.getValue().contains("jianguoyun.com")) {
                                    """
                                    ❌ 坚果云认证失败 (401)
                                    
                                    常见原因：
                                    1. 使用了登录密码而不是应用密码
                                    2. 用户名不是完整邮箱地址
                                    3. 应用密码权限不足
                                    4. 用户名包含空格或特殊字符
                                    
                                    解决步骤：
                                    1. 登录坚果云网页版
                                    2. 进入"账户信息" → "安全选项"
                                    3. 生成新的应用密码（确保有读写权限）
                                    4. 用户名使用完整邮箱地址（如：user@example.com）
                                    5. 密码使用刚生成的应用密码
                                    
                                    检查当前配置：
                                    • 服务器: ${prefs.serverUrl.getValue()}
                                    • 用户名: ${prefs.username.getValue()}
                                    • 密码长度: ${prefs.password.getValue().length} 位
                                    """.trimIndent()
                                } else {
                                    """
                                    ❌ 认证失败 (401)
                                    
                                    可能原因：
                                    1. 用户名或密码错误
                                    2. 使用了登录密码而不是应用密码
                                    3. 应用密码权限不足
                                    
                                    解决方案：
                                    1. 确认用户名格式正确
                                    2. 使用应用密码（不是登录密码）
                                    3. 检查应用密码是否有读写权限
                                    """.trimIndent()
                                }
                            }
                            error.message?.contains("403") == true -> {
                                """
                                ❌ 权限不足 (403)
                                
                                可能原因：
                                1. 应用密码权限不足
                                2. 服务器拒绝访问
                                
                                解决方案：
                                1. 重新生成应用密码，确保有读写权限
                                2. 检查服务器地址是否正确
                                """.trimIndent()
                            }
                            error.message?.contains("404") == true -> {
                                """
                                ❌ 服务器地址错误 (404)
                                
                                解决方案：
                                1. 检查服务器地址格式
                                2. 确保地址以 /dav/ 结尾
                                3. 正确格式：https://dav.jianguoyun.com/dav/
                                """.trimIndent()
                            }
                            error.message?.contains("timeout") == true -> {
                                """
                                ❌ 连接超时
                                
                                解决方案：
                                1. 检查网络连接
                                2. 尝试使用 WiFi
                                3. 检查防火墙设置
                                """.trimIndent()
                            }
                            else -> {
                                """
                                ❌ 连接失败
                                
                                错误信息：${error.message}
                                
                                请检查：
                                1. 服务器地址格式
                                2. 用户名和密码
                                3. 网络连接
                                4. 防火墙设置
                                """.trimIndent()
                            }
                        }
                        
                        AlertDialog.Builder(requireContext())
                            .setTitle("❌ 连接失败")
                            .setMessage(errorMessage)
                            .setPositiveButton("确定", null)
                            .show()
                    }
                )
            }
        }
    }
    
    private fun performSync(operation: WebDAVSyncManager.SyncOperation) {
        if (operation == WebDAVSyncManager.SyncOperation.DOWNLOAD) {
            // 下载前确认
            AlertDialog.Builder(requireContext())
                .setTitle("⚠️ 警告")
                .setMessage("下载备份将覆盖所有本地数据，包括：\n• 输入法设置\n• 用户词库\n• 个人数据\n\n确定要继续吗？")
                .setPositiveButton("确定") { _, _ ->
                    doSync(operation)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            doSync(operation)
        }
    }
    
    private fun doSync(operation: WebDAVSyncManager.SyncOperation) {
        val progressView = createProgressView()
        
        progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("同步中...")
            .setView(progressView)
            .setCancelable(false)
            .create()
        
        progressDialog?.show()
        
        val statusText = progressView.getChildAt(1) as TextView
        
        lifecycleScope.launch {
            val result = WebDAVSyncManager.sync(operation) { status ->
                if (isAdded && !isDetached) {
                    statusText.text = status
                }
            }
            
            dismissProgressDialog()
            
            if (!isAdded) return@launch
            
            when (result) {
                is WebDAVSyncManager.SyncResult.Success -> {
                    refreshScreen()
                    AlertDialog.Builder(requireContext())
                        .setTitle("✅ 同步成功")
                        .setMessage(when (operation) {
                            WebDAVSyncManager.SyncOperation.UPLOAD -> "备份已上传到 WebDAV 服务器"
                            WebDAVSyncManager.SyncOperation.DOWNLOAD -> "备份已从 WebDAV 下载\n数据已导入，应用将重启"
                            WebDAVSyncManager.SyncOperation.AUTO -> "同步完成"
                        })
                        .setPositiveButton("确定") { _, _ ->
                            if (operation == WebDAVSyncManager.SyncOperation.DOWNLOAD) {
                                // 使用与导入用户数据完全相同的逻辑
                                lifecycleScope.launch(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                                    kotlinx.coroutines.delay(400L)
                                    com.yuyan.imemodule.utils.AppUtil.exit()
                                }
                                com.yuyan.imemodule.utils.AppUtil.showRestartNotification(requireContext())
                                android.widget.Toast.makeText(requireContext(), R.string.user_data_imported, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                }
                is WebDAVSyncManager.SyncResult.Error -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("❌ 同步失败")
                        .setMessage("错误: ${result.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
                is WebDAVSyncManager.SyncResult.Cancelled -> {
                    Toast.makeText(requireContext(), "同步已取消", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun cleanupOldBackups() {
        showProgressDialog("正在清理旧备份...")
        
        lifecycleScope.launch {
            val result = WebDAVSyncManager.cleanupOldBackups()
            
            dismissProgressDialog()
            
            if (isAdded) {
                result.fold(
                    onSuccess = { deletedCount ->
                        AlertDialog.Builder(requireContext())
                            .setTitle("✅ 清理完成")
                            .setMessage("已删除 $deletedCount 个旧备份文件")
                            .setPositiveButton("确定", null)
                            .show()
                    },
                    onFailure = { error ->
                        AlertDialog.Builder(requireContext())
                            .setTitle("❌ 清理失败")
                            .setMessage("错误: ${error.message}")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                )
            }
        }
    }
    
    private fun showSyncStatus() {
        val lastSyncTime = WebDAVSyncManager.getLastSyncTimeFormatted()
        val success = prefs.lastSyncSuccess.getValue()
        val enabled = prefs.enabled.getValue()
        val autoSync = prefs.autoSync.getValue()
        
        val message = """
            📊 同步状态信息
            
            启用状态: ${if (enabled) "✅ 已启用" else "❌ 已禁用"}
            自动同步: ${if (autoSync) "✅ 已启用" else "❌ 已禁用"}
            上次同步: $lastSyncTime
            同步结果: ${if (success) "✅ 成功" else "❌ 失败"}
            
            服务器: ${prefs.serverUrl.getValue()}
            远程路径: ${prefs.remotePath.getValue()}
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("同步状态")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showJianguoyunHelper() {
        val currentConfig = """
            当前配置检查：
            
            🌐 服务器地址: ${prefs.serverUrl.getValue()}
            👤 用户名: ${prefs.username.getValue()}
            🔑 密码长度: ${prefs.password.getValue().length} 位
            📁 远程路径: ${prefs.remotePath.getValue()}
            
            配置状态分析：
        """.trimIndent()
        
        val analysis = buildString {
            append(currentConfig)
            append("\n\n")
            
            // 服务器地址检查
            val serverUrl = prefs.serverUrl.getValue()
            if (serverUrl.contains("dav.jianguoyun.com") && serverUrl.endsWith("/dav/")) {
                append("✅ 服务器地址格式正确\n")
            } else {
                append("❌ 服务器地址格式可能有问题\n")
                append("   正确格式: https://dav.jianguoyun.com/dav/\n")
            }
            
            // 用户名检查
            val username = prefs.username.getValue().trim().replace("\\s+".toRegex(), "")
            val hasAtSymbol = username.contains("@")
            val hasDotSymbol = username.contains(".")
            if (hasAtSymbol && hasDotSymbol) {
                append("✅ 用户名格式正确（包含@和.符号）\n")
            } else {
                append("❌ 用户名格式可能有问题\n")
                append("   应该是完整邮箱地址，如: user@example.com\n")
            }
            
            // 密码检查
            val passwordLength = prefs.password.getValue().length
            if (passwordLength >= 6) {
                append("✅ 密码长度合理（${passwordLength}位）\n")
            } else {
                append("❌ 密码长度可能太短（${passwordLength}位）\n")
                append("   应用密码通常6位以上\n")
            }
            
            append("\n🔧 配置建议：\n")
            if (!serverUrl.contains("dav.jianguoyun.com") || !serverUrl.endsWith("/dav/")) {
                append("• 修正服务器地址为: https://dav.jianguoyun.com/dav/\n")
            }
            if (!hasAtSymbol || !hasDotSymbol) {
                append("• 用户名使用完整邮箱地址\n")
            }
            if (passwordLength < 6) {
                append("• 确认使用应用密码而不是登录密码\n")
            }
            append("• 在坚果云网页版生成新的应用密码\n")
            append("• 确保应用密码有读写权限\n")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("🔧 坚果云配置助手")
            .setMessage(analysis)
            .setPositiveButton("自动修复") { _, _ ->
                autoFixJianguoyunConfig()
            }
            .setNegativeButton("手动配置", null)
            .show()
    }
    
    private fun autoFixJianguoyunConfig() {
        var fixed = false
        
        // 修复服务器地址
        val currentServerUrl = prefs.serverUrl.getValue()
        if (!currentServerUrl.contains("dav.jianguoyun.com") || !currentServerUrl.endsWith("/dav/")) {
            prefs.serverUrl.setValue("https://dav.jianguoyun.com/dav/")
            fixed = true
        }
        
        // 修复用户名（去除空格）
        val currentUsername = prefs.username.getValue()
        val cleanedUsername = currentUsername.trim().replace("\\s+".toRegex(), "")
        if (currentUsername != cleanedUsername) {
            prefs.username.setValue(cleanedUsername)
            fixed = true
        }
        
        if (fixed) {
            Toast.makeText(requireContext(), "已自动修复配置", Toast.LENGTH_SHORT).show()
            refreshScreen()
        } else {
            Toast.makeText(requireContext(), "配置无需修复", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun performDetailedDebugTest() {
        showProgressDialog("正在进行详细调试测试...")
        
        lifecycleScope.launch {
            try {
                val debugInfo = buildString {
                    append("=== 坚果云WebDAV详细调试信息 ===\n\n")
                    
                    // 当前配置
                    append("📋 当前配置:\n")
                    append("服务器地址: ${prefs.serverUrl.getValue()}\n")
                    append("用户名: '${prefs.username.getValue()}'\n")
                    append("密码长度: ${prefs.password.getValue().length} 位\n")
                    append("远程路径: ${prefs.remotePath.getValue()}\n\n")
                    
                    // 用户名分析
                    val username = prefs.username.getValue()
                    val trimmedUsername = username.trim()
                    val cleanedUsername = trimmedUsername.replace("\\s+".toRegex(), "")
                    
                    append("🔍 用户名分析:\n")
                    append("原始用户名: '$username'\n")
                    append("去除首尾空格: '$trimmedUsername'\n")
                    append("去除所有空格: '$cleanedUsername'\n")
                    append("包含@符号: ${cleanedUsername.contains("@")}\n")
                    append("包含.符号: ${cleanedUsername.contains(".")}\n")
                    append("用户名长度: ${cleanedUsername.length}\n\n")
                    
                    // 字符编码分析
                    append("📝 字符编码分析:\n")
                    cleanedUsername.forEachIndexed { index, char ->
                        append("[$index]: '$char' (U+${char.code.toString(16).uppercase()})\n")
                    }
                    append("\n")
                    
                    // URL分析
                    val serverUrl = prefs.serverUrl.getValue()
                    append("🌐 URL分析:\n")
                    append("原始URL: '$serverUrl'\n")
                    append("包含dav.jianguoyun.com: ${serverUrl.contains("dav.jianguoyun.com")}\n")
                    append("以/dav/结尾: ${serverUrl.endsWith("/dav/")}\n")
                    append("以https://开头: ${serverUrl.startsWith("https://")}\n\n")
                    
                    // 密码分析
                    val password = prefs.password.getValue()
                    append("🔑 密码分析:\n")
                    append("密码长度: ${password.length}\n")
                    append("前3个字符: '${password.take(3)}'\n")
                    append("后3个字符: '${password.takeLast(3)}'\n")
                    append("是否包含数字: ${password.any { it.isDigit() }}\n")
                    append("是否包含字母: ${password.any { it.isLetter() }}\n")
                    append("是否包含特殊字符: ${password.any { !it.isLetterOrDigit() }}\n\n")
                    
                    // 常见问题检查
                    append("⚠️ 常见问题检查:\n")
                    val issues = mutableListOf<String>()
                    
                    if (!cleanedUsername.contains("@") || !cleanedUsername.contains(".")) {
                        issues.add("用户名不是完整邮箱地址")
                    }
                    if (password.length < 6) {
                        issues.add("密码长度可能太短")
                    }
                    if (!serverUrl.contains("dav.jianguoyun.com")) {
                        issues.add("服务器地址不是坚果云WebDAV地址")
                    }
                    if (!serverUrl.endsWith("/dav/")) {
                        issues.add("服务器地址不以/dav/结尾")
                    }
                    
                    if (issues.isEmpty()) {
                        append("✅ 配置格式检查通过\n")
                    } else {
                        append("❌ 发现以下问题:\n")
                        issues.forEach { issue ->
                            append("  • $issue\n")
                        }
                    }
                }
                
                dismissProgressDialog()
                
                AlertDialog.Builder(requireContext())
                    .setTitle("🔍 详细调试信息")
                    .setMessage(debugInfo)
                    .setPositiveButton("测试连接") { _, _ ->
                        performActualConnectionTest()
                    }
                    .setNegativeButton("关闭", null)
                    .show()
                    
            } catch (e: Exception) {
                dismissProgressDialog()
                AlertDialog.Builder(requireContext())
                    .setTitle("❌ 调试失败")
                    .setMessage("调试过程中发生错误: ${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }
    
    private fun showHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle("📖 WebDAV 同步使用说明")
            .setMessage("""
                🎯 功能说明
                WebDAV 同步可以将您的输入法数据备份到支持 WebDAV 协议的云存储服务，实现多设备间的数据同步。
                
                🔧 配置步骤
                1. 设置 WebDAV 服务器地址（如坚果云、Nextcloud 等）
                2. 输入用户名和密码
                3. 设置远程备份路径
                4. 测试连接确保配置正确
                5. 启用同步功能
                
                🌐 支持的服务
                • 坚果云 (https://dav.jianguoyun.com/dav/)
                • Nextcloud
                • ownCloud
                • InfiniCLOUD
                • 其他支持 WebDAV 的服务
                
                📦 同步内容
                • 所有应用设置
                • Rime 用户词库
                • 自定义短语
                • 剪贴板历史
                • 其他个人数据
                
                🔄 同步方式
                • 智能同步: 自动判断上传或下载
                • 手动上传: 仅上传本地数据
                • 手动下载: 从服务器下载（会覆盖本地）
                
                ⚠️ 注意事项
                • 下载备份会覆盖本地所有数据
                • 建议定期清理旧备份节省空间
                • WiFi 同步可节省流量
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun createProgressView(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER
            
            addView(ProgressBar(context).apply {
                isIndeterminate = true
            })
            
            addView(TextView(context).apply {
                text = "准备中..."
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 0)
            })
        }
    }
    
    private fun showProgressDialog(message: String) {
        val progressView = createProgressView()
        (progressView.getChildAt(1) as TextView).text = message
        
        progressDialog = AlertDialog.Builder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .create()
        
        progressDialog?.show()
    }
    
    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    private fun refreshScreen() {
        preferenceScreen.removeAll()
        onPreferenceUiCreated(preferenceScreen)
    }
}

