package com.yuyan.imemodule.sync

import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * WebDAV 客户端封装
 * 提供文件上传、下载、删除等功能
 */
class WebDAVClient(
    serverUrl: String,
    username: String,
    password: String,
    private val ignoreSSLCert: Boolean = false
) {
    
    // 智能标准化处理后的配置
    // 注意：必须先标准化serverUrl，再用标准化后的serverUrl来处理username
    private val serverUrl: String = normalizeServerUrl(serverUrl)
    private val username: String = normalizeUsername(username, this.serverUrl)  // 使用已标准化的serverUrl
    private val password: String = password.trim()
    
    companion object {
        /**
         * 智能识别和转换各种编码的特殊符号
         */
        fun normalizeSymbols(input: String): String {
            var result = input
            
            // 替换各种编码的 @ 符号为标准 @
            val atSymbols = listOf("＠", "﹫", "⒜", "﹫", "＠")
            atSymbols.forEach { symbol ->
                result = result.replace(symbol, "@")
            }
            
            // 替换反斜杠为正斜杠
            result = result.replace("\\", "/")
            
            // 替换各种编码的点号为标准点号
            val dotSymbols = listOf("。", "．", "·")
            dotSymbols.forEach { symbol ->
                result = result.replace(symbol, ".")
            }
            
            // 替换各种编码的冒号为标准冒号
            val colonSymbols = listOf("：", "﹕")
            colonSymbols.forEach { symbol ->
                result = result.replace(symbol, ":")
            }
            
            // 移除所有不可见字符和零宽字符
            result = result.replace(Regex("[\u200B-\u200D\uFEFF]"), "")
            
            return result
        }
        
        /**
         * 智能标准化服务器URL
         */
        fun normalizeServerUrl(url: String): String {
            var normalized = url.trim()
            
            // 先进行符号标准化
            normalized = normalizeSymbols(normalized)
            
            // 移除多余的空格
            normalized = normalized.replace(Regex("\\s+"), "")
            
            // 坚果云特殊处理
            if (normalized.contains("jianguoyun.com", ignoreCase = true)) {
                // 确保使用HTTPS
                if (!normalized.startsWith("http://", ignoreCase = true) && 
                    !normalized.startsWith("https://", ignoreCase = true)) {
                    normalized = "https://$normalized"
                }
                // 替换http为https
                if (normalized.startsWith("http://", ignoreCase = true)) {
                    normalized = "https://" + normalized.substring(7)
                }
                // 确保使用dav子域名
                if (!normalized.contains("dav.jianguoyun.com", ignoreCase = true)) {
                    normalized = normalized.replace(Regex("jianguoyun\\.com", RegexOption.IGNORE_CASE), "dav.jianguoyun.com")
                }
                // 确保以/dav/结尾
                normalized = normalized.trimEnd('/')
                if (!normalized.endsWith("/dav", ignoreCase = true)) {
                    normalized += "/dav"
                }
                normalized += "/"
            } else {
                // 标准WebDAV服务器处理
                // 如果没有协议,默认使用https
                if (!normalized.startsWith("http://", ignoreCase = true) && 
                    !normalized.startsWith("https://", ignoreCase = true)) {
                    normalized = "https://$normalized"
                }
                
                // 确保以/结尾
                if (!normalized.endsWith("/")) {
                    normalized += "/"
                }
            }
            
            // 移除重复的斜杠,但保留协议部分的 ://
            normalized = normalized.replace(Regex("(?<!:)/+"), "/")
            
            android.util.Log.d("WebDAVClient", "URL标准化: $url -> $normalized")
            return normalized
        }
        
        /**
         * 智能标准化用户名
         */
        fun normalizeUsername(username: String, serverUrl: String): String {
            var normalized = username.trim()
            
            // 先进行符号标准化
            normalized = normalizeSymbols(normalized)
            
            // 移除所有空格
            normalized = normalized.replace(Regex("\\s+"), "")
            
            // 坚果云特殊处理
            if (serverUrl.contains("jianguoyun.com", ignoreCase = true)) {
                // 转换为小写(邮箱地址通常不区分大小写)
                normalized = normalized.lowercase()
                
                // 验证邮箱格式
                if (normalized.isNotEmpty() && !normalized.contains("@")) {
                    android.util.Log.w("WebDAVClient", "坚果云用户名格式警告: 缺少@符号")
                }
                if (normalized.isNotEmpty() && !normalized.contains(".")) {
                    android.util.Log.w("WebDAVClient", "坚果云用户名格式警告: 缺少.符号")
                }
            }
            
            android.util.Log.d("WebDAVClient", "用户名标准化: '$username' -> '$normalized'")
            return normalized
        }
    }
    
    /**
     * 创建基础的 OkHttpClient.Builder（公共配置）
     */
    private fun createBaseClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES)) // 移动应用：最多5个连接，空闲连接保持5分钟
    }

    /**
     * 创建标准的OkHttpClient（带连接池管理）
     */
    private fun createStandardOkHttpClient(): OkHttpClient {
        return createBaseClientBuilder().build()
    }

    /**
     * 创建SSL不安全的OkHttpClient（用于自签名证书）
     */
    private fun createUnsafeOkHttpClient(): OkHttpClient {
        try {
            // 创建信任所有证书的TrustManager
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // 创建SSLContext
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // 创建不验证主机名的HostnameVerifier
            val hostnameVerifier = HostnameVerifier { _, _ -> true }

            return createBaseClientBuilder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier(hostnameVerifier)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Failed to create unsafe SSL client", e)
            // 如果创建失败，返回标准客户端
            return createStandardOkHttpClient()
        }
    }
    
    private val sardine: Sardine by lazy {
        val okHttpClient = if (ignoreSSLCert) {
            if (com.yuyan.imemodule.BuildConfig.DEBUG) {
                android.util.Log.d("WebDAVClient", "Using unsafe SSL client (ignoring certificates)")
            }
            createUnsafeOkHttpClient()
        } else {
            if (com.yuyan.imemodule.BuildConfig.DEBUG) {
                android.util.Log.d("WebDAVClient", "Using standard SSL client")
            }
            createStandardOkHttpClient()
        }
            .newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                
                // 只在 DEBUG 模式下记录详细日志
                if (com.yuyan.imemodule.BuildConfig.DEBUG) {
                    android.util.Log.d("WebDAVClient", "Request: ${request.method} ${request.url}")
                }

                try {
                    val response = chain.proceed(request)
                    
                    // 只记录错误响应
                    if (response.code >= 400) {
                        android.util.Log.w("WebDAVClient", "Response ${response.code} for ${request.url}")
                        
                        if (response.code == 401 && serverUrl.contains("jianguoyun.com")) {
                            android.util.Log.w("WebDAVClient", "坚果云认证失败：请检查应用密码和邮箱格式")
                        }
                    }

                    response
                } catch (e: Exception) {
                    // 只记录错误
                    android.util.Log.e("WebDAVClient", "Request failed: ${e.javaClass.simpleName} - ${e.message}")
                    throw e
                }
            }
            .build()

        OkHttpSardine(okHttpClient).apply {
            // username和password已经在构造函数中标准化，直接使用
            if (com.yuyan.imemodule.BuildConfig.DEBUG) {
                android.util.Log.d("WebDAVClient", "Setting credentials for: $serverUrl")
                android.util.Log.d("WebDAVClient", "Username: '$username', Password length: ${password.length}")
            }
            
            setCredentials(username, password)
        }
    }
    
    /**
     * 确保认证信息是最新的（username和password已在构造函数中标准化）
     */
    private fun ensureCredentials() {
        android.util.Log.d("WebDAVClient", "Ensuring credentials are set")
        sardine.setCredentials(username, password)
    }
    
    /**
     * 统一的智能连接测试（自动检测服务器类型和协议，带重试机制）
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "=== 开始智能连接测试 ===")
            android.util.Log.d("WebDAVClient", "服务器URL: $serverUrl")
            android.util.Log.d("WebDAVClient", "用户名: $username")
            android.util.Log.d("WebDAVClient", "密码长度: ${password.length}")
            
            // 自动检测服务器类型和协议
            val isJianguoyun = serverUrl.contains("jianguoyun.com", ignoreCase = true)
            val isHttps = serverUrl.startsWith("https://", ignoreCase = true)
            val isHttp = serverUrl.startsWith("http://", ignoreCase = true)
            
            android.util.Log.d("WebDAVClient", "服务器类型: ${if (isJianguoyun) "坚果云" else "标准WebDAV"}")
            android.util.Log.d("WebDAVClient", "协议类型: ${when {
                isHttps -> "HTTPS"
                isHttp -> "HTTP"
                else -> "未知"
            }}")
            android.util.Log.d("WebDAVClient", "忽略SSL设置: $ignoreSSLCert")
            
            // 准备认证信息
            val credential = Credentials.basic(username, password)
            
            // 准备WebDAV PROPFIND请求
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop>
                        <D:displayname/>
                        <D:resourcetype/>
                    </D:prop>
                </D:propfind>
            """.trimIndent()
            
            val request = Request.Builder()
                .url(serverUrl)
                .header("Authorization", credential)
                .header("User-Agent", "YuYanIME-WebDAV/1.0")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Depth", "0")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            
            // 根据协议类型选择测试策略
            return@withContext when {
                isHttp -> {
                    // HTTP连接：直接使用标准客户端，无需SSL处理
                    android.util.Log.d("WebDAVClient", "检测到HTTP协议，使用标准客户端")
                    testWithClient(createStandardOkHttpClient(), request, isJianguoyun, "HTTP")
                }
                isHttps -> {
                    // HTTPS连接：根据ignoreSSLCert设置选择策略
                    if (ignoreSSLCert) {
                        // 用户明确要求忽略SSL证书
                        android.util.Log.d("WebDAVClient", "检测到HTTPS协议，用户设置忽略SSL，使用不安全客户端")
                        testWithClient(createUnsafeOkHttpClient(), request, isJianguoyun, "HTTPS(忽略证书)")
                    } else {
                        // 先尝试标准SSL客户端
                        android.util.Log.d("WebDAVClient", "检测到HTTPS协议，先尝试标准SSL验证")
                        val standardResult = testWithClient(createStandardOkHttpClient(), request, isJianguoyun, "HTTPS", maxRetries = 1)
                        
                        if (standardResult.isSuccess) {
                            android.util.Log.d("WebDAVClient", "✅ 标准SSL验证成功")
                            standardResult
                        } else {
                            val error = standardResult.exceptionOrNull()
                            // 如果是SSL证书错误，自动尝试不安全的客户端
                            if (error?.message?.contains("cert", ignoreCase = true) == true ||
                                error?.message?.contains("SSL", ignoreCase = true) == true ||
                                error?.message?.contains("trust", ignoreCase = true) == true) {
                                
                                android.util.Log.w("WebDAVClient", "标准SSL验证失败（证书问题），自动尝试忽略证书验证")
                                android.util.Log.w("WebDAVClient", "建议：如果测试成功，可以启用'忽略SSL证书'选项")
                                
                                val unsafeResult = testWithClient(createUnsafeOkHttpClient(), request, isJianguoyun, "HTTPS(自动忽略证书)")
                                
                                if (unsafeResult.isSuccess) {
                                    android.util.Log.d("WebDAVClient", "✅ 忽略证书后连接成功")
                                    // 返回成功，但附带提示信息
                                    Result.success(true)
                                } else {
                                    android.util.Log.e("WebDAVClient", "忽略证书后仍然失败")
                                    unsafeResult
                                }
                            } else {
                                // 不是SSL问题，直接返回原始错误
                                android.util.Log.e("WebDAVClient", "连接失败（非SSL问题）")
                                standardResult
                            }
                        }
                    }
                }
                else -> {
                    // 未知协议
                    android.util.Log.e("WebDAVClient", "未知的协议类型")
                    Result.failure(Exception("未知的协议类型: 请确保URL以http://或https://开头"))
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "连接测试异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 使用指定的客户端进行连接测试
     */
    private suspend fun testWithClient(
        client: OkHttpClient,
        request: Request,
        isJianguoyun: Boolean,
        protocol: String,
        maxRetries: Int = 3
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            var response: okhttp3.Response? = null
            try {
                android.util.Log.d("WebDAVClient", "[$protocol] 尝试连接 (第${attempt}/${maxRetries}次)...")
                
                response = client.newCall(request).execute()
                val code = response.code
                
                android.util.Log.d("WebDAVClient", "[$protocol] 响应代码: $code")
                
                when (code) {
                    in 200..299 -> {
                        android.util.Log.d("WebDAVClient", "[$protocol] ✅ 连接测试成功")
                        return@withContext Result.success(true)
                    }
                    401 -> {
                        val errorMsg = if (isJianguoyun) {
                            "认证失败: 请检查用户名(邮箱)和应用密码是否正确"
                        } else {
                            "认证失败: 用户名或密码错误"
                        }
                        android.util.Log.e("WebDAVClient", "[$protocol] $errorMsg")
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                    403 -> {
                        android.util.Log.e("WebDAVClient", "[$protocol] 权限被拒绝")
                        return@withContext Result.failure(Exception("权限不足: 请检查用户权限设置"))
                    }
                    404 -> {
                        android.util.Log.e("WebDAVClient", "[$protocol] 路径不存在")
                        return@withContext Result.failure(Exception("服务器路径不存在: 请检查URL是否正确"))
                    }
                    else -> {
                        android.util.Log.w("WebDAVClient", "[$protocol] 意外的响应代码: $code")
                        lastException = Exception("服务器返回意外响应: $code")
                    }
                }
                
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("WebDAVClient", "[$protocol] 第${attempt}次尝试失败: ${e.javaClass.simpleName} - ${e.message}")
                
                // 检查是否值得重试
                if (attempt < maxRetries) {
                    val delay = attempt * 1000L
                    android.util.Log.d("WebDAVClient", "[$protocol] 等待${delay}ms后重试...")
                    kotlinx.coroutines.delay(delay)
                }
            } finally {
                // 确保响应总是被关闭
                response?.close()
            }
        }
        
        // 所有重试都失败
        android.util.Log.e("WebDAVClient", "[$protocol] ❌ 连接测试失败: 已重试${maxRetries}次")
        
        val errorMessage = when {
            lastException?.message?.contains("cert", ignoreCase = true) == true || 
            lastException?.message?.contains("SSL", ignoreCase = true) == true ||
            lastException?.message?.contains("trust", ignoreCase = true) == true -> 
                "SSL证书验证失败: ${lastException?.message}"
            lastException?.message?.contains("timeout", ignoreCase = true) == true -> 
                "连接超时: 请检查网络连接和服务器地址"
            lastException?.message?.contains("UnknownHost", ignoreCase = true) == true -> 
                "无法解析服务器地址: 请检查URL是否正确"
            isJianguoyun -> 
                "连接失败: ${lastException?.message ?: "请检查服务器地址、用户名(邮箱)和应用密码"}"
            else -> 
                "连接失败: ${lastException?.message ?: "请检查服务器配置"}"
        }
        
        Result.failure(Exception(errorMessage, lastException))
    }
    
    
    /**
     * 检查WebDAV目录权限
     */
    suspend fun checkDirectoryPermissions(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "=== Checking Directory Permissions ===")
            android.util.Log.d("WebDAVClient", "Checking permissions for: $remotePath")
            
            // serverUrl和username已经在构造函数中标准化了,直接使用
            val credential = Credentials.basic(username, password)
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    android.util.Log.d("WebDAVClient", "Permission check request: ${request.method} ${request.url}")
                    
                    val response = chain.proceed(request)
                    android.util.Log.d("WebDAVClient", "Permission check response: ${response.code}")
                    
                    if (response.code == 403) {
                        android.util.Log.e("WebDAVClient", "403 Forbidden - Permission denied")
                        val body = response.body?.string()
                        android.util.Log.e("WebDAVClient", "403 Response body: $body")
                    }
                    
                    response
                }
                .build()
            
            // 测试目录访问权限（使用与buildDirectoryPath相同的逻辑）
            val directoryUrl = buildDirectoryPath(remotePath)
            
            android.util.Log.d("WebDAVClient", "Testing directory access: $directoryUrl")
            
            // 使用PROPFIND检查目录权限
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop>
                        <D:displayname/>
                        <D:resourcetype/>
                        <D:getlastmodified/>
                    </D:prop>
                </D:propfind>
            """.trimIndent()
            
            val request = Request.Builder()
                .url(directoryUrl)
                .header("Authorization", credential)
                .header("User-Agent", "YuYanIME-WebDAV/1.0")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Depth", "1")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            android.util.Log.d("WebDAVClient", "Directory permission check response: ${response.code}")
            
            when (response.code) {
                200, 207 -> {
                    android.util.Log.d("WebDAVClient", "Directory access successful")
                    response.close()
                    return@withContext Result.success(true)
                }
                401 -> {
                    android.util.Log.e("WebDAVClient", "401 Unauthorized - Authentication failed")
                    response.close()
                    return@withContext Result.failure(Exception("Authentication failed (401)"))
                }
                403 -> {
                    android.util.Log.e("WebDAVClient", "403 Forbidden - Permission denied")
                    val errorBody = response.body?.string()
                    android.util.Log.e("WebDAVClient", "403 Error body: $errorBody")
                    response.close()
                    return@withContext Result.failure(Exception("Permission denied (403) - User does not have write access to directory"))
                }
                404 -> {
                    android.util.Log.w("WebDAVClient", "404 Not Found - Directory does not exist")
                    response.close()
                    return@withContext Result.failure(Exception("Directory not found (404) - Please check the remote path"))
                }
                else -> {
                    android.util.Log.e("WebDAVClient", "Unexpected response code: ${response.code}")
                    val errorBody = response.body?.string()
                    android.util.Log.e("WebDAVClient", "Error body: $errorBody")
                    response.close()
                    return@withContext Result.failure(Exception("Unexpected response: ${response.code}"))
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Directory permission check exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建目录（如果不存在）
     */
    suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fullPath = buildDirectoryPath(path)
            android.util.Log.d("WebDAVClient", "Creating directory: $fullPath")
            if (!sardine.exists(fullPath)) {
                sardine.createDirectory(fullPath)
                android.util.Log.d("WebDAVClient", "Directory created successfully")
            } else {
                android.util.Log.d("WebDAVClient", "Directory already exists")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Failed to create directory: $path", e)
            Result.failure(e)
        }
    }
    
    /**
     * 上传文件
     */
    suspend fun uploadFile(
        localStream: InputStream,
        remotePath: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 确保远程目录存在
            createDirectory(remotePath).getOrThrow()
            
            // 读取本地文件到内存
            val bytes = localStream.readBytes()
            val fullPath = buildFullPath(remotePath, fileName)
            
            android.util.Log.d("WebDAVClient", "Uploading file to: $fullPath")
            android.util.Log.d("WebDAVClient", "File size: ${bytes.size} bytes")
            
            // 上传文件
            sardine.put(fullPath, bytes, "application/zip")
            
            android.util.Log.d("WebDAVClient", "File uploaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Failed to upload file: $fileName", e)
            Result.failure(e)
        }
    }
    
    /**
     * 下载文件
     */
    suspend fun downloadFile(
        remotePath: String,
        fileName: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val fullPath = buildFullPath(remotePath, fileName)
            android.util.Log.d("WebDAVClient", "Downloading file from: $fullPath")
            
            val inputStream = sardine.get(fullPath)
            
            val outputStream = ByteArrayOutputStream()
            inputStream.use { input ->
                input.copyTo(outputStream)
            }
            
            val data = outputStream.toByteArray()
            android.util.Log.d("WebDAVClient", "File downloaded successfully, size: ${data.size} bytes")
            Result.success(data)
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Failed to download file: $fileName", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检查文件是否存在
     */
    suspend fun fileExists(remotePath: String, fileName: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                val fullPath = buildFullPath(remotePath, fileName)
                android.util.Log.d("WebDAVClient", "Checking if file exists: $fullPath")
                val exists = sardine.exists(fullPath)
                android.util.Log.d("WebDAVClient", "File exists: $exists")
                Result.success(exists)
            } catch (e: Exception) {
                android.util.Log.e("WebDAVClient", "Failed to check file existence: $fileName", e)
                Result.failure(e)
            }
        }
    
    /**
     * 获取文件修改时间
     */
    suspend fun getFileModifiedTime(remotePath: String, fileName: String): Result<Long> = 
        withContext(Dispatchers.IO) {
            try {
                val fullPath = buildFullPath(remotePath, fileName)
                android.util.Log.d("WebDAVClient", "Getting file modified time: $fullPath")
                val resources = sardine.list(fullPath)
                
                if (resources.isNotEmpty()) {
                    val modifiedTime = resources[0].modified?.time ?: 0L
                    android.util.Log.d("WebDAVClient", "File modified time: $modifiedTime")
                    Result.success(modifiedTime)
                } else {
                    android.util.Log.w("WebDAVClient", "File not found: $fileName")
                    Result.failure(Exception("文件不存在"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebDAVClient", "Failed to get file modified time: $fileName", e)
                Result.failure(e)
            }
        }
    
    /**
     * 删除文件
     */
    suspend fun deleteFile(remotePath: String, fileName: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val fullPath = buildFullPath(remotePath, fileName)
                android.util.Log.d("WebDAVClient", "Deleting file: $fullPath")
                sardine.delete(fullPath)
                android.util.Log.d("WebDAVClient", "File deleted successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("WebDAVClient", "Failed to delete file: $fileName", e)
                Result.failure(e)
            }
        }
    
    /**
     * 列出目录下的所有文件
     */
    suspend fun listFiles(remotePath: String): Result<List<WebDAVFile>> = 
        withContext(Dispatchers.IO) {
            try {
                val fullPath = buildDirectoryPath(remotePath)
                android.util.Log.d("WebDAVClient", "Listing files in: $fullPath")
                
                // 确保目录存在
                if (!sardine.exists(fullPath)) {
                    android.util.Log.w("WebDAVClient", "Directory does not exist: $remotePath")
                    return@withContext Result.success(emptyList())
                }
                
                val resources = sardine.list(fullPath)
                val files = resources
                    .filter { !it.isDirectory }
                    .map { resource ->
                        WebDAVFile(
                            name = resource.name,
                            size = resource.contentLength,
                            modified = resource.modified?.time ?: 0L,
                            path = resource.path
                        )
                    }
                
                android.util.Log.d("WebDAVClient", "Found ${files.size} files in directory")
                Result.success(files)
            } catch (e: Exception) {
                android.util.Log.e("WebDAVClient", "Failed to list files in: $remotePath", e)
                Result.failure(e)
            }
        }
    
    /**
     * 构建完整的文件路径
     */
    private fun buildFullPath(remotePath: String, fileName: String): String {
        val baseUrl = serverUrl.trimEnd('/')
        val path = remotePath.trimStart('/').trimEnd('/')
        val file = fileName.trimStart('/')
        
        return if (path.isEmpty()) {
            "$baseUrl/$file"
        } else {
            "$baseUrl/$path/$file"
        }
    }
    
    /**
     * 构建完整的目录路径
     */
    private fun buildDirectoryPath(remotePath: String): String {
        val baseUrl = serverUrl.trimEnd('/')
        val path = remotePath.trimStart('/').trimEnd('/')
        
        return if (path.isEmpty()) {
            baseUrl
        } else {
            "$baseUrl/$path"
        }
    }
    
    /**
     * WebDAV 文件信息
     */
    data class WebDAVFile(
        val name: String,
        val size: Long,
        val modified: Long,
        val path: String
    )
}

