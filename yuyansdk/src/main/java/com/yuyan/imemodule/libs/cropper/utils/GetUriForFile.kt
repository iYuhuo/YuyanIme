package com.yuyan.imemodule.libs.cropper.utils

import android.content.Context
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths

internal fun Context.authority() = "$packageName.cropper.fileprovider"

internal fun getUriForFile(context: Context, file: File): Uri {
  val authority = context.authority()
  try {
    return FileProvider.getUriForFile(context, authority, file)
  } catch (e: Exception) {
    try {
      val cacheFolder = File(context.cacheDir, "CROP_LIB_CACHE")
      val cacheLocation = File(cacheFolder, file.name)
      var input: InputStream? = null
      var output: OutputStream? = null
      try {
        input = FileInputStream(file)
        output = FileOutputStream(cacheLocation)
        input.copyTo(output)
        return FileProvider.getUriForFile(context, authority, cacheLocation)
      } catch (e: Exception) {
        val path = "content://$authority/files/my_images/"

        if (SDK_INT >= 26) {
          Files.createDirectories(Paths.get(path))
        } else {
          val directory = File(path)
          if (!directory.exists()) directory.mkdirs()
        }
        return Uri.parse("$path${file.name}")
      } finally {
        input?.close()
        output?.close()
      }
    } catch (e: Exception) {
      if (SDK_INT < 29) {
        val cacheDir = context.externalCacheDir
        cacheDir?.let {
          try {
            return Uri.fromFile(File(cacheDir.path, file.absolutePath))
          } catch (_: Exception) { }
        }
      }
      return Uri.fromFile(file)
    }
  }
}