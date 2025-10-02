package com.yuyan.imemodule.libs.cropper

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.net.toUri
import com.yuyan.imemodule.libs.cropper.CropImageView.CropResult
import com.yuyan.imemodule.libs.cropper.CropImageView.OnCropImageCompleteListener
import com.yuyan.imemodule.libs.cropper.CropImageView.OnSetImageUriCompleteListener
import com.yuyan.imemodule.libs.cropper.utils.getUriForFile
import com.yuyan.imemodule.R
import com.yuyan.imemodule.databinding.CropImageActivityBinding
import java.io.File

open class CropImageActivity : AppCompatActivity(), OnSetImageUriCompleteListener, OnCropImageCompleteListener {

  
  private var cropImageUri: Uri? = null

  
  private lateinit var cropImageOptions: CropImageOptions

  
  private var cropImageView: CropImageView? = null
  private lateinit var binding: CropImageActivityBinding
  private var latestTmpUri: Uri? = null
  private val pickImageGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    onPickImageResult(uri)
  }

  private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) {
    if (it) {
      onPickImageResult(latestTmpUri)
    } else {
      onPickImageResult(null)
    }
  }

  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = CropImageActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setCropImageView(binding.cropImageView)
    val bundle = intent.getBundleExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE)
    cropImageUri = bundle?.parcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE)
    cropImageOptions =
      bundle?.parcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS) ?: CropImageOptions()

    if (savedInstanceState == null) {
      if (cropImageUri == null || cropImageUri == Uri.EMPTY) {
        when {
          cropImageOptions.showIntentChooser -> showIntentChooser()
          cropImageOptions.imageSourceIncludeGallery &&
            cropImageOptions.imageSourceIncludeCamera ->
            showImageSourceDialog(::openSource)
          cropImageOptions.imageSourceIncludeGallery ->
            pickImageGallery.launch("image
  open fun showImageSourceDialog(openSource: (Source) -> Unit) {
    AlertDialog.Builder(this)
      .setCancelable(false)
      .setOnKeyListener { _, keyCode, keyEvent ->
        if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
          setResultCancel()
          finish()
        }
        true
      }
      .setTitle(R.string.pick_image_chooser_title)
      .setItems(
        arrayOf(
          getString(R.string.pick_image_camera),
          getString(R.string.pick_image_gallery),
        ),
      ) { _, position -> openSource(if (position == 0) Source.CAMERA else Source.GALLERY) }
      .show()
  }

  public override fun onStart() {
    super.onStart()
    cropImageView?.setOnSetImageUriCompleteListener(this)
    cropImageView?.setOnCropImageCompleteListener(this)
  }

  public override fun onStop() {
    super.onStop()
    cropImageView?.setOnSetImageUriCompleteListener(null)
    cropImageView?.setOnCropImageCompleteListener(null)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(BUNDLE_KEY_TMP_URI, latestTmpUri.toString())
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    if (cropImageOptions.skipEditing) return true
    menuInflater.inflate(R.menu.crop_image_menu, menu)

    if (!cropImageOptions.allowRotation) {
      menu.removeItem(R.id.ic_rotate_left_24)
      menu.removeItem(R.id.ic_rotate_right_24)
    } else if (cropImageOptions.allowCounterRotation) {
      menu.findItem(R.id.ic_rotate_left_24).isVisible = true
    }

    if (!cropImageOptions.allowFlipping) menu.removeItem(R.id.ic_flip_24)

    if (cropImageOptions.cropMenuCropButtonTitle != null) {
      menu.findItem(R.id.crop_image_menu_crop).title =
        cropImageOptions.cropMenuCropButtonTitle
    }

    var cropIcon: Drawable? = null
    try {
      if (cropImageOptions.cropMenuCropButtonIcon != 0) {
        cropIcon = ContextCompat.getDrawable(this, cropImageOptions.cropMenuCropButtonIcon)
        menu.findItem(R.id.crop_image_menu_crop).icon = cropIcon
      }
    } catch (e: Exception) {
      Log.w("AIC", "Failed to read menu crop drawable", e)
    }

    if (cropImageOptions.activityMenuIconColor != 0) {
      updateMenuItemIconColor(
        menu,
        R.id.ic_rotate_left_24,
        cropImageOptions.activityMenuIconColor,
      )
      updateMenuItemIconColor(
        menu,
        R.id.ic_rotate_right_24,
        cropImageOptions.activityMenuIconColor,
      )
      updateMenuItemIconColor(menu, R.id.ic_flip_24, cropImageOptions.activityMenuIconColor)

      if (cropIcon != null) {
        updateMenuItemIconColor(
          menu,
          R.id.crop_image_menu_crop,
          cropImageOptions.activityMenuIconColor,
        )
      }
    }
    cropImageOptions.activityMenuTextColor?.let { menuItemsTextColor ->
      val menuItemIds = listOf(
        R.id.ic_rotate_left_24,
        R.id.ic_rotate_right_24,
        R.id.ic_flip_24,
        R.id.ic_flip_24_horizontally,
        R.id.ic_flip_24_vertically,
        R.id.crop_image_menu_crop,
      )
      for (itemId in menuItemIds) {
        updateMenuItemTextColor(menu, itemId, menuItemsTextColor)
      }
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    R.id.crop_image_menu_crop -> {
      cropImage()
      true
    }
    R.id.ic_rotate_left_24 -> {
      rotateImage(-cropImageOptions.rotationDegrees)
      true
    }
    R.id.ic_rotate_right_24 -> {
      rotateImage(cropImageOptions.rotationDegrees)
      true
    }
    R.id.ic_flip_24_horizontally -> {
      cropImageView?.flipImageHorizontally()
      true
    }
    R.id.ic_flip_24_vertically -> {
      cropImageView?.flipImageVertically()
      true
    }
    android.R.id.home -> {
      setResultCancel()
      true
    }
    else -> super.onOptionsItemSelected(item)
  }

  protected open fun onPickImageResult(resultUri: Uri?) {
    when (resultUri) {
      null -> setResultCancel()
      else -> {
        cropImageUri = resultUri
        cropImageView?.setImageUriAsync(cropImageUri)
      }
    }
  }

  override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
    if (error == null) {
      if (cropImageOptions.initialCropWindowRectangle != null) {
        cropImageView?.cropRect = cropImageOptions.initialCropWindowRectangle
      }

      if (cropImageOptions.initialRotation > 0) {
        cropImageView?.rotatedDegrees = cropImageOptions.initialRotation
      }

      if (cropImageOptions.skipEditing) {
        cropImage()
      }
    } else {
      setResult(null, error, 1)
    }
  }

  override fun onCropImageComplete(view: CropImageView, result: CropResult) {
    setResult(result.uriContent, result.error, result.sampleSize)
  }

  
  open fun cropImage() {
    if (cropImageOptions.noOutputImage) {
      setResult(null, null, 1)
    } else {
      cropImageView?.croppedImageAsync(
        saveCompressFormat = cropImageOptions.outputCompressFormat,
        saveCompressQuality = cropImageOptions.outputCompressQuality,
        reqWidth = cropImageOptions.outputRequestWidth,
        reqHeight = cropImageOptions.outputRequestHeight,
        options = cropImageOptions.outputRequestSizeOptions,
        customOutputUri = cropImageOptions.customOutputUri,
      )
    }
  }

  
  open fun setCropImageView(cropImageView: CropImageView) {
    this.cropImageView = cropImageView
  }

  
  open fun rotateImage(degrees: Int) {
    cropImageView?.rotateImage(degrees)
  }

  
  open fun setResult(uri: Uri?, error: Exception?, sampleSize: Int) {
    setResult(
      error?.let { CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE } ?: RESULT_OK,
      getResultIntent(uri, error, sampleSize),
    )
    finish()
  }

  
  open fun setResultCancel() {
    setResult(RESULT_CANCELED)
    finish()
  }

  
  open fun getResultIntent(uri: Uri?, error: Exception?, sampleSize: Int): Intent {
    val result = CropImage.ActivityResult(
        originalUri = cropImageView?.imageUri,
        uriContent = uri,
        error = error,
        cropPoints = cropImageView?.cropPoints,
        cropRect = cropImageView?.cropRect,
        rotation = cropImageView?.rotatedDegrees ?: 0,
        wholeImageRect = cropImageView?.wholeImageRect,
        sampleSize = sampleSize,
    )
    val intent = Intent()
    intent.extras?.let(intent::putExtras)
    intent.putExtra(CropImage.CROP_IMAGE_EXTRA_RESULT, result)
    return intent
  }

  
  open fun updateMenuItemIconColor(menu: Menu, itemId: Int, color: Int) {
    val menuItem = menu.findItem(itemId)
    if (menuItem != null) {
      val menuItemIcon = menuItem.icon
      if (menuItemIcon != null) {
        try {
          menuItemIcon.apply {
            mutate()
            colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
              color,
              BlendModeCompat.SRC_ATOP,
            )
          }
          menuItem.icon = menuItemIcon
        } catch (e: Exception) {
          Log.w("AIC", "Failed to update menu item color", e)
        }
      }
    }
  }

  
  open fun updateMenuItemTextColor(menu: Menu, itemId: Int, color: Int) {
    val menuItem = menu.findItem(itemId) ?: return
    val menuTitle = menuItem.title
    if (menuTitle?.isNotBlank() == true) {
      try {
        val spannableTitle: Spannable = SpannableString(menuTitle)
        spannableTitle.setSpan(
          ForegroundColorSpan(color),
          0,
          spannableTitle.length,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        menuItem.title = spannableTitle
      } catch (e: Exception) {
        Log.w("AIC", "Failed to update menu item color", e)
      }
    }
  }

  enum class Source { CAMERA, GALLERY }

  private companion object {

    const val BUNDLE_KEY_TMP_URI = "bundle_key_tmp_uri"
  }
}
