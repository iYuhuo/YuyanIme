package com.yuyan.imemodule.manager

import com.yuyan.imemodule.BuildConfig
import com.yuyan.imemodule.R
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.utils.errorRuntime
import com.yuyan.imemodule.utils.extract
import com.yuyan.imemodule.utils.withTempDir
import com.yuyan.imemodule.utils.versionCodeCompat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object UserDataManager {

    private val json = Json { prettyPrint = true }

    @Serializable
    data class Metadata(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val exportTime: Long
    )

    private fun writeFileTree(srcDir: File, destPrefix: String, dest: ZipOutputStream) {
        dest.putNextEntry(ZipEntry("$destPrefix/"))
        srcDir.walkTopDown().forEach { f ->
            val related = f.relativeTo(srcDir)
            if (related.path != "") {
                if (f.isDirectory) {
                    dest.putNextEntry(ZipEntry("$destPrefix/${related.path}/"))
                } else if (f.isFile) {
                    dest.putNextEntry(ZipEntry("$destPrefix/${related.path}"))
                    f.inputStream().use { it.copyTo(dest) }
                }
            }
        }
    }

    private val sharedPrefsDir = File(Launcher.instance.context.applicationInfo.dataDir, "shared_prefs")
    private val dataBasesDir = File(Launcher.instance.context.applicationInfo.dataDir, "databases")
    private val externalDir = Launcher.instance.context.getExternalFilesDir(null)!!

    @OptIn(ExperimentalSerializationApi::class)
    fun export(dest: OutputStream, timestamp: Long = System.currentTimeMillis()) = runCatching {
        android.util.Log.d("UserDataManager", "Starting export process...")
        ZipOutputStream(dest.buffered()).use { zipStream ->
            // shared_prefs
            android.util.Log.d("UserDataManager", "Exporting shared_prefs...")
            writeFileTree(sharedPrefsDir, "shared_prefs", zipStream)
            
            // databases
            android.util.Log.d("UserDataManager", "Exporting databases...")
            writeFileTree(dataBasesDir, "databases", zipStream)
            
            // external
            android.util.Log.d("UserDataManager", "Exporting external files...")
            writeFileTree(externalDir, "external", zipStream)
            
            // metadata
            android.util.Log.d("UserDataManager", "Writing metadata...")
            zipStream.putNextEntry(ZipEntry("metadata.json"))
            val pkgInfo = Launcher.instance.context.packageManager.getPackageInfo(Launcher.instance.context.packageName, 0)
            val metadata = Metadata(
                pkgInfo.packageName,
                pkgInfo.versionCodeCompat,
                BuildConfig.versionName,
                timestamp
            )
            json.encodeToStream(metadata, zipStream)
            zipStream.closeEntry()
            
            android.util.Log.d("UserDataManager", "Export completed successfully")
        }
    }

    private fun copyDir(source: File, target: File) {
        if (!source.exists()) {
            // 备份中目录不存在是正常的（用户可能未使用某些功能）
            android.util.Log.d("UserDataManager", "Source directory does not exist (skipping): ${source.path}")
            return
        }
        
        if (!source.isDirectory) {
            // 如果存在但不是目录，这可能是个问题
            android.util.Log.w("UserDataManager", "Source is not a directory (skipping): ${source.path}")
            return
        }
        
        try {
            source.copyRecursively(target, overwrite = true)
            android.util.Log.d("UserDataManager", "Successfully copied directory: ${source.name} (${source.listFiles()?.size ?: 0} items)")
        } catch (e: Exception) {
            android.util.Log.e("UserDataManager", "Failed to copy directory: ${source.path} -> ${target.path}", e)
            throw e  // 抛出异常，让调用者知道导入失败
        }
    }

    fun import(src: InputStream) = runCatching {
        android.util.Log.d("UserDataManager", "Starting import process...")
        ZipInputStream(src).use { zipStream ->
            withTempDir { tempDir ->
                android.util.Log.d("UserDataManager", "Extracting backup to temp directory...")
                val metadataFile = zipStream.extract(tempDir).find { it.name == "metadata.json" } ?: errorRuntime(R.string.exception_user_data_metadata)
                val metadata = json.decodeFromString<Metadata>(metadataFile.readText())
                android.util.Log.d("UserDataManager", "Backup metadata: package=${metadata.packageName}, version=${metadata.versionName}, exportTime=${metadata.exportTime}")
                
                android.util.Log.d("UserDataManager", "Importing shared_prefs...")
                copyDir(File(tempDir, "shared_prefs"), sharedPrefsDir)
                
                android.util.Log.d("UserDataManager", "Importing databases...")
                copyDir(File(tempDir, "databases"), dataBasesDir)
                
                android.util.Log.d("UserDataManager", "Importing external files...")
                copyDir(File(tempDir, "external"), externalDir)
                
                android.util.Log.d("UserDataManager", "Import completed successfully")
                metadata
            }
        }
    }
}