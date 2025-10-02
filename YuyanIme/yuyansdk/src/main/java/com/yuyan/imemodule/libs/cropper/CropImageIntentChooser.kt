package com.yuyan.imemodule.libs.cropper

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Parcelable
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.yuyan.imemodule.R

internal class CropImageIntentChooser(
    private val activity: ComponentActivity,
    private val callback: ResultCallback,
) {
  internal interface ResultCallback {
    fun onSuccess(uri: Uri?)
    fun onCancelled()
  }

  internal companion object {
    const val GOOGLE_PHOTOS = "com.google.android.apps.photos"
    const val GOOGLE_PHOTOS_GO = "com.google.android.apps.photosgo"
    const val SAMSUNG_GALLERY = "com.sec.android.gallery3d"
    const val ONEPLUS_GALLERY = "com.oneplus.gallery"
    const val MIUI_GALLERY = "com.miui.gallery"
  }

  private var title: String = activity.getString(R.string.pick_image_chooser_title)
  private var priorityIntentList = listOf(
    GOOGLE_PHOTOS,
    GOOGLE_PHOTOS_GO,
    SAMSUNG_GALLERY,
    ONEPLUS_GALLERY,
    MIUI_GALLERY,
  )
  private var cameraImgUri: Uri? = null
  private val intentChooser = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityRes ->
    if (activityRes.resultCode == Activity.RESULT_OK) {
      
      (activityRes.data?.data ?: cameraImgUri).let { uri ->
        callback.onSuccess(uri)
      }
    } else {
      callback.onCancelled()
    }
  }

  
  fun showChooserIntent(
    includeCamera: Boolean,
    includeGallery: Boolean,
    cameraImgUri: Uri? = null,
  ) {
    this.cameraImgUri = cameraImgUri
    val allIntents: MutableList<Intent> = ArrayList()
    val packageManager = activity.packageManager
    if (!isExplicitCameraPermissionRequired(activity) && includeCamera) {
      allIntents.addAll(getCameraIntents(activity, packageManager))
    }

    if (includeGallery) {
      var galleryIntents = getGalleryIntents(packageManager, Intent.ACTION_GET_CONTENT)
      if (galleryIntents.isEmpty()) {
        galleryIntents = getGalleryIntents(packageManager, Intent.ACTION_PICK)
      }
      allIntents.addAll(galleryIntents)
    }

    val target = if (allIntents.isEmpty()) {
      Intent()
    } else {
      Intent(Intent.ACTION_CHOOSER, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
        if (includeGallery) {
          action = Intent.ACTION_PICK
          type = "image
  private fun getCameraIntents(context: Context, packageManager: PackageManager): List<Intent> {
    val allIntents: MutableList<Intent> = ArrayList()
    val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

    val flags = 0
    val listCam = when {
      SDK_INT >= 33 -> packageManager.queryIntentActivities(captureIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
      else -> packageManager.queryIntentActivities(captureIntent, flags)
    }

    for (resolveInfo in listCam) {
      val intent = Intent(captureIntent)
      intent.component = ComponentName(
        resolveInfo.activityInfo.packageName,
        resolveInfo.activityInfo.name,
      )
      intent.setPackage(resolveInfo.activityInfo.packageName)
      if (context is Activity) {
        context.grantUriPermission(
          resolveInfo.activityInfo.packageName,
          cameraImgUri,
          Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImgUri)
      allIntents.add(intent)
    }
    return allIntents
  }

  
  private fun getGalleryIntents(packageManager: PackageManager, action: String): List<Intent> {
    val intents: MutableList<Intent> = ArrayList()
    val galleryIntent = if (action == Intent.ACTION_GET_CONTENT) {
      Intent(action)
    } else {
      Intent(action, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }
    galleryIntent.type = "image
  private fun isExplicitCameraPermissionRequired(context: Context): Boolean = hasCameraPermissionInManifest(context) &&
    context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED

  
  private fun hasCameraPermissionInManifest(context: Context): Boolean {
    val packageName = context.packageName
    try {
      val flags = PackageManager.GET_PERMISSIONS
      val packageInfo = when {
        SDK_INT >= 33 -> context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else -> context.packageManager.getPackageInfo(packageName, flags)
      }
      val declaredPermissions = packageInfo.requestedPermissions
      return declaredPermissions
        ?.any { it?.equals("android.permission.CAMERA", true) == true } == true
    } catch (e: PackageManager.NameNotFoundException) {
      e.printStackTrace()
    }
    return false
  }

  
  fun setupPriorityAppsList(appsList: List<String>): CropImageIntentChooser = apply {
    priorityIntentList = appsList
  }

  
  fun setIntentChooserTitle(title: String): CropImageIntentChooser = apply {
    this.title = title
  }
}