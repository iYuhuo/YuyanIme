package com.yuyan.imemodule.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.manager.UserDataManager
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * WebDAV 同步管理器
 * 负责用户数据的上传、下载和同步
 */
object WebDAVSyncManager {
    
    private val context: Context get() = Launcher.instance.context
    private val prefs get() = AppPrefs.getInstance().webdav
    
    private const val BACKUP_FILE_PREFIX = "yuyanIme_backup_"
    private const val BACKUP_FILE_EXTENSION = ".zip"
    
    /**
     * 同步结果
     */
    sealed class SyncResult {
        object Success : SyncResult()
        data class Error(val message: String, val exception: Throwable? = null) : SyncResult()
        object Cancelled : SyncResult()
    }
    
    /**
     * 同步操作类型
     */
    enum class SyncOperation {
        UPLOAD,   // 仅上传
        DOWNLOAD, // 仅下载
        AUTO      // 自动同步（智能选择）
    }
    
    /**
     * 创建 WebDAV 客户端
     */
    private fun createClient(): WebDAVClient? {
        val serverUrl = prefs.serverUrl.getValue()
        val username = prefs.username.getValue()
        val password = prefs.password.getValue()
        val ignoreSSLCert = prefs.ignoreSSLCert.getValue()
        
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            return null
        }
        
