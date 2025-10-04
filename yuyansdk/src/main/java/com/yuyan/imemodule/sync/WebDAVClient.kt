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
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    private val ignoreSSLCert: Boolean = false
) {
    
    /**
     * 创建标准的OkHttpClient（带连接池管理）
     */
    private fun createStandardOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES)) // 连接池：最多10个连接，空闲连接保持5分钟
            .build()
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

            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES)) // 连接池：最多10个连接，空闲连接保持5分钟
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
            android.util.Log.d("WebDAVClient", "Using unsafe SSL client (ignoring certificates)")
            createUnsafeOkHttpClient()
        } else {
            android.util.Log.d("WebDAVClient", "Using standard SSL client")
            createStandardOkHttpClient()
        }
            .newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                android.util.Log.d("WebDAVClient", "Request URL: ${request.url}")
                android.util.Log.d("WebDAVClient", "Request Method: ${request.method}")
                android.util.Log.d("WebDAVClient", "Request Headers: ${request.headers}")

                // 检查认证头
                val authHeader = request.header("Authorization")
                if (authHeader != null) {
                    android.util.Log.d("WebDAVClient", "Auth header present: ${authHeader.take(20)}...")
                } else {
                    android.util.Log.w("WebDAVClient", "No Authorization header found!")
                }

                try {
                val response = chain.proceed(request)
                android.util.Log.d("WebDAVClient", "Response Code: ${response.code}")
                android.util.Log.d("WebDAVClient", "Response Headers: ${response.headers}")

                // 如果是 401，记录关键信息
                if (response.code == 401) {
                    android.util.Log.e("WebDAVClient", "401 Unauthorized - Check credentials for ${request.url}")

                    // 只在坚果云情况下提供简洁提示
                    if (serverUrl.contains("jianguoyun.com")) {
                        android.util.Log.w("WebDAVClient", "坚果云认证失败：请检查应用密码和邮箱格式")
                    }
                }

                response
                } catch (e: Exception) {
                    // 只记录关键错误信息，避免过多日志
                    android.util.Log.e("WebDAVClient", "Request failed: ${e.javaClass.simpleName} - ${e.message}")

                    // 对于连接问题，提供简洁的诊断信息
                    if (e.message?.contains("closed", ignoreCase = true) == true) {
                        android.util.Log.w("WebDAVClient", "Connection closed - check network stability")
                    }

                    throw e
                }
            }
            .build()

        OkHttpSardine(okHttpClient).apply {
            // 坚果云特殊处理
            val cleanUsername = if (serverUrl.contains("jianguoyun.com")) {
                // 坚果云要求用户名必须是完整的邮箱地址，去除所有空格
                username.trim().replace("\\s+".toRegex(), "")
            } else {
                username.trim()
            }

            android.util.Log.d("WebDAVClient", "Setting credentials")
            android.util.Log.d("WebDAVClient", "Original username: '$username'")
            android.util.Log.d("WebDAVClient", "Cleaned username: '$cleanUsername'")
            android.util.Log.d("WebDAVClient", "Password length: ${password.length}")
            android.util.Log.d("WebDAVClient", "Server URL: $serverUrl")

            // 验证坚果云用户名格式
            if (serverUrl.contains("jianguoyun.com")) {
                val hasAtSymbol = cleanUsername.contains("@")
                val hasDotSymbol = cleanUsername.contains(".")
                if (!hasAtSymbol || !hasDotSymbol) {
                    android.util.Log.w("WebDAVClient", "警告：坚果云用户名格式可能不正确")
                    android.util.Log.w("WebDAVClient", "应该是完整邮箱地址，如: user@example.com")
                }
            }

            setCredentials(cleanUsername, password)
        }
    }
    
    /**
     * 确保认证信息是最新的
     */
    private fun ensureCredentials() {
        val cleanUsername = if (serverUrl.contains("jianguoyun.com")) {
            // 坚果云要求用户名必须是完整的邮箱地址，去除所有空格
            username.trim().replace("\\s+".toRegex(), "")
        } else {
            username.trim()
        }
        
        android.util.Log.d("WebDAVClient", "Ensuring credentials are set")
        android.util.Log.d("WebDAVClient", "Original username: '$username'")
        android.util.Log.d("WebDAVClient", "Cleaned username: '$cleanUsername'")
        android.util.Log.d("WebDAVClient", "Password length: ${password.length}")
        
        sardine.setCredentials(cleanUsername, password)
    }
    
    /**
     * 测试连接是否可用（带重试机制）
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
        try {
                android.util.Log.d("WebDAVClient", "Testing connection to: $serverUrl (attempt $attempt/$maxRetries)")
            android.util.Log.d("WebDAVClient", "Username: $username")
            android.util.Log.d("WebDAVClient", "Password length: ${password.length}")
                
                // 如果是坚果云，先尝试多种认证方式
                if (serverUrl.contains("jianguoyun.com")) {
                    android.util.Log.d("WebDAVClient", "Testing Jianguoyun with multiple auth methods...")
                    
                    // 方法1: 简单HTTP测试
                    val simpleResult = testSimpleHttp()
                    if (simpleResult.isSuccess) {
                        android.util.Log.d("WebDAVClient", "Simple HTTP test successful, trying full WebDAV...")
                    } else {
                        android.util.Log.w("WebDAVClient", "Simple HTTP test failed: ${simpleResult.exceptionOrNull()?.message}")
                    }
                    
                    // 方法2: 尝试不同的用户名格式
                    val alternativeResult = testAlternativeAuth()
                    if (alternativeResult.isSuccess) {
                        android.util.Log.d("WebDAVClient", "Alternative auth successful")
                        return@withContext Result.success(true)
                    } else {
                        android.util.Log.w("WebDAVClient", "Alternative auth failed: ${alternativeResult.exceptionOrNull()?.message}")
                    }
                }
            
            // 确保认证信息是最新的
            ensureCredentials()
            
            // 使用用户配置的服务器URL进行测试
            val testUrl = normalizeUrl(serverUrl)
            android.util.Log.d("WebDAVClient", "Actual test URL: $testUrl")
            
            // 测试服务器访问
            val exists = sardine.exists(testUrl)
            android.util.Log.d("WebDAVClient", "Server directory exists: $exists")
            
            // 尝试列出目录内容
            try {
                android.util.Log.d("WebDAVClient", "Attempting to list directory contents...")
                val resources = sardine.list(testUrl)
                android.util.Log.d("WebDAVClient", "Directory contents: ${resources.size} items")
                resources.take(3).forEach { resource ->
                    android.util.Log.d("WebDAVClient", "  - ${resource.name} (${if (resource.isDirectory) "dir" else "file"})")
                }
            } catch (e: Exception) {
                android.util.Log.w("WebDAVClient", "Failed to list directory contents", e)
                // 即使无法列出内容，如果exists返回true，也认为连接成功
            }
            
                android.util.Log.d("WebDAVClient", "Connection test successful on attempt $attempt")
                return@withContext Result.success(true)
                
        } catch (e: Exception) {
                lastException = e
                android.util.Log.e("WebDAVClient", "Connection test failed on attempt $attempt", e)
            android.util.Log.e("WebDAVClient", "Error message: ${e.message}")
            android.util.Log.e("WebDAVClient", "Error type: ${e.javaClass.simpleName}")
                
                // 检查是否是连接关闭错误
                if (e.message?.contains("closed", ignoreCase = true) == true) {
                    android.util.Log.e("WebDAVClient", "连接被关闭 - 尝试重试")
                    if (attempt < maxRetries) {
                        android.util.Log.d("WebDAVClient", "等待 ${attempt * 2} 秒后重试...")
                        kotlinx.coroutines.delay(attempt * 2000L) // 递增延迟
                        continue
                    }
                }
                
                // 如果不是连接关闭错误，或者已达到最大重试次数，则失败
                if (attempt == maxRetries) {
                    break
                }
                
                // 其他错误也进行重试
                android.util.Log.d("WebDAVClient", "等待 ${attempt * 2} 秒后重试...")
                kotlinx.coroutines.delay(attempt * 2000L)
            }
        }
        
        // 所有重试都失败了
        android.util.Log.e("WebDAVClient", "All connection attempts failed after $maxRetries retries")
            
            // 检查是否是坚果云特定的错误
            if (serverUrl.contains("jianguoyun.com")) {
                android.util.Log.e("WebDAVClient", "坚果云连接失败，请检查：")
                android.util.Log.e("WebDAVClient", "1. 用户名是否为完整邮箱地址")
                android.util.Log.e("WebDAVClient", "2. 应用密码是否有读写权限")
                android.util.Log.e("WebDAVClient", "3. 服务器地址是否为 https://dav.jianguoyun.com/dav/")
            android.util.Log.e("WebDAVClient", "4. 网络连接是否稳定")
            android.util.Log.e("WebDAVClient", "5. 是否被防火墙阻止")
        }
        
        Result.failure(lastException ?: Exception("Connection failed after $maxRetries attempts"))
    }
    
    /**
     * 尝试不同的认证方式（坚果云专用）
     */
    private suspend fun testAlternativeAuth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!serverUrl.contains("jianguoyun.com")) {
                return@withContext Result.success(true)
            }
            
            android.util.Log.d("WebDAVClient", "=== Testing Alternative Auth Methods ===")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            
            val testUrl = normalizeUrl(serverUrl)
            
            // 尝试不同的用户名格式
            val usernameVariants = listOf(
                username.trim(), // 原始用户名
                username.trim().replace("\\s+".toRegex(), ""), // 去除所有空格
                username.trim().lowercase(), // 小写
                username.trim().replace("\\s+".toRegex(), "").lowercase() // 去除空格+小写
            ).distinct()
            
            android.util.Log.d("WebDAVClient", "Testing ${usernameVariants.size} username variants:")
            usernameVariants.forEachIndexed { index, variant ->
                android.util.Log.d("WebDAVClient", "  [$index]: '$variant'")
            }
            
            for ((index, usernameVariant) in usernameVariants.withIndex()) {
                try {
                    android.util.Log.d("WebDAVClient", "Testing variant $index: '$usernameVariant'")
                    
                    val credential = Credentials.basic(usernameVariant, password)
                    val request = Request.Builder()
                        .url(testUrl)
                        .header("Authorization", credential)
                        .header("User-Agent", "YuYanIME-WebDAV/1.0")
                        .method("PROPFIND", null)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    android.util.Log.d("WebDAVClient", "Variant $index response: ${response.code}")
                    
                    if (response.isSuccessful) {
                        android.util.Log.d("WebDAVClient", "Success with variant $index: '$usernameVariant'")
                        response.close()
                        return@withContext Result.success(true)
                    }
                    
                    response.close()
                } catch (e: Exception) {
                    android.util.Log.w("WebDAVClient", "Variant $index failed: ${e.message}")
                }
            }
            
            android.util.Log.e("WebDAVClient", "All authentication variants failed")
            Result.failure(Exception("All authentication methods failed"))
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Alternative auth test failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检查WebDAV目录权限
     */
    suspend fun checkDirectoryPermissions(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "=== Checking Directory Permissions ===")
            android.util.Log.d("WebDAVClient", "Checking permissions for: $remotePath")
            
            val testUrl = normalizeUrl(serverUrl)
            val cleanUsername = username.trim().replace("\\s+".toRegex(), "")
            val credential = Credentials.basic(cleanUsername, password)
            
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
            
            // 测试目录访问权限
            val directoryUrl = if (remotePath.endsWith("/")) {
                "$testUrl$remotePath"
            } else {
                "$testUrl$remotePath/"
            }
            
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
     * 智能连接测试 - 自动检测服务器类型并使用相应的测试模式
     */
    suspend fun testSmartConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "=== Smart Connection Test ===")
            
            val testUrl = normalizeUrl(serverUrl)
            android.util.Log.d("WebDAVClient", "Testing connection to: $testUrl")
            
            // 自动检测服务器类型
            val isJianguoyun = testUrl.contains("jianguoyun.com")
            val isHttp = testUrl.startsWith("http://")
            val isHttps = testUrl.startsWith("https://")
            
            android.util.Log.d("WebDAVClient", "Server type detection:")
            android.util.Log.d("WebDAVClient", "  - Jianguoyun: $isJianguoyun")
            android.util.Log.d("WebDAVClient", "  - Protocol: ${if (isHttp) "HTTP" else if (isHttps) "HTTPS" else "UNKNOWN"}")
            android.util.Log.d("WebDAVClient", "  - Ignore SSL: $ignoreSSLCert")
            
            // 根据服务器类型选择测试方法
            when {
                isJianguoyun -> {
                    android.util.Log.d("WebDAVClient", "Using Jianguoyun-specific test")
                    testJianguoyunWebDAV()
                }
                isHttp -> {
                    android.util.Log.d("WebDAVClient", "Using HTTP test")
                    testHttpConnection()
                }
                isHttps -> {
                    android.util.Log.d("WebDAVClient", "Using HTTPS test")
                    testHttpsConnection()
                }
                else -> {
                    android.util.Log.e("WebDAVClient", "Unknown protocol")
                    Result.failure(Exception("Unknown protocol: $testUrl"))
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Smart connection test exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * HTTP连接测试
     */
    private suspend fun testHttpConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "Testing HTTP connection...")
            
            val testUrl = normalizeUrl(serverUrl)
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(testUrl)
                .head()
                .build()
            
            val response = client.newCall(request).execute()
            android.util.Log.d("WebDAVClient", "HTTP test response: ${response.code}")
            response.close()
            
            if (response.isSuccessful) {
                android.util.Log.d("WebDAVClient", "HTTP connection successful")
                Result.success(true)
            } else {
                android.util.Log.w("WebDAVClient", "HTTP connection failed with code: ${response.code}")
                Result.failure(Exception("HTTP connection failed: ${response.code}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "HTTP connection failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * HTTPS连接测试
     */
    private suspend fun testHttpsConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "Testing HTTPS connection...")
            
            val testUrl = normalizeUrl(serverUrl)
            
            // 先尝试普通客户端
            try {
                val normalClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url(testUrl)
                    .head()
                    .build()
                
                val response = normalClient.newCall(request).execute()
                android.util.Log.d("WebDAVClient", "Normal HTTPS test response: ${response.code}")
                response.close()
                
                if (response.isSuccessful) {
                    android.util.Log.d("WebDAVClient", "Normal HTTPS connection successful")
                    return@withContext Result.success(true)
                }
            } catch (e: Exception) {
                android.util.Log.w("WebDAVClient", "Normal HTTPS connection failed: ${e.message}")
                if (e.message?.contains("cert", ignoreCase = true) == true) {
                    android.util.Log.d("WebDAVClient", "SSL certificate issue detected")
                }
            }
            
            // 如果普通客户端失败且设置了忽略SSL，尝试不安全客户端
            if (ignoreSSLCert) {
                try {
                    val unsafeClient = createUnsafeOkHttpClient()
                    
                    val request = Request.Builder()
                        .url(testUrl)
                        .head()
                        .build()
                    
                    val response = unsafeClient.newCall(request).execute()
                    android.util.Log.d("WebDAVClient", "Unsafe HTTPS test response: ${response.code}")
                    response.close()
                    
                    if (response.isSuccessful) {
                        android.util.Log.d("WebDAVClient", "Unsafe HTTPS connection successful")
                        return@withContext Result.success(true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebDAVClient", "Unsafe HTTPS connection also failed: ${e.message}")
                }
            }
            
            android.util.Log.e("WebDAVClient", "HTTPS connection test failed")
            Result.failure(Exception("HTTPS connection test failed"))
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "HTTPS connection test exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * 测试连接（处理HTTP和HTTPS）
     */
    suspend fun testSSLConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WebDAVClient", "=== Testing Connection ===")
            
            val testUrl = normalizeUrl(serverUrl)
            android.util.Log.d("WebDAVClient", "Testing connection to: $testUrl")
            
            // 检查是否为HTTP连接
            val isHttp = testUrl.startsWith("http://")
            val isHttps = testUrl.startsWith("https://")
            
            android.util.Log.d("WebDAVClient", "Protocol: ${if (isHttp) "HTTP" else if (isHttps) "HTTPS" else "UNKNOWN"}")
            
            if (isHttp) {
                // HTTP连接，直接测试
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    
                    val request = Request.Builder()
                        .url(testUrl)
                        .head()
                        .build()
                    
                    val response = client.newCall(request).execute()
                    android.util.Log.d("WebDAVClient", "HTTP test response: ${response.code}")
                    response.close()
                    
                    if (response.isSuccessful) {
                        android.util.Log.d("WebDAVClient", "HTTP connection successful")
                        return@withContext Result.success(true)
                    } else {
                        android.util.Log.w("WebDAVClient", "HTTP connection failed with code: ${response.code}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebDAVClient", "HTTP connection failed: ${e.message}")
                }
            } else if (isHttps) {
                // HTTPS连接，先尝试普通客户端
                try {
                    val normalClient = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    
                    val request = Request.Builder()
                        .url(testUrl)
                        .head()
                        .build()
                    
                    val response = normalClient.newCall(request).execute()
                    android.util.Log.d("WebDAVClient", "Normal HTTPS test response: ${response.code}")
                    response.close()
                    
                    if (response.isSuccessful) {
                        android.util.Log.d("WebDAVClient", "Normal HTTPS connection successful")
                        return@withContext Result.success(true)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WebDAVClient", "Normal HTTPS connection failed: ${e.message}")
                    if (e.message?.contains("cert", ignoreCase = true) == true) {
                        android.util.Log.d("WebDAVClient", "SSL certificate issue detected, trying unsafe SSL client")
                    }
                }
                
                // 如果普通客户端失败，尝试不安全SSL客户端
                try {
                    val unsafeClient = createUnsafeOkHttpClient()
                    
                    val request = Request.Builder()
                        .url(testUrl)
                        .head()
                        .build()
                    
                    val response = unsafeClient.newCall(request).execute()
                    android.util.Log.d("WebDAVClient", "Unsafe HTTPS test response: ${response.code}")
                    response.close()
                    
                    if (response.isSuccessful) {
                        android.util.Log.d("WebDAVClient", "Unsafe HTTPS connection successful")
                        return@withContext Result.success(true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebDAVClient", "Unsafe HTTPS connection also failed: ${e.message}")
                }
            }
            
            android.util.Log.e("WebDAVClient", "Connection test failed")
            Result.failure(Exception("Connection test failed"))
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Connection test exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * 专门的坚果云WebDAV测试（使用标准WebDAV协议）
     */
    suspend fun testJianguoyunWebDAV(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!serverUrl.contains("jianguoyun.com")) {
                return@withContext Result.success(true)
            }
            
            android.util.Log.d("WebDAVClient", "=== Jianguoyun WebDAV Standard Test ===")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    android.util.Log.d("WebDAVClient", "Jianguoyun Request: ${request.method} ${request.url}")
                    android.util.Log.d("WebDAVClient", "Jianguoyun Headers: ${request.headers}")
                    
                    val response = chain.proceed(request)
                    android.util.Log.d("WebDAVClient", "Jianguoyun Response: ${response.code} ${response.message}")
                    android.util.Log.d("WebDAVClient", "Jianguoyun Response Headers: ${response.headers}")
                    
                    if (response.code == 401) {
                        android.util.Log.e("WebDAVClient", "Jianguoyun 401 Response Body:")
                        try {
                            val body = response.body?.string()
                            android.util.Log.e("WebDAVClient", "Response Body: $body")
                        } catch (e: Exception) {
                            android.util.Log.e("WebDAVClient", "Failed to read response body: ${e.message}")
                        }
                    }
                    
                    response
                }
                .build()
            
            val cleanUsername = username.trim().replace("\\s+".toRegex(), "")
            val credential = Credentials.basic(cleanUsername, password)
            val testUrl = normalizeUrl(serverUrl)
            
            android.util.Log.d("WebDAVClient", "Jianguoyun Test Details:")
            android.util.Log.d("WebDAVClient", "  Username: '$cleanUsername'")
            android.util.Log.d("WebDAVClient", "  Password length: ${password.length}")
            android.util.Log.d("WebDAVClient", "  Test URL: $testUrl")
            android.util.Log.d("WebDAVClient", "  Credential: ${credential.take(20)}...")
            
            // 使用标准的WebDAV PROPFIND请求
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                    <D:allprop/>
                </D:propfind>
            """.trimIndent()
            
            val request = Request.Builder()
                .url(testUrl)
                .header("Authorization", credential)
                .header("User-Agent", "YuYanIME-WebDAV/1.0")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Depth", "1")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            android.util.Log.d("WebDAVClient", "Jianguoyun PROPFIND response: ${response.code}")
            
            if (response.isSuccessful) {
                android.util.Log.d("WebDAVClient", "Jianguoyun WebDAV test successful")
                response.close()
                return@withContext Result.success(true)
            } else {
                android.util.Log.e("WebDAVClient", "Jianguoyun WebDAV test failed: ${response.code}")
                val errorBody = response.body?.string()
                android.util.Log.e("WebDAVClient", "Error response body: $errorBody")
                response.close()
                return@withContext Result.failure(Exception("Jianguoyun WebDAV test failed: ${response.code}"))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Jianguoyun WebDAV test exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * 简单的 HTTP 测试（用于坚果云，带重试机制）
     */
    suspend fun testSimpleHttp(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!serverUrl.contains("jianguoyun.com")) {
                return@withContext Result.success(true)
            }
            
            val maxRetries = 2
            var lastException: Exception? = null
            
            for (attempt in 1..maxRetries) {
                try {
                    android.util.Log.d("WebDAVClient", "Simple HTTP test attempt $attempt/$maxRetries")
                    
                    // 先尝试普通客户端，如果SSL失败则使用不安全客户端
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .addInterceptor { chain ->
                            val request = chain.request()
                            android.util.Log.d("WebDAVClient", "Simple HTTP Request URL: ${request.url}")
                            android.util.Log.d("WebDAVClient", "Simple HTTP Request Method: ${request.method}")
                            android.util.Log.d("WebDAVClient", "Simple HTTP Request Headers: ${request.headers}")
                            
                            try {
                                val response = chain.proceed(request)
                                android.util.Log.d("WebDAVClient", "Simple HTTP Response Code: ${response.code}")
                                android.util.Log.d("WebDAVClient", "Simple HTTP Response Headers: ${response.headers}")
                                
                                if (response.code == 401) {
                                    android.util.Log.e("WebDAVClient", "Simple HTTP 401 - Detailed Analysis:")
                                    android.util.Log.e("WebDAVClient", "WWW-Authenticate: ${response.header("WWW-Authenticate")}")
                                    try {
                                        val responseBody = response.body?.string()
                                        android.util.Log.e("WebDAVClient", "Response Body: $responseBody")
                                    } catch (e: Exception) {
                                        android.util.Log.e("WebDAVClient", "Failed to read response body: ${e.message}")
                                    }
                                }
                                
                                response
                            } catch (e: Exception) {
                                android.util.Log.e("WebDAVClient", "Simple HTTP interceptor exception", e)
                                throw e
                            }
                        }
                        .build()
                    
                    val cleanUsername = if (serverUrl.contains("jianguoyun.com")) {
                        // 坚果云要求用户名必须是完整的邮箱地址，去除所有空格
                        username.trim().replace("\\s+".toRegex(), "")
                    } else {
                        username.trim()
                    }
                    
                    // 详细记录认证信息
                    android.util.Log.d("WebDAVClient", "=== Simple HTTP Test Debug Info (Attempt $attempt) ===")
                    android.util.Log.d("WebDAVClient", "Original username: '$username'")
                    android.util.Log.d("WebDAVClient", "Cleaned username: '$cleanUsername'")
                    android.util.Log.d("WebDAVClient", "Password length: ${password.length}")
                    android.util.Log.d("WebDAVClient", "Password first 3 chars: '${password.take(3)}'")
                    android.util.Log.d("WebDAVClient", "Password last 3 chars: '${password.takeLast(3)}'")
                    
                    // 检查用户名格式
                    val hasAtSymbol = cleanUsername.contains("@")
                    val hasDotSymbol = cleanUsername.contains(".")
                    android.util.Log.d("WebDAVClient", "Username format check:")
                    android.util.Log.d("WebDAVClient", "  - Contains @: $hasAtSymbol")
                    android.util.Log.d("WebDAVClient", "  - Contains .: $hasDotSymbol")
                    android.util.Log.d("WebDAVClient", "  - Username length: ${cleanUsername.length}")
                    
            val credential = Credentials.basic(cleanUsername, password)
            android.util.Log.d("WebDAVClient", "Generated credential: ${credential.take(20)}...")
            
            // 详细分析认证信息
            android.util.Log.d("WebDAVClient", "=== Authentication Analysis ===")
            android.util.Log.d("WebDAVClient", "Base64 credential: $credential")
            android.util.Log.d("WebDAVClient", "Decoded credential: ${String(android.util.Base64.decode(credential.substring(6), android.util.Base64.DEFAULT))}")
            
            // 检查认证头格式
            val authParts = credential.split(" ")
            if (authParts.size == 2 && authParts[0] == "Basic") {
                val decodedAuth = String(android.util.Base64.decode(authParts[1], android.util.Base64.DEFAULT))
                val colonIndex = decodedAuth.indexOf(':')
                if (colonIndex > 0) {
                    val decodedUsername = decodedAuth.substring(0, colonIndex)
                    val decodedPassword = decodedAuth.substring(colonIndex + 1)
                    android.util.Log.d("WebDAVClient", "Decoded username: '$decodedUsername'")
                    android.util.Log.d("WebDAVClient", "Decoded password length: ${decodedPassword.length}")
                    android.util.Log.d("WebDAVClient", "Original username matches: ${decodedUsername == cleanUsername}")
                    android.util.Log.d("WebDAVClient", "Original password matches: ${decodedPassword == password}")
                }
            }
                    
                    // 使用用户配置的URL进行测试，而不是硬编码的URL
                    val testUrl = normalizeUrl(serverUrl)
                    android.util.Log.d("WebDAVClient", "Test URL: $testUrl")
                    
                    val request = Request.Builder()
                        .url(testUrl)
                        .header("Authorization", credential)
                        .header("User-Agent", "YuYanIME-WebDAV/1.0")
                        .header("Accept", "*/*")
                        .header("Depth", "1")
                        .method("PROPFIND", null)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    android.util.Log.d("WebDAVClient", "Simple HTTP response code: ${response.code}")
                    android.util.Log.d("WebDAVClient", "Simple HTTP response message: ${response.message}")
                    android.util.Log.d("WebDAVClient", "Simple HTTP response headers: ${response.headers}")
                    
                    // 读取响应体用于调试
                    try {
                        val responseBody = response.body?.string()
                        android.util.Log.d("WebDAVClient", "Simple HTTP response body: $responseBody")
                    } catch (e: Exception) {
                        android.util.Log.w("WebDAVClient", "Failed to read response body: ${e.message}")
                    }
                    
                    response.close()
                    
                    if (response.isSuccessful) {
                        android.util.Log.d("WebDAVClient", "Simple HTTP test successful on attempt $attempt")
                        return@withContext Result.success(true)
            } else {
                android.util.Log.e("WebDAVClient", "Simple HTTP test failed: ${response.code} ${response.message}")
                        lastException = Exception("HTTP ${response.code}: ${response.message}")
                        
                        // 如果是401错误，不重试
                        if (response.code == 401) {
                            break
                        }
                    }
                    
                } catch (e: Exception) {
                    lastException = e
                    android.util.Log.e("WebDAVClient", "Simple HTTP test attempt $attempt failed", e)
                    android.util.Log.e("WebDAVClient", "Exception type: ${e.javaClass.simpleName}")
                    android.util.Log.e("WebDAVClient", "Exception message: ${e.message}")
                    
                    // 检查是否是连接关闭错误
                    if (e.message?.contains("closed", ignoreCase = true) == true) {
                        android.util.Log.e("WebDAVClient", "Simple HTTP connection closed - will retry")
                        if (attempt < maxRetries) {
                            android.util.Log.d("WebDAVClient", "Waiting ${attempt * 2} seconds before retry...")
                            kotlinx.coroutines.delay(attempt * 2000L)
                            continue
                        }
                    }
                    
                    // 如果不是连接关闭错误，或者已达到最大重试次数，则失败
                    if (attempt == maxRetries) {
                        break
                    }
                    
                    // 其他错误也进行重试
                    android.util.Log.d("WebDAVClient", "Waiting ${attempt * 2} seconds before retry...")
                    kotlinx.coroutines.delay(attempt * 2000L)
                }
            }
            
            android.util.Log.e("WebDAVClient", "Simple HTTP test failed after $maxRetries attempts")
            Result.failure(lastException ?: Exception("Simple HTTP test failed after $maxRetries attempts"))
            
        } catch (e: Exception) {
            android.util.Log.e("WebDAVClient", "Simple HTTP test failed", e)
            android.util.Log.e("WebDAVClient", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("WebDAVClient", "Exception message: ${e.message}")
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
     * 标准化 URL（移除重复的斜杠）
     */
    private fun normalizeUrl(url: String): String {
        var normalizedUrl = url.trim()
        
        // 坚果云特殊处理
        if (normalizedUrl.contains("jianguoyun.com")) {
            // 确保坚果云URL格式正确
            if (!normalizedUrl.startsWith("https://")) {
                normalizedUrl = "https://$normalizedUrl"
            }
            if (!normalizedUrl.contains("dav.jianguoyun.com")) {
                normalizedUrl = normalizedUrl.replace("jianguoyun.com", "dav.jianguoyun.com")
            }
            if (!normalizedUrl.endsWith("/dav/")) {
                normalizedUrl = normalizedUrl.trimEnd('/') + "/dav/"
            }
            android.util.Log.d("WebDAVClient", "坚果云URL标准化: $url -> $normalizedUrl")
        }
        
        // 移除多余的斜杠，但保留协议部分的 ://
        return normalizedUrl.replace(Regex("(?<!:)/+"), "/")
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