        return WebDAVClient(serverUrl, username, password, ignoreSSLCert)
    }
    
    /**
     * 测试 WebDAV 连接
     */
    suspend fun testConnection(): Result<Boolean> {
        val client = createClient() 
            ?: return Result.failure(Exception("WebDAV 配置不完整"))
        
        android.util.Log.d("WebDAVSyncManager", "Starting connection test...")
        android.util.Log.d("WebDAVSyncManager", "Server URL: ${prefs.serverUrl.getValue()}")
        android.util.Log.d("WebDAVSyncManager", "Username: ${prefs.username.getValue()}")
        android.util.Log.d("WebDAVSyncManager", "Remote path: ${prefs.remotePath.getValue()}")
        
        // 使用智能连接测试（自动检测服务器类型）
        android.util.Log.d("WebDAVSyncManager", "Using smart connection test...")
        val smartResult = client.testSmartConnection()
        if (smartResult.isSuccess) {
            android.util.Log.d("WebDAVSyncManager", "Smart connection test successful")
        } else {
            android.util.Log.w("WebDAVSyncManager", "Smart connection test failed: ${smartResult.exceptionOrNull()?.message}")
            return smartResult
        }
        
        // 非坚果云直接测试
        val result = client.testConnection()
        android.util.Log.d("WebDAVSyncManager", "Connection test result: ${result.isSuccess}")
        return result
    }
    
    /**
     * 检查网络连接
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * 检查是否为 WiFi 连接
     */
    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * 检查同步条件
     */
    private fun checkSyncConditions(): String? {
        if (!prefs.enabled.getValue()) {
            return "WebDAV 同步未启用"
        }
        
        if (!isNetworkAvailable()) {
            return "网络不可用"
        }
        
        if (prefs.syncOnWifiOnly.getValue() && !isWifiConnected()) {
            return "仅允许在 WiFi 下同步"
        }
        
        return null
    }
    
    /**
     * 执行同步操作
     */
    suspend fun sync(
        operation: SyncOperation = SyncOperation.AUTO,
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            // 检查同步条件
            val errorMsg = checkSyncConditions()
            if (errorMsg != null) {
                return@withContext SyncResult.Error(errorMsg)
            }
            
            val client = createClient() 
                ?: return@withContext SyncResult.Error("WebDAV 配置不完整")
            
            onProgress?.invoke("正在连接 WebDAV 服务器...")
            
            // 测试连接
            val testResult = client.testConnection()
            if (testResult.isFailure) {
                return@withContext SyncResult.Error(
                    "连接失败: ${testResult.exceptionOrNull()?.message}",
                    testResult.exceptionOrNull()
                )
            }
            
            val remotePath = prefs.remotePath.getValue()
            
            // 根据操作类型执行不同的同步逻辑
            when (operation) {
                SyncOperation.UPLOAD -> {
                    uploadBackup(client, remotePath, onProgress)
                }
                SyncOperation.DOWNLOAD -> {
                    downloadBackup(client, remotePath, onProgress)
                }
                SyncOperation.AUTO -> {
                    autoSync(client, remotePath, onProgress)
                }
            }
            
            // 更新同步时间
            prefs.lastSyncTime.setValue(System.currentTimeMillis())
            prefs.lastSyncSuccess.setValue(true)
            
            SyncResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            prefs.lastSyncSuccess.setValue(false)
            SyncResult.Error("同步失败: ${e.message}", e)
        }
    }
    
    /**
     * 上传备份到 WebDAV - 直接使用现有的导出功能
     */
    private suspend fun uploadBackup(
        client: WebDAVClient,
        remotePath: String,
        onProgress: ((String) -> Unit)?
    ) {
        onProgress?.invoke("正在导出用户数据...")
        
        // 直接使用UserDataManager的导出功能（与OtherSettingsFragment相同）
        val outputStream = ByteArrayOutputStream()
        val exportResult = UserDataManager.export(outputStream)
        
        if (exportResult.isFailure) {
            throw Exception("导出用户数据失败: ${exportResult.exceptionOrNull()?.message}")
        }
        
        val backupData = outputStream.toByteArray()
        val fileName = generateBackupFileName()
        
        onProgress?.invoke("正在上传备份文件...")
        
        // 上传到 WebDAV
        val uploadResult = client.uploadFile(
            ByteArrayInputStream(backupData),
            remotePath,
            fileName
        )
        
        if (uploadResult.isFailure) {
            throw Exception("上传失败: ${uploadResult.exceptionOrNull()?.message}")
        }
        
        onProgress?.invoke("上传完成！")
    }
    
    /**
     * 从 WebDAV 下载备份 - 下载文件然后直接调用导入功能
     */
    private suspend fun downloadBackup(
        client: WebDAVClient,
        remotePath: String,
        onProgress: ((String) -> Unit)?
    ) {
        onProgress?.invoke("正在查找远程备份...")
        
        // 获取最新的备份文件
        val latestFile = getLatestBackupFile(client, remotePath)
            ?: throw Exception("远程没有找到备份文件")
        
        onProgress?.invoke("正在下载备份文件...")
        
        // 下载备份文件
        val downloadResult = client.downloadFile(remotePath, latestFile.name)
        
        if (downloadResult.isFailure) {
            throw Exception("下载失败: ${downloadResult.exceptionOrNull()?.message}")
        }
        
        val backupData = downloadResult.getOrThrow()
        
        onProgress?.invoke("正在导入用户数据...")
        
        // 直接使用UserDataManager的导入功能（与OtherSettingsFragment完全相同）
        val importResult = UserDataManager.import(ByteArrayInputStream(backupData))
        
        if (importResult.isFailure) {
            throw Exception("导入用户数据失败: ${importResult.exceptionOrNull()?.message}")
        }
        
        onProgress?.invoke("下载完成！数据已导入")
    }
    
    /**
     * 自动同步（智能判断上传还是下载）
     */
    private suspend fun autoSync(
        client: WebDAVClient,
        remotePath: String,
        onProgress: ((String) -> Unit)?
    ) {
        onProgress?.invoke("正在检查远程备份...")
        
        // 获取最新的远程备份文件
        val latestRemoteFile = getLatestBackupFile(client, remotePath)
        val lastSyncTime = prefs.lastSyncTime.getValue()
        
        when {
            // 远程没有备份，上传
            latestRemoteFile == null -> {
                onProgress?.invoke("远程无备份，开始上传...")
                uploadBackup(client, remotePath, onProgress)
            }
            // 本地从未同步过，下载
            lastSyncTime == 0L -> {
                onProgress?.invoke("本地无同步记录，开始下载...")
                downloadBackup(client, remotePath, onProgress)
            }
            // 远程文件更新，下载
            latestRemoteFile.modified > lastSyncTime -> {
                onProgress?.invoke("远程文件较新，开始下载...")
                downloadBackup(client, remotePath, onProgress)
            }
            // 本地文件更新，上传
            else -> {
                onProgress?.invoke("本地文件较新，开始上传...")
                uploadBackup(client, remotePath, onProgress)
            }
        }
    }
    
    
    /**
     * 获取最新的备份文件
     */
    private suspend fun getLatestBackupFile(
        client: WebDAVClient,
        remotePath: String
    ): WebDAVClient.WebDAVFile? {
        val filesResult = client.listFiles(remotePath)
        
        if (filesResult.isFailure) {
            return null
        }
        
        val files = filesResult.getOrThrow()
        
        return files
            .filter { it.name.startsWith(BACKUP_FILE_PREFIX) && it.name.endsWith(BACKUP_FILE_EXTENSION) }
            .maxByOrNull { it.modified }
    }
    
    /**
     * 生成备份文件名
     */
    private fun generateBackupFileName(): String {
        val timestamp = System.currentTimeMillis()
        val dateTime = TimeUtils.iso8601UTCDateTime(timestamp).replace(":", "-")
        return "$BACKUP_FILE_PREFIX$dateTime$BACKUP_FILE_EXTENSION"
    }
    
    /**
     * 获取上次同步时间的可读格式
     */
    fun getLastSyncTimeFormatted(): String {
        val lastSyncTime = prefs.lastSyncTime.getValue()
        if (lastSyncTime == 0L) {
            return "从未同步"
        }
        
        return TimeUtils.iso8601UTCDateTime(lastSyncTime)
    }
    
    /**
     * 清理旧的备份文件（保留最近的 N 个）
     */
    suspend fun cleanupOldBackups(keepCount: Int = 5): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val client = createClient() 
                ?: return@withContext Result.failure(Exception("WebDAV 配置不完整"))
            
            val remotePath = prefs.remotePath.getValue()
            val filesResult = client.listFiles(remotePath)
            
            if (filesResult.isFailure) {
                return@withContext Result.failure(filesResult.exceptionOrNull()!!)
            }
            
            val files = filesResult.getOrThrow()
                .filter { it.name.startsWith(BACKUP_FILE_PREFIX) && it.name.endsWith(BACKUP_FILE_EXTENSION) }
                .sortedByDescending { it.modified }
            
            if (files.size <= keepCount) {
                return@withContext Result.success(0)
            }
            
            val filesToDelete = files.drop(keepCount)
            var deletedCount = 0
            
            filesToDelete.forEach { file ->
                val deleteResult = client.deleteFile(remotePath, file.name)
                if (deleteResult.isSuccess) {
                    deletedCount++
                }
            }
            
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

